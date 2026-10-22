package com.moe.whale;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** DeepSeek 对话客户端（流式） */
public class Brain {

    public interface Cb {
        /** 收到一个增量片段 */
        void onDelta(String piece);
        /** 全部结束；err 非空表示出错 */
        void onDone(String full, String err);
    }

    private final Context ctx;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled = false;
    private HttpURLConnection conn;

    public Brain(Context c) { this.ctx = c.getApplicationContext(); }

    public void cancel() {
        cancelled = true;
        try { if (conn != null) conn.disconnect(); } catch (Exception ignored) { }
    }

    /** 普通对话：自动带上人设 + 记忆 + 最近上下文 */
    public void chat(String userText, Cb cb) {
        List<String[]> msgs = new ArrayList<>();
        msgs.add(new String[]{"system", buildSystem()});
        for (String[] m : Store.get(ctx).recentMsgs(12)) msgs.add(m);
        msgs.add(new String[]{"user", userText});
        send(msgs, cb);
    }

    /** 定制对话：调用方自己组织消息 */
    public void send(List<String[]> msgs, Cb cb) {
        String key = Config.apiKey(ctx);
        if (key == null || key.trim().isEmpty()) {
            cb.onDone("", "还没填 API Key，去设置里填一下");
            return;
        }
        final String body = buildBody(msgs);
        new Thread(() -> {
            StringBuilder full = new StringBuilder();
            try {
                URL u = new URL(Config.apiBase(ctx).replaceAll("/+$", "") + "/chat/completions");
                conn = (HttpURLConnection) u.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(120000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + key);
                conn.setRequestProperty("Accept", "text/event-stream");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (code != 200) {
                    String err = readAll(conn.getErrorStream());
                    final String msg = "HTTP " + code + " " + trim(err, 200);
                    ui.post(() -> cb.onDone("", msg));
                    return;
                }

                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) {
                    if (cancelled) break;
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if (data.equals("[DONE]")) break;
                    try {
                        JSONObject o = new JSONObject(data);
                        JSONArray ch = o.optJSONArray("choices");
                        if (ch == null || ch.length() == 0) continue;
                        JSONObject d = ch.getJSONObject(0).optJSONObject("delta");
                        if (d == null) continue;
                        String piece = d.optString("content", "");
                        if (piece.isEmpty()) continue;
                        full.append(piece);
                        ui.post(() -> cb.onDelta(piece));
                    } catch (Exception ignored) { }
                }
                br.close();
                final String f = full.toString();
                ui.post(() -> cb.onDone(f, null));
            } catch (Exception e) {
                if (cancelled) { ui.post(() -> cb.onDone(full.toString(), null)); return; }
                final String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
                ui.post(() -> cb.onDone(full.toString(), msg));
            } finally {
                try { if (conn != null) conn.disconnect(); } catch (Exception ignored) { }
            }
        }).start();
    }

    private String buildSystem() {
        StringBuilder sb = new StringBuilder();
        sb.append(Config.persona(ctx)).append("\n\n");
        String facts = Store.get(ctx).factsText(30);
        if (!facts.isEmpty()) {
            sb.append("你记得关于用户的事：\n").append(facts).append('\n');
        }
        sb.append("\n现在是 ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",
                java.util.Locale.CHINA).format(new java.util.Date())).append("。");
        return sb.toString();
    }

    private String buildBody(List<String[]> msgs) {
        try {
            JSONArray arr = new JSONArray();
            for (String[] m : msgs) {
                JSONObject o = new JSONObject();
                o.put("role", m[0]);
                o.put("content", m[1]);
                arr.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("model", Config.model(ctx));
            root.put("messages", arr);
            root.put("stream", true);
            root.put("temperature", 1.0);
            root.put("max_tokens", 800);
            return root.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String readAll(java.io.InputStream in) {
        if (in == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) sb.append(l);
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private static String trim(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}