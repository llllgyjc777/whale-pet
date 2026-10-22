package com.moe.whale;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/** 代码绘制的 Q 版小鲸鱼：呼吸 + 眨眼 + 说话张嘴 */
public class WhaleView extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private long t0 = SystemClock.uptimeMillis();
    private long blinkAt = 0;
    private boolean blinking = false;
    private long talkUntil = 0;

    private int body = 0xFF5B9BD5;      // 鲸鱼蓝
    private int bodyDark = 0xFF3F7FB5;
    private int belly = 0xFFE8F2FA;
    private int ink = 0xFF17212B;

    public WhaleView(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    /** 开始说话：嘴巴动 1.2 秒 */
    public void talk() { talkUntil = SystemClock.uptimeMillis() + 1200; }

    @Override protected void onDraw(Canvas cv) {
        long now = SystemClock.uptimeMillis();
        float t = (now - t0) / 1000f;

        int w = getWidth(), h = getHeight();
        float cx = w / 2f;
        float scale = Math.min(w, h) / 200f;

        // 呼吸：上下浮动 + 轻微缩放
        float bob = (float) Math.sin(t * 1.6) * 4f * scale;
        float breathe = 1f + (float) Math.sin(t * 1.6) * 0.025f;
        cv.save();
        cv.translate(cx, h * 0.56f + bob);
        cv.scale(scale * breathe, scale * (2f - breathe));

        // ---- 尾鳍 ----
        p.setColor(bodyDark);
        Path tail = new Path();
        tail.moveTo(-58, 8);
        tail.quadTo(-86, -22, -78, 22);
        tail.quadTo(-92, 16, -84, 44);
        tail.quadTo(-66, 34, -52, 30);
        tail.close();
        cv.drawPath(tail, p);

        // ---- 身体 ----
        p.setColor(body);
        RectF bodyR = new RectF(-66, -46, 62, 46);
        cv.drawOval(bodyR, p);

        // ---- 肚子 ----
        p.setColor(belly);
        RectF bellyR = new RectF(-44, -6, 52, 42);
        cv.drawOval(bellyR, p);

        // ---- 头顶喷水（可爱小水花）----
        p.setColor(0xCCFFFFFF);
        float spout = (float) Math.max(0, Math.sin(t * 2.2)) * 10f;
        cv.drawCircle(4, -58 - spout, 4f, p);
        cv.drawCircle(-6, -66 - spout * 1.3f, 3f, p);
        cv.drawCircle(12, -64 - spout * 1.1f, 2.5f, p);

        // ---- 眼睛（带眨眼）----
        // 每 3~6 秒眨一次
        if (!blinking && now > blinkAt + 3200 + Math.random() * 3000) {
            blinking = true;
            blinkAt = now;
        }
        if (blinking && now > blinkAt + 140) blinking = false;

        p.setColor(ink);
        float eyeY = -12;
        if (blinking) {
            p.setStrokeWidth(2.4f);
            p.setStyle(Paint.Style.STROKE);
            cv.drawLine(14, eyeY, 26, eyeY, p);
            p.setStyle(Paint.Style.FILL);
        } else {
            cv.drawCircle(20, eyeY, 6.2f, p);
            // 高光
            p.setColor(0xFFFFFFFF);
            cv.drawCircle(22.4f, eyeY - 2.2f, 2.2f, p);
            p.setColor(ink);
        }

        // ---- 嘴（说话时开合）----
        boolean talking = now < talkUntil;
        float open = talking ? (float) (Math.abs(Math.sin(t * 18)) * 7 + 2) : 0;
        p.setColor(ink);
        if (open > 0.5f) {
            RectF mouth = new RectF(18, 4, 36, 4 + open);
            cv.drawOval(mouth, p);
        } else {
            p.setStrokeWidth(2.2f);
            p.setStyle(Paint.Style.STROKE);
            RectF smile = new RectF(18, 0, 34, 12);
            cv.drawArc(smile, 10, 140, false, p);
            p.setStyle(Paint.Style.FILL);
        }

        // ---- 腮红 ----
        p.setColor(0x44FF7A9C);
        cv.drawCircle(30, 4, 6f, p);
        cv.drawCircle(-26, 6, 5f, p);

        cv.restore();

        // 持续刷新动画（只在可见时）
        if (isShown()) invalidate();
    }
}