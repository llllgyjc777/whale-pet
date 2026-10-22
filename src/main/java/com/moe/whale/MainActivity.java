package com.moe.whale;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/** 主界面：设置 + 开关 + 诊断 */
public class MainActivity extends Activity {

    private LinearLayout col;
    private TextView statusView;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        ScrollView sv = new ScrollView(this);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        col.setPadding(pad, pad, pad, pad);
        col.setBackgroundColor(0xFF0B1220);
        sv.addView(col);
        setContentView(sv);

        title("小鲸");
        sub("住在你手机上的 AI 桌宠 · 会说话 · 会刷视频");

        statusView = new TextView(this);
        statusView.setTextColor(0xFF8FB8DE);
        statusView.setTextSize(12f);
        statusView.setPadding(0, dp(6), 0, dp(14));
        col.addView(statusView);

        buildPermissionSection();
        buildApiSection();
        buildVoiceSection();
        buildAutoSection();
        buildDiagnostics();

        refreshStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        if (statusView == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append(Settings.canDrawOverlays(this) ? "✓ 悬浮窗权限" : "✗ 悬浮窗权限").append("    ");
        sb.append(AutoService.get() != null ? "✓ 无障碍已连接" : "✗ 无障碍未连接").append("    ");
        sb.append(Config.apiKey(this).isEmpty() ? "✗ 未填 Key" : "✓ Key 已配置");
        statusView.setText(sb.toString());
    }

