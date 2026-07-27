package com.geometryduel;

import com.badlogic.gdx.graphics.Color;

/**
 * 主题配色，数值逐项还原自：
 * - 亮色：pama1234.gdx.game.duel.util.theme.ThemeData#init()
 * - 暗色：assets/theme/darkTheme.yaml（0xRRGGBBAA）
 */
public class ThemeData {
    public Color background;
    public Color backgroundLine;
    public Color longbowArrow;
    public Color longbowEffect;
    public Color longbowLine;
    public Color longbowStroke;
    public Color particleDefault;
    public Color playerDamaged;
    public Color player_a;
    public Color player_b;
    public Color ring;
    public Color shortbowArrow;
    public Color squareParticles;
    public Color stroke;
    public Color teleportEffect;
    public Color teleportStroke;
    public Color text;

    public enum Type {Light, Dark}

    /** 亮色主题（ThemeData.init 默认值，Duel.color 0..255 灰度/RGB）。 */
    public static ThemeData light() {
        ThemeData t = new ThemeData();
        t.shortbowArrow = gray(192);
        t.longbowArrow = gray(64);
        t.squareParticles = gray(0);
        t.longbowLine = rgb(192, 64, 64);
        t.longbowEffect = rgb(192, 64, 64);
        t.teleportStroke = rgb(0, 89, 132);
        t.teleportEffect = rgb(0, 132, 196);
        t.longbowStroke = new Color(0f, 0f, 0f, 128 / 255f); // Duel.color(0, 128)：灰度0+透明度128
        t.playerDamaged = rgb(192, 64, 64);
        t.ring = gray(0);
        t.particleDefault = gray(0);
        t.backgroundLine = gray(224);
        t.player_a = gray(255);
        t.player_b = gray(0);
        t.text = gray(0);
        t.background = gray(255);
        t.stroke = gray(0);
        return t;
    }

    /** 暗色主题（darkTheme.yaml，0xRRGGBBAA）。 */
    public static ThemeData dark() {
        ThemeData t = new ThemeData();
        t.background = hex(0x1e1e1effL);
        t.backgroundLine = hex(0xa0a0a0ffL);
        t.longbowArrow = hex(0xc0c0c0ffL);
        t.longbowEffect = hex(0xf24040ffL);
        t.longbowLine = hex(0xf24040ffL);
        t.longbowStroke = hex(0xfb6104d0L);
        t.teleportStroke = hex(0x005984ffL);
        t.teleportEffect = hex(0x0084C4ffL);
        t.particleDefault = hex(0xd6d6d6ffL);
        t.playerDamaged = hex(0xf24040ffL);
        t.player_a = hex(0x1e1e1effL);
        t.player_b = hex(0xd6d6d6ffL);
        t.ring = hex(0xd6d6d6ffL);
        t.shortbowArrow = hex(0xc0c0c0ffL);
        t.squareParticles = hex(0xd6d6d6ffL);
        t.stroke = hex(0xd6d6d6ffL);
        t.text = hex(0xd6d6d6ffL);
        return t;
    }

    private static Color gray(int v) {
        return new Color(v / 255f, v / 255f, v / 255f, 1f);
    }

    private static Color rgb(int r, int g, int b) {
        return new Color(r / 255f, g / 255f, b / 255f, 1f);
    }

    private static Color hex(long rgba) {
        return new Color((int) (rgba & 0xffffffffL));
    }
}
