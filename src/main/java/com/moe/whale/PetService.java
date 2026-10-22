package com.moe.whale;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 桌宠常驻服务：悬浮窗 + 气泡 + 语音 + 刷视频 */
public class PetService extends Service {

    public static final String CH = "whale_pet";
    public static final int NOTI = 1001;

    private static PetService inst;
    public static PetService get() { return inst; }

    private WindowManager wm;
    private FrameLayout root;
    private WhaleView whale;
    private LinearLayout bubbleBox;
    private TextView bubble;
    private View micBtn;
    private View stopBtn;

    private WindowManager.LayoutParams lp;
    private int screenW, screenH;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private ScrollEngine engine;
    private final Brain brain = new Brain(this);
    private boolean listening = false;

    @Override public void onCreate() {
        super.onCreate();
        inst = this;
        startForeground(NOTI, buildNoti());
        Voice.initTts(this);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        screenW = getResources().getDisplayMetrics().widthPixels;
        screenH = getResources().getDisplayMetrics().heightPixels;
        addPet();
        engine = new ScrollEngine(this, new ScrollEngine.Listener() {
            @Override public void onStatus(String text) { say(text, true); }
            @Override public void onCount(int watched) { }
            @Override public void onStop(String reason) { say(reason, true); setStopVisible(false); }
        });
        say("我来了～点我聊天，长按设置", false);
    }

    private Notification buildNoti() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH, "小鲸", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        return b.setContentTitle("小鲸在陪着你")
                .setContentText("点我说话")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    // ================= 悬浮窗 =================
    private void addPet() {
        root = new FrameLayout(this);

        // 气泡容器
        bubbleBox = new LinearLayout(this);
        bubbleBox.setOrientation(LinearLayout.VERTICAL);
        bubbleBox.setVisibility(View.GONE);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        root.addView(bubbleBox, blp);

        bubble = new TextView(this);
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(13f);
        bubble.setPadding(dp(12), dp(8), dp(12), dp(8));
        bubble.setMaxWidth(dp(240));
        bubble.setBackgroundColor(0xE6202833);
        bubbleBox.addView(bubble);

        // 鲸鱼本体
        whale = new WhaleView(this);
        FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(dp(120), dp(120));
        wlp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        wlp.topMargin = dp(56);
        root.addView(whale, wlp);

        // 麦克风按钮
        micBtn = new TextView(this);
        ((TextView) micBtn).setText("🎤");
        ((TextView) micBtn).setTextSize(16f);
        micBtn.setBackgroundColor(0xCC1E88E5);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(dp(30), dp(30));
        mlp.gravity = Gravity.TOP | Gravity.START;
        mlp.topMargin = dp(10);
        mlp.leftMargin = dp(4);
        root.addView(micBtn, mlp);

        // 停止按钮（自动化时出现）
        stopBtn = new TextView(this);
        ((TextView) stopBtn).setText("■");
        ((TextView) stopBtn).setTextSize(14f);
        ((TextView) stopBtn).setTextColor(Color.WHITE);
        stopBtn.setBackgroundColor(0xCCE53935);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(dp(30), dp(30));
        slp.gravity = Gravity.TOP | Gravity.END;
        slp.topMargin = dp(10);
        slp.rightMargin = dp(4);
        root.addView(stopBtn, slp);
        stopBtn.setVisibility(View.GONE);

        lp = new WindowManager.LayoutParams(
                dp(150), dp(190),
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = screenW - dp(170);
        lp.y = screenH / 2;

        // ---- 拖动 + 点击 ----
        final long[] downT = {0};
        final float[] downXY = {0, 0};
        final int[] startXY = {0, 0};
        final boolean[] moved = {false};
        final long[] lastTap = {0};

        whale.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downT[0] = System.currentTimeMillis();
                    downXY[0] = e.getRawX();
                    downXY[1] = e.getRawY();
                    startXY[0] = lp.x;
                    startXY[1] = lp.y;
                    moved[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) (e.getRawX() - downXY[0]);
                    int dy = (int) (e.getRawY() - downXY[1]);
                    if (Math.abs(dx) > dp(8) || Math.abs(dy) > dp(8)) moved[0] = true;
                    if (moved[0]) {
                        lp.x = startXY[0] + dx;
                        lp.y = startXY[1] + dy;
                        clamp();
                        wm.updateViewLayout(root, lp);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    long dt = System.currentTimeMillis() - downT[0];
                    if (!moved[0] && dt < 300) {
                        long now = System.currentTimeMillis();
                        if (now - lastTap[0] < 320) {
                            onDoubleTap();
                        } else {
                            lastTap[0] = now;
                            ui.postDelayed(() -> {
                                if (System.currentTimeMillis() - lastTap[0] >= 310) onSingleTap();
                            }, 330);
                        }
                    } else if (!moved[0] && dt >= 600) {
                        onLongPress();
                    }
                    return true;
            }
            return false;
        });

