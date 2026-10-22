package com.moe.whale;

import android.content.Context;
import android.content.SharedPreferences;

/** 全局配置读写 */
public class Config {
    private static final String P = "whale_cfg";
    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(P, Context.MODE_PRIVATE);
    }

    // ---- DeepSeek ----
    public static String apiKey(Context c) { return sp(c).getString("api_key", ""); }
    public static void setApiKey(Context c, String v) { sp(c).edit().putString("api_key", v).apply(); }

    public static String apiBase(Context c) {
        return sp(c).getString("api_base", "https://api.deepseek.com");
    }
    public static void setApiBase(Context c, String v) { sp(c).edit().putString("api_base", v).apply(); }

    public static String model(Context c) { return sp(c).getString("model", "deepseek-chat"); }
    public static void setModel(Context c, String v) { sp(c).edit().putString("model", v).apply(); }

    // ---- 语音 ----
    public static boolean ttsOn(Context c) { return sp(c).getBoolean("tts_on", true); }
    public static void setTtsOn(Context c, boolean v) { sp(c).edit().putBoolean("tts_on", v).apply(); }

    public static float ttsRate(Context c) { return sp(c).getFloat("tts_rate", 1.0f); }
    public static void setTtsRate(Context c, float v) { sp(c).edit().putFloat("tts_rate", v).apply(); }

    public static float ttsPitch(Context c) { return sp(c).getFloat("tts_pitch", 1.15f); }
    public static void setTtsPitch(Context c, float v) { sp(c).edit().putFloat("tts_pitch", v).apply(); }

    // ---- 自动化总开关 ----
    public static boolean autoEnabled(Context c) { return sp(c).getBoolean("auto_enabled", false); }
    public static void setAutoEnabled(Context c, boolean v) { sp(c).edit().putBoolean("auto_enabled", v).apply(); }

    // 刷视频
    public static boolean scrollEnabled(Context c) { return sp(c).getBoolean("scroll_enabled", false); }
    public static void setScrollEnabled(Context c, boolean v) { sp(c).edit().putBoolean("scroll_enabled", v).apply(); }

    public static int scrollMinutes(Context c) { return sp(c).getInt("scroll_minutes", 30); }
    public static void setScrollMinutes(Context c, int v) { sp(c).edit().putInt("scroll_minutes", v).apply(); }

    public static int scrollIntervalMs(Context c) { return sp(c).getInt("scroll_interval", 8000); }
    public static void setScrollIntervalMs(Context c, int v) { sp(c).edit().putInt("scroll_interval", v).apply(); }

    /** 0=只看 1=点赞喜欢的 2=全赞 */
    public static int likePolicy(Context c) { return sp(c).getInt("like_policy", 1); }
    public static void setLikePolicy(Context c, int v) { sp(c).edit().putInt("like_policy", v).apply(); }

    public static String targetPkg(Context c) { return sp(c).getString("target_pkg", "com.ss.android.ugc.aweme"); }
    public static void setTargetPkg(Context c, String v) { sp(c).edit().putString("target_pkg", v).apply(); }

    // 读完内容后是否让小鲸说一句
    public static boolean narrate(Context c) { return sp(c).getBoolean("narrate", true); }
    public static void setNarrate(Context c, boolean v) { sp(c).edit().putBoolean("narrate", v).apply(); }

    // ---- 人设 ----
    public static String persona(Context c) {
        return sp(c).getString("persona",
            "你是「小鲸」，一只住在用户手机上的 Q 版小鲸鱼。说话短、口语化、带一点俏皮。\n" +
            "回复长度控制在 40 字以内，除非用户明确要求详细解释。\n" +
            "你能操作手机时会先简短说明要做什么。");
    }
    public static void setPersona(Context c, String v) { sp(c).edit().putString("persona", v).apply(); }
}