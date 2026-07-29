package com.geometryduel.ui;

import com.badlogic.gdx.graphics.Color;
import com.geometryduel.ThemeData;
import com.geometryduel.render.Shapes;

/**
 * Material 3 开关：轨道全圆角，滑块滑动+变大的弹簧动画。
 * 尺寸由外部按 unit 缩放设置（设计规格 52x32dp）。
 */
public class M3Switch {
    public float x, y, w, h;
    public boolean value;
    public boolean enabled = true;

    private float animT;
    private final Color track = new Color();
    private final Color thumb = new Color();

    public M3Switch(boolean value) {
        this.value = value;
        this.animT = value ? 1f : 0f;
    }

    public void toggle() {
        value = !value;
    }

    public void setCenter(float cx, float cy) {
        x = cx - w / 2f;
        y = cy - h / 2f;
    }

    public boolean contains(float px, float py) {
        // 触控热区放大到 48dp
        float pad = Math.max(0f, (h * 1.5f - h) / 2f);
        return enabled && px >= x - pad && px <= x + w + pad && py >= y - pad && py <= y + h + pad;
    }

    public void update(float delta) {
        float target = value ? 1f : 0f;
        animT += (target - animT) * Math.min(1f, delta * 10f);
        if (Math.abs(animT - target) < 0.001f) animT = target;
    }

    public void draw(Shapes s, ThemeData theme) {
        float t = Anim.softSpring(animT);
        t = Anim.clamp01(t);
        float a = enabled ? 1f : 0.38f;

        // 轨道：off=surfaceVariant+描边，on=primary
        track.set(theme.surfaceVariant).lerp(theme.primary, t);
        track.a = a;
        s.doFill();
        s.fill(track);
        s.noStroke();
        s.roundRect(x, y, w, h, h / 2f);
        if (t < 0.5f) {
            s.noFill();
            s.stroke(theme.onSurfaceVariant.r, theme.onSurfaceVariant.g, theme.onSurfaceVariant.b, a * (1f - t * 2f));
            s.strokeWeight(2f);
            s.roundRect(x, y, w, h, h / 2f);
            s.doFill();
        }

        // 滑块：位置滑动 + 半径变大（off 0.30h → on 0.42h）
        thumb.set(theme.onSurfaceVariant).lerp(theme.onPrimary, t);
        thumb.a = a;
        s.fill(thumb);
        s.noStroke();
        float r = Anim.lerp(h * 0.30f, h * 0.42f, t);
        float cx = Anim.lerp(x + h / 2f, x + w - h / 2f, t);
        s.filledCircle(cx, y + h / 2f, r * 2f);
    }
}
