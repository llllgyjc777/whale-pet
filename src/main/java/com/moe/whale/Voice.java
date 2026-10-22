package com.moe.whale;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.Locale;

/** 语音：TTS 说 + ASR 听 */
public class Voice {

    // ================= 说话 =================
    private static TextToSpeech tts;
    private static boolean ttsReady = false;
    private static Runnable onSpeakDone;
    private static final Handler ui = new Handler(Looper.getMainLooper());

    public static void initTts(Context c) {
        if (tts != null) return;
        tts = new TextToSpeech(c.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(Locale.CHINA);
                ttsReady = (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED);
                if (!ttsReady) {
                    // 退回中文语音的通用设置
                    tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                    ttsReady = true;
                }
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { }
                    @Override public void onDone(String id) {
                        if (onSpeakDone != null) ui.post(onSpeakDone);
                    }
                    @Override public void onError(String id) {
                        if (onSpeakDone != null) ui.post(onSpeakDone);
                    }
                });
            }
        });
    }

    /** 说一句话；done 可为 null */
    public static void speak(Context c, String text, Runnable done) {
        String clean = stripForTts(text);
        if (clean.isEmpty()) { if (done != null) done.run(); return; }
        if (!Config.ttsOn(c) || tts == null || !ttsReady) { if (done != null) done.run(); return; }
        onSpeakDone = done;
        tts.setSpeechRate(Config.ttsRate(c));
        tts.setPitch(Config.ttsPitch(c));
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "whale_" + System.currentTimeMillis());
    }

    public static void stopSpeaking() {
        try { if (tts != null) tts.stop(); } catch (Exception ignored) { }
        onSpeakDone = null;
    }

    public static boolean isSpeaking() {
        try { return tts != null && tts.isSpeaking(); } catch (Exception e) { return false; }
    }

    /** 去掉 markdown / emoji，避免 TTS 念出符号 */
    private static String stripForTts(String s) {
        if (s == null) return "";
        return s.replaceAll("[*_`#>\\[\\]()~|]", "")
                .replaceAll("[\\uD800-\\uDFFF]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // ================= 听 =================
    public interface HearCb {
        void onPartial(String text);
        void onFinal(String text);
        void onErr(String msg);
    }

    private static SpeechRecognizer sr;
    private static HearCb hearCb;
    private static boolean listening = false;

    public static boolean canListen(Context c) {
        return SpeechRecognizer.isRecognitionAvailable(c);
    }

    public static void startListening(Context c, HearCb cb) {
        if (!canListen(c)) { cb.onErr("这台设备没有语音识别服务"); return; }
        stopListening();
        hearCb = cb;
        ui.post(() -> {
            try {
                sr = SpeechRecognizer.createSpeechRecognizer(c.getApplicationContext());
                sr.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle b) { }
                    @Override public void onBeginningOfSpeech() { }
                    @Override public void onRmsChanged(float v) { }
                    @Override public void onBufferReceived(byte[] b) { }
                    @Override public void onEndOfSpeech() { }
                    @Override public void onError(int code) {
                        listening = false;
                        if (hearCb != null) hearCb.onErr(errText(code));
                    }
                    @Override public void onResults(Bundle b) {
                        listening = false;
                        ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (hearCb != null) {
                            if (r != null && !r.isEmpty()) hearCb.onFinal(r.get(0));
                            else hearCb.onErr("没听清");
                        }
                    }
                    @Override public void onPartialResults(Bundle b) {
                        ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (r != null && !r.isEmpty() && hearCb != null) hearCb.onPartial(r.get(0));
                    }
                    @Override public void onEvent(int i, Bundle b) { }
                });
                Intent it = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                it.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                it.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
                it.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                it.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
                it.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, c.getPackageName());
                sr.startListening(it);
                listening = true;
            } catch (Exception e) {
                listening = false;
                cb.onErr(e.getClass().getSimpleName());
            }
        });
    }

    public static void stopListening() {
        try {
            if (sr != null) { sr.stopListening(); sr.destroy(); }
        } catch (Exception ignored) { }
        sr = null;
        listening = false;
    }

    public static boolean isListening() { return listening; }

    private static String errText(int code) {
        switch (code) {
            case SpeechRecognizer.ERROR_AUDIO: return "录音出错";
            case SpeechRecognizer.ERROR_CLIENT: return "识别客户端出错";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "没有录音权限";
            case SpeechRecognizer.ERROR_NETWORK: return "网络出错";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "网络超时";
            case SpeechRecognizer.ERROR_NO_MATCH: return "没听清";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "识别器忙";
            case SpeechRecognizer.ERROR_SERVER: return "服务端出错";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "没听到声音";
            default: return "识别失败(" + code + ")";
        }
    }
}