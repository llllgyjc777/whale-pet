package com.moe.whale;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 刷视频引擎。
 * 策略：
 *   1) 先尝试读控件树（能读到文字 → 说明无障碍未被封锁）
 *   2) 读不到 → 自动降级为盲滑手势
 *   3) 每 N 个视频做一次简短"观后感"（可选，走 DeepSeek）
 */
public class ScrollEngine {

    public interface Listener {
        void onStatus(String text);
        void onCount(int watched);
        void onStop(String reason);
    }

    private final Context ctx;
    private final Listener li;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();

    private volatile boolean running = false;
    private int watched = 0;
    private long startedAt = 0;
    private String mode = "未知";
    private final List<String> seenTitles = new ArrayList<>();
    private int narrateTick = 0;

    public ScrollEngine(Context c, Listener l) { this.ctx = c.getApplicationContext(); this.li = l; }

    public boolean isRunning() { return running; }
    public int watchedCount() { return watched; }
    public String mode() { return mode; }

    public void start() {
        if (running) return;
        AutoService a = AutoService.get();
        if (a == null) { li.onStop("无障碍服务没开，先去系统设置里打开「小鲸」"); return; }
        if (!Config.autoEnabled(ctx)) { li.onStop("自动化总开关是关的"); return; }

        running = true;
        watched = 0;
        startedAt = System.currentTimeMillis();
        seenTitles.clear();

        Store.get(ctx).log("SCROLL", "开始刷视频，目标=" + Config.targetPkg(ctx)
                + " 时长=" + Config.scrollMinutes(ctx) + "分");

        try {
            Intent i = ctx.getPackageManager().getLaunchIntentForPackage(Config.targetPkg(ctx));
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            }
        } catch (Exception ignored) { }

        h.postDelayed(this::tick, 3000);
    }

    public void stop(String reason) {
        if (!running) return;
        running = false;
        h.removeCallbacksAndMessages(null);
        Voice.stopSpeaking();
        long min = (System.currentTimeMillis() - startedAt) / 60000;
        Store.get(ctx).log("SCROLL", "停止：" + reason + "，共刷 " + watched + " 个，用时 " + min + " 分钟");
        li.onStop(reason);
    }

    private void tick() {
        if (!running) return;

        long elapsedMin = (System.currentTimeMillis() - startedAt) / 60000;
        if (elapsedMin >= Config.scrollMinutes(ctx)) {
            stop("到点了，刷了 " + watched + " 个视频，休息一下");
            return;
        }

        AutoService a = AutoService.get();
        if (a == null) { stop("无障碍服务断开"); return; }

        String fg = a.foregroundPkg();
        if (!fg.isEmpty() && !fg.equals(Config.targetPkg(ctx)) && !fg.equals(ctx.getPackageName())) {
            li.onStatus("切回目标 App…");
            try {
                Intent i = ctx.getPackageManager().getLaunchIntentForPackage(Config.targetPkg(ctx));
                if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i); }
            } catch (Exception ignored) { }
            h.postDelayed(this::tick, 2500);
            return;
        }

        String screen = a.screenText(1200);
        boolean gotText = screen != null && screen.trim().length() > 20;
        mode = gotText ? "控件" : "盲滑";

        String title = pickTitle(screen);
        if (!title.isEmpty() && !seenTitles.contains(title)) {
            seenTitles.add(title);
            if (seenTitles.size() > 200) seenTitles.remove(0);
            Store.get(ctx).log("看", title);
        }

        watched++;
        li.onCount(watched);

        narrateTick++;
        if (Config.narrate(ctx) && narrateTick % 6 == 0) {
            narrate(title, screen);
        } else {
            li.onStatus("第 " + watched + " 个  [" + mode + "]");
        }

        boolean ok;
        if (gotText) {
            ok = a.tapText("下一条") || a.tapText("下一个") || a.tapText("继续观看");
            if (!ok) ok = a.swipeUp();
        } else {
            ok = a.swipeUp();
        }
        if (!ok) Store.get(ctx).log("SCROLL", "翻页失败，重试");

        int base = Config.scrollIntervalMs(ctx);
        int jitter = (int) (base * 0.25);
        int wait = base + rnd.nextInt(Math.max(1, jitter * 2)) - jitter;
        h.postDelayed(this::tick, Math.max(1500, wait));
    }

    private String pickTitle(String screen) {
        if (screen == null || screen.isEmpty()) return "";
        String best = "";
        for (String line : screen.split("\n")) {
            String s = line.trim();
            if (s.length() < 6 || s.length() > 60) continue;
            if (s.matches("^[0-9:：\\s]+$")) continue;
            if (s.contains("点赞") || s.contains("评论") || s.contains("分享")) continue;
            if (s.contains("加载") || s.contains("网络")) continue;
            if (s.startsWith("@")) continue;
            if (s.length() > best.length()) best = s;
        }
        return best;
    }

    private void narrate(String title, String screen) {
        if (title.isEmpty()) return;
        List<String[]> msgs = new ArrayList<>();
        msgs.add(new String[]{"system",
                Config.persona(ctx) + "\n你正在替用户刷短视频。用一句话（20字内）说说你看到了什么、感觉如何，别用列表。"});
        msgs.add(new String[]{"user",
                "我刚刷到一条，屏幕上写着：\n" + clip(screen, 500) + "\n\n说一句。"});
        new Brain(ctx).send(msgs, new Brain.Cb() {
            @Override public void onDelta(String p) { }
            @Override public void onDone(String full, String err) {
                if (err != null || full.isEmpty()) return;
                li.onStatus(full.trim());
                if (Config.ttsOn(ctx)) Voice.speak(ctx, full.trim(), null);
            }
        });
    }

    private static String clip(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n);
    }
}