        micBtn.setOnClickListener(v -> onMic());
        stopBtn.setOnClickListener(v -> {
            if (engine != null && engine.isRunning()) engine.stop("手动停止");
            setStopVisible(false);
        });

        wm.addView(root, lp);
    }

    private void clamp() {
        int w = dp(150);
        if (lp.x < 0) lp.x = 0;
        if (lp.x > screenW - w) lp.x = screenW - w;
        if (lp.y < 0) lp.y = 0;
        if (lp.y > screenH - dp(120)) lp.y = screenH - dp(120);
    }

    // ================= 交互 =================
    private void onSingleTap() {
        if (listening) { stopListen(); return; }
        say("想说什么？点左边麦克风，或者直接长按我", false);
    }

    private void onDoubleTap() {
        // 双击：读屏并汇报
        AutoService a = AutoService.get();
        if (a == null) { say("无障碍没开，读不了屏", true); return; }
        String t = a.screenText(400);
        if (t.trim().isEmpty()) { say("这屏没什么文字", true); return; }
        say("屏幕上写着：" + t.replace("\n", " ").substring(0, Math.min(90, t.length())), true);
    }

    private void onLongPress() {
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void onMic() {
        if (listening) { stopListen(); return; }
        if (!Voice.canListen(this)) { say("这台机器没有语音识别服务", true); return; }

        listening = true;
        ((TextView) micBtn).setText("⏺");
        micBtn.setBackgroundColor(0xCCE53935);
        say("我在听…", false);

        Voice.startListening(this, new Voice.HearCb() {
            @Override public void onPartial(String text) {
                bubbleBox.setVisibility(View.VISIBLE);
                bubble.setText(text);
            }
            @Override public void onFinal(String text) {
                stopListen();
                handleUser(text);
            }
            @Override public void onErr(String msg) {
                stopListen();
                say(msg, true);
            }
        });
    }

    private void stopListen() {
        listening = false;
        Voice.stopListening();
        ((TextView) micBtn).setText("🎤");
        micBtn.setBackgroundColor(0xCC1E88E5);
    }

    public void handleUser(String text) {
        if (text == null || text.trim().isEmpty()) return;
        Store.get(this).addMsg("user", text);
        bubbleBox.setVisibility(View.VISIBLE);
        bubble.setText("…");
        whale.talk();

        brain.chat(text, new Brain.Cb() {
            @Override public void onDelta(String piece) {
                bubbleBox.setVisibility(View.VISIBLE);
                bubble.append(piece);
                whale.talk();
            }
            @Override public void onDone(String full, String err) {
                if (err != null) {
                    bubble.setText("出错了：" + err);
                    return;
                }
                Store.get(this).addMsg("assistant", full);
                if (Config.ttsOn(PetService.this)) {
                    whale.talk();
                    Voice.speak(PetService.this, full, null);
                    // 边说边动嘴
                    ui.postDelayed(new Runnable() {
                        int n = 0;
                        @Override public void run() {
                            if (n++ > 40 || !Voice.isSpeaking()) return;
                            whale.talk();
                            ui.postDelayed(this, 260);
                        }
                    }, 260);
                }
            }
        });
    }

    /** 显示气泡；speakNow=true 时同时朗读 */
    public void say(String text, boolean speakNow) {
        if (text == null) return;
        ui.post(() -> {
            bubbleBox.setVisibility(View.VISIBLE);
            bubble.setText(text);
            bubbleBox.animate().alpha(1f).setDuration(120).start();
        });
        if (speakNow && Config.ttsOn(this)) {
            whale.talk();
            Voice.speak(this, text, null);
        }
        ui.removeCallbacks(hideBubble);
        ui.postDelayed(hideBubble, Math.max(6000, text.length() * 260));
    }

    private final Runnable hideBubble = () -> {
        if (bubbleBox != null) bubbleBox.setVisibility(View.GONE);
    };

    public void setStopVisible(boolean v) {
        ui.post(() -> { if (stopBtn != null) stopBtn.setVisibility(v ? View.VISIBLE : View.GONE); });
    }

    // ================= 刷视频 =================
    public void startScroll() {
        if (engine == null) return;
        if (engine.isRunning()) { say("已经在刷了", true); return; }
        setStopVisible(true);
        engine.start();
        if (engine.isRunning()) say("开始刷视频，刷 " + Config.scrollMinutes(this) + " 分钟", true);
    }

    public void stopScroll() {
        if (engine != null && engine.isRunning()) engine.stop("手动停止");
        setStopVisible(false);
    }

    public boolean isScrolling() { return engine != null && engine.isRunning(); }

    public void showPet(boolean show) {
        ui.post(() -> {
            if (root == null) return;
            root.setVisibility(show ? View.VISIBLE : View.GONE);
        });
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public void onDestroy() {
        inst = null;
        try { if (root != null) wm.removeView(root); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}