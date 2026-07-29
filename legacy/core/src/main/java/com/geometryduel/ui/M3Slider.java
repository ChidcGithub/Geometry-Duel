package com.geometryduel.ui;

import com.geometryduel.ThemeData;
import com.geometryduel.render.Shapes;

/**
 * Material 3 滑块（音量等连续值）：非激活轨 + 激活轨 + 圆形滑块。
 * y 为轨道中心线；trackH/thumbR 由布局按 unit 设置。
 */
public class M3Slider {
    public float x, y, w;
    public float value; // 0..1
    public float trackH = 6f;
    public float thumbR = 11f;
    public float touchH = 48f;
    public boolean enabled = true;
    /** 按下时滑块放大（M3 expressive 手感）。 */
    private float pressT;

    private boolean dragging;

    public boolean onTouchDown(float px, float py) {
        if (enabled && px >= x - thumbR && px <= x + w + thumbR
                && py >= y - touchH / 2f && py <= y + touchH / 2f) {
            dragging = true;
            setFromX(px);
            return true;
        }
        return false;
    }

    public void onDrag(float px) {
        if (dragging) setFromX(px);
    }

    public void onUp() {
        dragging = false;
    }

    public boolean isDragging() {
        return dragging;
    }

    private void setFromX(float px) {
        value = Anim.clamp01((px - x) / w);
    }

    public void update(float delta) {
        float target = dragging ? 1f : 0f;
        pressT += (target - pressT) * Math.min(1f, delta * 10f);
    }

    public void draw(Shapes s, ThemeData theme) {
        float a = enabled ? 1f : 0.38f;
        float thumbX = x + w * value;
        // 非激活轨
        s.doFill();
        s.fill(theme.surfaceVariant.r, theme.surfaceVariant.g, theme.surfaceVariant.b, a);
        s.noStroke();
        s.roundRect(x, y - trackH / 2f, w, trackH, trackH / 2f);
        // 激活轨
        if (value > 0.001f) {
            s.fill(theme.primary.r, theme.primary.g, theme.primary.b, a);
            s.roundRect(x, y - trackH / 2f, w * value, trackH, trackH / 2f);
        }
        // 滑块（按下放大 1.15x，带回弹）
        float r = thumbR * (1f + 0.15f * Anim.softSpring(pressT));
        s.fill(theme.primary.r, theme.primary.g, theme.primary.b, a);
        s.filledCircle(thumbX, y, r * 2f);
    }
}