    // ============ 权限 ============
    private void buildPermissionSection() {
        head("① 权限");
        btn("开悬浮窗权限", () -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });
        btn("开无障碍（读屏/自动操作）", () -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, "在列表里找到「小鲸」并打开", Toast.LENGTH_LONG).show();
        });
        btn("授权录音（语音输入）", () -> {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, 10);
        });
        if (Build.VERSION.SDK_INT >= 33) {
            btn("授权通知", () -> ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11));
        }
    }

    // ============ API ============
    private void buildApiSection() {
        head("② 大脑（DeepSeek）");
        edit("API Key（sk- 开头）", Config.apiKey(this), false,
                v -> Config.setApiKey(this, v.trim()));
        edit("API 地址", Config.apiBase(this), false,
                v -> Config.setApiBase(this, v.trim()));
        edit("模型", Config.model(this), false,
                v -> Config.setModel(this, v.trim()));
        edit("人设", Config.persona(this), true,
                v -> Config.setPersona(this, v));
    }

    // ============ 语音 ============
    private void buildVoiceSection() {
        head("③ 语音");
        check("让小鲸说话（TTS）", Config.ttsOn(this),
                v -> Config.setTtsOn(this, v));
        seek("语速", 1, 30, (int) (Config.ttsRate(this) * 10),
                v -> Config.setTtsRate(this, v / 10f));
        seek("音调", 5, 25, (int) (Config.ttsPitch(this) * 10),
                v -> Config.setTtsPitch(this, v / 10f));
        btn("试听一句", () -> {
            Voice.initTts(this);
            Voice.speak(this, "我是小鲸，住在这台手机里，随时叫我。", null);
        });
    }

    // ============ 自动化 ============
    private void buildAutoSection() {
        head("④ 自动化");
        check("自动化总开关（必须先开这个）", Config.autoEnabled(this),
                v -> Config.setAutoEnabled(this, v));
        check("允许自动刷视频", Config.scrollEnabled(this),
                v -> Config.setScrollEnabled(this, v));
        check("刷的时候偶尔说一句（会消耗 token）", Config.narrate(this),
                v -> Config.setNarrate(this, v));

        seek("刷视频时长（分钟）", 5, 120, Config.scrollMinutes(this),
                v -> Config.setScrollMinutes(this, v));
        seek("每个视频停留（秒）", 3, 60, Config.scrollIntervalMs(this) / 1000,
                v -> Config.setScrollIntervalMs(this, v * 1000));

        edit("目标 App 包名\n抖音 com.ss.android.ugc.aweme\n快手 com.smile.gifmaker",
                Config.targetPkg(this), false, v -> Config.setTargetPkg(this, v.trim()));

        btn("▶ 开始刷视频", () -> {
            if (!Config.autoEnabled(this)) {
                toast("先把自动化总开关打开");
                return;
            }
            if (!Config.scrollEnabled(this)) {
                toast("先打开「允许自动刷视频」");
                return;
            }
            if (AutoService.get() == null) {
                toast("无障碍没连接，去第①步开一下");
                return;
            }
            PetService p = PetService.get();
            if (p == null) { toast("小鲸还没启动，先回桌面看看悬浮窗在不在"); return; }
            p.startScroll();
            Toast.makeText(this, "回桌面看它干活", Toast.LENGTH_SHORT).show();
            moveTaskToBack(true);
        });

        btn("■ 停止", () -> {
            PetService p = PetService.get();
            if (p != null) p.stopScroll();
            toast("已停止");
        });
    }

    // ============ 诊断 ============
    private void buildDiagnostics() {
        head("⑤ 诊断");
        btn("测试：读当前屏幕", () -> {
            AutoService a = AutoService.get();
            if (a == null) { toast("无障碍未连接"); return; }
            moveTaskToBack(true);
            col.postDelayed(() -> {
                String t = a.screenText(400);
                runOnUiThread(() -> alert("屏幕文字（前 400 字）",
                        t.trim().isEmpty() ? "（读不到文字 —— 可能该页面封锁了无障碍）" : t));
            }, 1200);
        });

        btn("查看操作日志", () -> {
            StringBuilder sb = new StringBuilder();
            for (String l : Store.get(this).recentLogs(40)) sb.append(l).append('\n');
            alert("最近记录", sb.length() == 0 ? "还没有记录" : sb.toString());
        });

        btn("清空对话记忆", () -> {
            Store.get(this).clearMsgs();
            toast("已清空");
        });

        btn("重启小鲸（悬浮窗）", () -> {
            stopService(new Intent(this, PetService.class));
            col.postDelayed(() -> startPet(), 400);
        });
    }

    private void startPet() {
        if (!Settings.canDrawOverlays(this)) { toast("先给悬浮窗权限"); return; }
        startForegroundService(new Intent(this, PetService.class));
        toast("小鲸已启动");
        moveTaskToBack(true);
    }

    // ============ UI 小工具 ============
    private void title(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(26f);
        v.setTextColor(0xFFEAF3FB);
        v.setPadding(0, dp(10), 0, dp(4));
        col.addView(v);
    }

    private void sub(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(12f);
        v.setTextColor(0xFF6E8AA6);
        col.addView(v);
    }

    private void head(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(15f);
        v.setTextColor(0xFF5FD3F3);
        v.setPadding(0, dp(20), 0, dp(8));
        col.addView(v);
    }

    private void btn(String label, Runnable r) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13f);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(8);
        b.setLayoutParams(p);
        b.setOnClickListener(v -> r.run());
        col.addView(b);
    }

    private void check(String label, boolean def, java.util.function.Consumer<Boolean> cb) {
        CheckBox c = new CheckBox(this);
        c.setText(label);
        c.setTextSize(13f);
        c.setTextColor(0xFFD8E6F2);
        c.setChecked(def);
        c.setPadding(0, dp(6), 0, dp(6));
        c.setOnCheckedChangeListener((v, checked) -> cb.accept(checked));
        col.addView(c);
    }

    private void edit(String label, String value, boolean multiline,
                      java.util.function.Consumer<String> cb) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(12f);
        l.setTextColor(0xFF8AA8C2);
        l.setPadding(0, dp(10), 0, dp(2));
        col.addView(l);

        EditText e = new EditText(this);
        e.setText(value);
        e.setTextSize(12f);
        e.setTextColor(0xFFE4EFF8);
        e.setHintTextColor(0xFF4A6480);
        if (multiline) {
            e.setSingleLine(false);
            e.setMinLines(3);
            e.setGravity(Gravity.TOP | Gravity.START);
        } else {
            e.setSingleLine(true);
        }
        e.setOnFocusChangeListener((v, has) -> { if (!has) cb.accept(e.getText().toString()); });
        col.addView(e);
    }

    private void seek(String label, int min, int max, int cur,
                      java.util.function.IntConsumer cb) {
        TextView l = new TextView(this);
        l.setText(label + "：" + cur);
        l.setTextSize(12f);
        l.setTextColor(0xFF8AA8C2);
        l.setPadding(0, dp(10), 0, 0);
        col.addView(l);

        SeekBar s = new SeekBar(this);
        s.setMax(max - min);
        s.setProgress(Math.max(0, Math.min(max - min, cur - min)));
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                int val = p + min;
                l.setText(label + "：" + val);
                if (u) cb.accept(val);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
        col.addView(s);
    }

    private void alert(String title, String msg) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("好", null)
                .show();
    }

    private void toast(String t) { Toast.makeText(this, t, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}