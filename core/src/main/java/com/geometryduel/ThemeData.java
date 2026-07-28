package com.geometryduel;

import com.badlogic.gdx.graphics.Color;

/**
 * 主题配色：Metro UI 风格（大色块、方角、鲜明纯色）+ Material 3 Expressive 色板。
 * 游戏内元素（玩家/箭/特效）映射到 M3 色调角色，UI 组件使用 primary/container 色块。
 */
public class ThemeData {
    // ---- 基础（沿用字段名，游戏渲染引用）----
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

    // ---- M3 色板（UI 组件引用）----
    public Color primary;
    public Color onPrimary;
    public Color primaryContainer;
    public Color onPrimaryContainer;
    public Color surfaceVariant;
    public Color onSurfaceVariant;
    public Color error;

    public enum Type {Light, Dark}

    /** 亮色主题（M3 Expressive Light）。 */
    public static ThemeData light() {
        ThemeData t = new ThemeData();
        // M3 色板
        t.primary = hex(0x6750A4ffL);
        t.onPrimary = hex(0xFFFFFFffL);
        t.primaryContainer = hex(0xEADDFFffL);
        t.onPrimaryContainer = hex(0x21005DffL);
        t.surfaceVariant = hex(0xE7E0ECffL);
        t.onSurfaceVariant = hex(0x49454FffL);
        t.error = hex(0xB3261EffL);
        // 基础
        t.background = hex(0xFEF7FFffL);          // surface
        t.backgroundLine = hex(0xE7E0ECffL);      // surfaceVariant
        t.text = hex(0x1D1B20ffL);                // onSurface
        t.stroke = hex(0x1D1B20ffL);
        // 游戏元素
        t.player_a = t.primary;                   // 我方：主紫色块（Metro）
        t.player_b = hex(0x1D1B20ffL);            // 敌方：深色块
        t.shortbowArrow = hex(0x49454FffL);
        t.longbowArrow = hex(0x1D1B20ffL);
        t.longbowLine = t.error;
        t.longbowEffect = t.error;
        t.longbowStroke = rgba(0x1D1B20L, 0.5f);
        t.teleportStroke = hex(0x004C83ffL);
        t.teleportEffect = hex(0x0061A4ffL);      // M3 expressive blue
        t.playerDamaged = t.error;
        t.ring = t.primary;
        t.particleDefault = hex(0x1D1B20ffL);
        t.squareParticles = hex(0x1D1B20ffL);
        return t;
    }

    /** 暗色主题（M3 Expressive Dark）。 */
    public static ThemeData dark() {
        ThemeData t = new ThemeData();
        // M3 色板
        t.primary = hex(0xD0BCFFffL);
        t.onPrimary = hex(0x381E72ffL);
        t.primaryContainer = hex(0x4F378BffL);
        t.onPrimaryContainer = hex(0xEADDFFffL);
        t.surfaceVariant = hex(0x49454FffL);
        t.onSurfaceVariant = hex(0xCAC4D0ffL);
        t.error = hex(0xF2B8B5ffL);
        // 基础
        t.background = hex(0x141218ffL);          // surface
        t.backgroundLine = hex(0x49454FffL);
        t.text = hex(0xE6E0E9ffL);                // onSurface
        t.stroke = hex(0xE6E0E9ffL);
        // 游戏元素
        t.player_a = t.primary;                   // 我方：浅紫色块
        t.player_b = hex(0xE6E0E9ffL);            // 敌方：浅色块
        t.shortbowArrow = hex(0xCAC4D0ffL);
        t.longbowArrow = hex(0xE6E0E9ffL);
        t.longbowLine = t.error;
        t.longbowEffect = t.error;
        t.longbowStroke = rgba(0xE6E0E9L, 0.5f);
        t.teleportStroke = hex(0x50606FffL);
        t.teleportEffect = hex(0x9FCAFFffL);      // M3 dark blue
        t.playerDamaged = t.error;
        t.ring = t.primary;
        t.particleDefault = hex(0xE6E0E9ffL);
        t.squareParticles = hex(0xE6E0E9ffL);
        return t;
    }

    private static Color hex(long rgba) {
        return new Color((int) (rgba & 0xffffffffL));
    }

    private static Color rgba(long rgb, float a) {
        return new Color(((rgb >> 16) & 0xff) / 255f, ((rgb >> 8) & 0xff) / 255f, (rgb & 0xff) / 255f, a);
    }
}
