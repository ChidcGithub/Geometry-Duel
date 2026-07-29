package com.geometryduel.ui;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.geometryduel.ThemeData;
import com.geometryduel.render.Fonts;
import com.geometryduel.render.Shapes;

/**
 * Material 3 Expressive 按钮：
 * - 全圆角（radius = h/2），FILLED / TONAL / OUTLINE / DANGER 四型
 * - 入场动画：上浮 24dp + 淡入（decelerate）
 * - 按压动画：整体缩放到 0.94，释放弹簧回弹
 */
public class M3Button {
    public static final int FILLED = 0;
    public static final int TONAL = 1;
    public static final int OUTLINE = 2;
    public static final int DANGER = 3;

    public String text;
    public float x, y, w, h;
    public boolean visible = true;
    public boolean enabled = true;
    public int style = TONAL;
    /** 入场进度 0..1，由屏幕 stagger 时钟驱动（1=就位）。 */
    public float entrance = 1f;

    private boolean pressed;
    private float pressT;
    private final GlyphLayout layout = new GlyphLayout();

    public M3Button(String text, float w, float h) {
        this.text = text;
        this.w = w;
        this.h = h;
    }

    public void setCenter(float cx, float cy) {
        x = cx - w / 2f;
        y = cy - h / 2f;
    }

    public boolean contains(float px, float py) {
        return visible && enabled && px >= x && px <= x + w && py >= y && py <= y + h;
    }

    public void setPressed(boolean p) {
        pressed = p;
    }

    public void update(float delta) {
        float target = pressed ? 1f : 0f;
        float speed = pressed ? 10f : 4.5f; // 按下迅速、释放缓慢（弹性手感）
        pressT += (target - pressT) * Math.min(1f, delta * speed);
        if (Math.abs(pressT - target) < 0.001f) pressT = target;
    }

    /** 入场上浮量（y 向下为正，负值=向上偏移）。 */
    private float rise() {
        return (1f - Anim.decelerate(entrance)) * 24f;
    }

    private float pressScale() {
        return 1f - 0.06f * pressT;
    }

    private float alpha() {
        float a = Anim.decelerate(entrance);
        return enabled ? a : a * 0.38f;
    }

    public void draw(Shapes s, ThemeData theme) {
        if (!visible || entrance <= 0f) return;
        float cx = x + w / 2f, cy = y + h / 2f;
        int a = (int) (alpha() * 255f);
        s.push();
        s.translate(cx, cy);
        s.scale(pressScale(), pressScale());
        s.translate(-cx, -cy - rise());
        if (style == OUTLINE) {
            s.noFill();
            s.stroke(theme.stroke, a);
            s.strokeWeight(2f);
        } else {
            s.noStroke();
            s.doFill();
            s.fill(style == FILLED ? theme.primary
                    : style == DANGER ? theme.error
                    : theme.primaryContainer, a);
        }
        s.roundRect(x, y - rise(), w, h, h / 2f);
        s.pop();
    }

    public void drawText(SpriteBatch batch, Fonts fonts, ThemeData theme) {
        if (!visible || entrance <= 0f) return;
        BitmapFont f = fonts.body();
        float scale = pressScale();
        f.getData().setScale(scale);
        layout.setText(f, text);
        if (style == FILLED || style == DANGER) f.setColor(theme.onPrimary.r, theme.onPrimary.g, theme.onPrimary.b, alpha());
        else if (style == TONAL) f.setColor(theme.onPrimaryContainer.r, theme.onPrimaryContainer.g, theme.onPrimaryContainer.b, alpha());
        else f.setColor(theme.primary.r, theme.primary.g, theme.primary.b, alpha());
        f.draw(batch, text, x + (w - layout.width) / 2f,
                y - rise() + (h + layout.height) / 2f);
        f.getData().setScale(1f);
    }
}
