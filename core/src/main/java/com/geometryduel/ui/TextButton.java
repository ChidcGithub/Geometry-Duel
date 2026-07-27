package com.geometryduel.ui;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.geometryduel.ThemeData;
import com.geometryduel.render.Shapes;

/** 简单文本按钮（描边矩形 + 居中文字），用于菜单/设置/游戏内控件。 */
public class TextButton {
    public float x, y, w, h;
    public String text;
    public boolean visible = true;
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
        s.noFill();
        s.stroke(theme.stroke);
        s.strokeWeight(2f);
        s.rect(x, y, w, h);
    }

    public void drawText(SpriteBatch batch, BitmapFont font, ThemeData theme, float scale) {
        if (!visible) return;
        font.getData().setScale(scale);
        layout.setText(font, text);
        font.setColor(theme.text);
        font.draw(batch, text, x + (w - layout.width) / 2f, y + (h + layout.height) / 2f);
        font.getData().setScale(1f);
    }
}
