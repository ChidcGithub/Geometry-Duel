package com.geometryduel.ui;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.geometryduel.ThemeData;
import com.geometryduel.render.Shapes;

/**
 * Metro 风格文本按钮。
 * STYLE_OUTLINE：描边矩形（默认，兼容旧样式）；
 * STYLE_PRIMARY：主色大色块（primary 底 + onPrimary 字）；
 * STYLE_CONTAINER：容器色块（primaryContainer 底 + onPrimaryContainer 字）。
 */
public class TextButton {
    public static final int STYLE_OUTLINE = 0;
    public static final int STYLE_PRIMARY = 1;
    public static final int STYLE_CONTAINER = 2;

    public float x, y, w, h;
    public String text;
    public boolean visible = true;
    public int style = STYLE_OUTLINE;
    private final GlyphLayout layout = new GlyphLayout();

    public TextButton(String text, float cx, float cy, float w, float h) {
        this.text = text;
        this.w = w;
        this.h = h;
        setCenter(cx, cy);
    }

    public final void setCenter(float cx, float cy) {
        this.x = cx - w / 2f;
        this.y = cy - h / 2f;
    }

    public boolean contains(float px, float py) {
        return visible && px >= x && px <= x + w && py >= y && py <= y + h;
    }

    public void draw(Shapes s, ThemeData theme) {
        if (!visible) return;
        if (style == STYLE_OUTLINE) {
            s.noFill();
            s.stroke(theme.stroke);
            s.strokeWeight(2f);
            s.rect(x, y, w, h);
        } else {
            s.noStroke();
            s.doFill();
            s.fill(style == STYLE_PRIMARY ? theme.primary : theme.primaryContainer);
            s.rect(x, y, w, h);
        }
    }

    public void drawText(SpriteBatch batch, BitmapFont font, ThemeData theme, float scale) {
        if (!visible) return;
        font.getData().setScale(scale);
        layout.setText(font, text);
        font.setColor(style == STYLE_PRIMARY ? theme.onPrimary
                : style == STYLE_CONTAINER ? theme.onPrimaryContainer
                : theme.text);
        font.draw(batch, text, x + (w - layout.width) / 2f, y + (h + layout.height) / 2f);
        font.getData().setScale(1f);
    }
}
