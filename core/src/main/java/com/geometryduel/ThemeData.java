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

    /** 亮色主题（M3 Expressive Light，默认紫种子色）。 */
    public static ThemeData light() {
        return light(0);
    }

    /**
     * 亮色主题（Material You 动态取色）：以种子色为基调，按 M3 色调角色近似派生。
     * seed 为 ARGB；传 0 使用默认 M3 紫。
     */
    public static ThemeData light(int seed) {
        if (seed == 0) seed = 0xFF6750A4;
        float[] base = rgbToHsv(seed);
        float h = base[0], sat = base[1];
        ThemeData t = new ThemeData();
        // M3 色板（primary≈tone40，container≈tone90，onContainer≈tone10）
        t.primary = hsv(h, clamp(sat, 0.35f, 0.85f), 0.62f);
        t.onPrimary = hex(0xFFFFFFffL);
        t.primaryContainer = hsv(h, clamp(sat * 0.55f, 0.10f, 0.45f), 0.94f);
        t.onPrimaryContainer = hsv(h, clamp(sat + 0.15f, 0.30f, 1f), 0.28f);
        t.surfaceVariant = hsv(h, clamp(sat * 0.30f, 0.04f, 0.25f), 0.90f);
        t.onSurfaceVariant = hsv(h, clamp(sat * 0.35f, 0.05f, 0.30f), 0.32f);
        t.error = hex(0xB3261EffL);
        // 基础（surface/onSurface 带轻微种子色相）
        t.background = hsv(h, clamp(sat * 0.20f, 0.02f, 0.12f), 0.99f);
        t.backgroundLine = t.surfaceVariant;
        t.text = hsv(h, clamp(sat * 0.30f, 0.03f, 0.25f), 0.12f);
        t.stroke = t.text;
        // 游戏元素
        t.player_a = t.primary;                   // 我方：主色块（Metro）
        t.player_b = hex(0x1D1B20ffL);            // 敌方：深色块
        t.shortbowArrow = t.onSurfaceVariant;
        t.longbowArrow = t.text;
        t.longbowLine = t.error;
        t.longbowEffect = t.error;
        t.longbowStroke = rgba(0x1D1B20L, 0.5f);
        t.teleportStroke = hex(0x004C83ffL);
        t.teleportEffect = hex(0x0061A4ffL);      // M3 expressive blue
        t.playerDamaged = t.error;
        t.ring = t.primary;
        t.particleDefault = t.text;
        t.squareParticles = t.text;
        return t;
    }

    /** 暗色主题（M3 Expressive Dark，默认紫种子色）。 */
    public static ThemeData dark() {
        return dark(0);
    }

    /** 暗色主题（Material You 动态取色）。seed 为 ARGB；传 0 使用默认 M3 紫。 */
    public static ThemeData dark(int seed) {
        if (seed == 0) seed = 0xFF6750A4;
        float[] base = rgbToHsv(seed);
        float h = base[0], sat = base[1];
        ThemeData t = new ThemeData();
        // M3 色板（dark：primary≈tone80，container≈tone30，onContainer≈tone90）
        t.primary = hsv(h, clamp(sat * 0.6f, 0.20f, 0.65f), 0.92f);
        t.onPrimary = hsv(h, clamp(sat + 0.15f, 0.30f, 1f), 0.25f);
        t.primaryContainer = hsv(h, clamp(sat * 0.75f, 0.25f, 0.80f), 0.38f);
        t.onPrimaryContainer = hsv(h, clamp(sat * 0.35f, 0.08f, 0.40f), 0.95f);
        t.surfaceVariant = hsv(h, clamp(sat * 0.25f, 0.04f, 0.25f), 0.30f);
        t.onSurfaceVariant = hsv(h, clamp(sat * 0.25f, 0.05f, 0.30f), 0.82f);
        t.error = hex(0xF2B8B5ffL);
        // 基础
        t.background = hsv(h, clamp(sat * 0.25f, 0.03f, 0.20f), 0.09f);
        t.backgroundLine = t.surfaceVariant;
        t.text = hsv(h, clamp(sat * 0.20f, 0.03f, 0.20f), 0.93f);
        t.stroke = t.text;
        // 游戏元素
        t.player_a = t.primary;                   // 我方：浅色调主色块
        t.player_b = hex(0xE6E0E9ffL);            // 敌方：浅色块
        t.shortbowArrow = t.onSurfaceVariant;
        t.longbowArrow = t.text;
        t.longbowLine = t.error;
        t.longbowEffect = t.error;
        t.longbowStroke = rgba(0xE6E0E9L, 0.5f);
        t.teleportStroke = hex(0x50606FffL);
        t.teleportEffect = hex(0x9FCAFFffL);      // M3 dark blue
        t.playerDamaged = t.error;
        t.ring = t.primary;
        t.particleDefault = t.text;
        t.squareParticles = t.text;
        return t;
    }

    private static Color hex(long rgba) {
        return new Color((int) (rgba & 0xffffffffL));
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** ARGB → HSV（h: 0-360，s/v: 0-1）。 */
    private static float[] rgbToHsv(int argb) {
        float r = ((argb >> 16) & 0xff) / 255f;
        float g = ((argb >> 8) & 0xff) / 255f;
        float b = (argb & 0xff) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d < 0.0001f) h = 0f;
        else if (max == r) h = (60f * ((g - b) / d) + 360f) % 360f;
        else if (max == g) h = 60f * ((b - r) / d) + 120f;
        else h = 60f * ((r - g) / d) + 240f;
        float s = max < 0.0001f ? 0f : d / max;
        return new float[] {h, s, max};
    }

    /** HSV → Color（h: 0-360，s/v 自动 clamp 到 0-1）。 */
    private static Color hsv(float h, float s, float v) {
        h = ((h % 360f) + 360f) % 360f;
        s = clamp(s, 0f, 1f);
        v = clamp(v, 0f, 1f);
        float c = v * s;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = v - c;
        float r, g, b;
        if (h < 60f) { r = c; g = x; b = 0f; }
        else if (h < 120f) { r = x; g = c; b = 0f; }
        else if (h < 180f) { r = 0f; g = c; b = x; }
        else if (h < 240f) { r = 0f; g = x; b = c; }
        else if (h < 300f) { r = x; g = 0f; b = c; }
        else { r = c; g = 0f; b = x; }
        return new Color(r + m, g + m, b + m, 1f);
    }

    private static Color rgba(long rgb, float a) {
        return new Color(((rgb >> 16) & 0xff) / 255f, ((rgb >> 8) & 0xff) / 255f, (rgb & 0xff) / 255f, a);
    }
}
