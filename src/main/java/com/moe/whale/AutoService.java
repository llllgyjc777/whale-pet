package com.moe.whale;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Path;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/** 无障碍服务：读屏 / 点击 / 滑动 / 刷视频 */
public class AutoService extends AccessibilityService {

    private static AutoService inst;

    public static AutoService get() { return inst; }

    public static boolean isOn(Context c) {
        String flat = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (flat == null) return false;
        String me = new ComponentName(c, AutoService.class).flattenToString();
        String meShort = new ComponentName(c, AutoService.class).flattenToShortString();
        return flat.contains(me) || flat.contains(meShort);
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        inst = this;
        Store.get(this).log("A11Y", "无障碍服务已连接");
    }

    @Override public void onDestroy() {
        inst = null;
        super.onDestroy();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }
    @Override public void onInterrupt() { }

    // ================= 读屏 =================

    /** 当前窗口里所有有文字/可点的节点，格式：文字|类名|可点 */
    public List<Item> dump() {
        List<Item> out = new ArrayList<>();
        AccessibilityNodeInfo root;
        try { root = getRootInActiveWindow(); } catch (Exception e) { return out; }
        if (root == null) return out;
        walk(root, out, 0);
        return out;
    }

    private void walk(AccessibilityNodeInfo n, List<Item> out, int depth) {
        if (n == null || depth > 40) return;
        CharSequence t = n.getText();
        CharSequence d = n.getContentDescription();
        String label = !TextUtils.isEmpty(t) ? t.toString()
                : (!TextUtils.isEmpty(d) ? d.toString() : "");
        if (!label.isEmpty() || n.isClickable()) {
            Item it = new Item();
            it.label = label;
            it.cls = n.getClassName() == null ? "" : n.getClassName().toString();
            it.clickable = n.isClickable();
            it.node = n;
            android.graphics.Rect r = new android.graphics.Rect();
            n.getBoundsInScreen(r);
            it.rect = r;
            out.add(it);
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            try { walk(n.getChild(i), out, depth + 1); } catch (Exception ignored) { }
        }
    }

    public static class Item {
        public String label;
        public String cls;
        public boolean clickable;
        public android.graphics.Rect rect;
        public AccessibilityNodeInfo node;

        @Override public String toString() {
            return (label.isEmpty() ? "(无文字)" : label) + " [" + simpleCls() + "]"
                    + (clickable ? " 可点" : "");
        }

        private String simpleCls() {
            if (cls == null || cls.isEmpty()) return "?";
            int i = cls.lastIndexOf('.');
            return i >= 0 ? cls.substring(i + 1) : cls;
        }
    }

    /** 把屏幕内容压成一段纯文本，给 AI 看 */
    public String screenText(int maxChars) {
        StringBuilder sb = new StringBuilder();
        for (Item it : dump()) {
            if (it.label.isEmpty()) continue;
            String s = it.label.trim();
            if (s.isEmpty()) continue;
            sb.append(s).append('\n');
            if (sb.length() > maxChars) break;
        }
        return sb.toString();
    }

    // ================= 操作 =================

    /** 按文字找节点并点击 */
    public boolean tapText(String text) {
        if (text == null || text.isEmpty()) return false;
        AccessibilityNodeInfo root;
        try { root = getRootInActiveWindow(); } catch (Exception e) { return false; }
        if (root == null) return false;

        // 1. 精确匹配
        List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText(text);
        if (list != null && !list.isEmpty()) {
            for (AccessibilityNodeInfo n : list) {
                if (clickSelfOrParent(n)) return true;
            }
        }
        // 2. 包含匹配
        for (Item it : dump()) {
            if (it.label.contains(text) && clickSelfOrParent(it.node)) return true;
        }
        // 3. 兜底：按坐标点
        for (Item it : dump()) {
            if (it.label.contains(text) && it.rect != null && it.rect.width() > 0) {
                return clickXY(it.rect.centerX(), it.rect.centerY());
            }
        }
        return false;
    }

    private boolean clickSelfOrParent(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo cur = n;
        for (int i = 0; i < 6 && cur != null; i++) {
            if (cur.isClickable()) {
                try { return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK); }
                catch (Exception ignored) { }
            }
            cur = cur.getParent();
        }
        return false;
    }

    public boolean clickXY(float x, float y) {
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription.StrokeDescription st = new GestureDescription.StrokeDescription(p, 0, 60);
        GestureDescription g = new GestureDescription.Builder().addStroke(st).build();
        try { return dispatchGesture(g, null, null); } catch (Exception e) { return false; }
    }

    /** 模拟滑动：从 (x1,y1) 到 (x2,y2)，dur 毫秒 */
    public boolean swipe(float x1, float y1, float x2, float y2, long dur) {
        Path p = new Path();
        p.moveTo(x1, y1);
        // 稍微加一点曲线，避免被判定为机器滑动
        p.quadTo(x1 + (x2 - x1) * 0.5f + 8, y1 + (y2 - y1) * 0.5f, x2, y2);
        GestureDescription.StrokeDescription st = new GestureDescription.StrokeDescription(p, 0, Math.max(120, dur));
        GestureDescription g = new GestureDescription.Builder().addStroke(st).build();
        try { return dispatchGesture(g, null, null); } catch (Exception e) { return false; }
    }

    /** 向上滑（看下一个视频） */
    public boolean swipeUp() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float x = dm.widthPixels * 0.5f;
        float y1 = dm.heightPixels * 0.78f;
        float y2 = dm.heightPixels * 0.22f;
        long dur = 220 + (long) (Math.random() * 140); // 220~360ms，接近人手
        return swipe(x, y1, x, y2, dur);
    }

    public boolean back() {
        try { return performGlobalAction(GLOBAL_ACTION_BACK); } catch (Exception e) { return false; }
    }

    public boolean home() {
        try { return performGlobalAction(GLOBAL_ACTION_HOME); } catch (Exception e) { return false; }
    }

    /** 当前前台包名 */
    public String foregroundPkg() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return "";
            CharSequence p = root.getPackageName();
            return p == null ? "" : p.toString();
        } catch (Exception e) { return ""; }
    }
}