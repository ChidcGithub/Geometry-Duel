package com.geometryduel.theme

import androidx.compose.ui.graphics.Color

/**
 * 游戏配色：Metro UI 风格（大色块、方角、鲜明纯色）+ Material 3 Expressive 色板。
 * 游戏内元素（玩家/箭/特效）映射到 M3 色调角色。
 * 由原 libGDX ThemeData 逐项移植，HSV 派生数学保持一致。
 */
class GamePalette(
    // ---- 基础（游戏渲染引用）----
    val background: Color,
    val backgroundLine: Color,
    val longbowArrow: Color,
    val longbowEffect: Color,
    val longbowLine: Color,
    val longbowStroke: Color,
    val particleDefault: Color,
    val playerDamaged: Color,
    val playerA: Color,
    val playerB: Color,
    val ring: Color,
    val shortbowArrow: Color,
    val squareParticles: Color,
    val stroke: Color,
    val teleportEffect: Color,
    val teleportStroke: Color,
    val text: Color,
    // ---- M3 色板 ----
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val error: Color,
) {
    enum class Type { Light, Dark }

    companion object {
        /** 亮色主题（Material You 动态取色）：以种子色为基调派生。seed 为 ARGB；传 0 使用默认 M3 紫。 */
        fun light(seed: Int = 0): GamePalette {
            val s = if (seed == 0) 0xFF6750A4.toInt() else seed
            val base = rgbToHsv(s)
            val h = base[0]
            val sat = base[1]
            val primary = hsv(h, clamp(sat, 0.35f, 0.85f), 0.62f)
            val primaryContainer = hsv(h, clamp(sat * 0.55f, 0.10f, 0.45f), 0.94f)
            val onPrimaryContainer = hsv(h, clamp(sat + 0.15f, 0.30f, 1f), 0.28f)
            val surfaceVariant = hsv(h, clamp(sat * 0.30f, 0.04f, 0.25f), 0.90f)
            val onSurfaceVariant = hsv(h, clamp(sat * 0.35f, 0.05f, 0.30f), 0.32f)
            val error = Color(0xFFB3261E)
            val text = hsv(h, clamp(sat * 0.30f, 0.03f, 0.25f), 0.12f)
            return GamePalette(
                primary = primary,
                onPrimary = Color.White,
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = onSurfaceVariant,
                error = error,
                background = hsv(h, clamp(sat * 0.20f, 0.02f, 0.12f), 0.99f),
                backgroundLine = surfaceVariant,
                text = text,
                stroke = text,
                playerA = primary,
                playerB = Color(0xFF1D1B20),
                shortbowArrow = onSurfaceVariant,
                longbowArrow = text,
                longbowLine = error,
                longbowEffect = error,
                longbowStroke = Color(0x801D1B20),
                teleportStroke = Color(0xFF004C83),
                teleportEffect = Color(0xFF0061A4),
                playerDamaged = error,
                ring = primary,
                particleDefault = text,
                squareParticles = text,
            )
        }

        /** 暗色主题（Material You 动态取色）。seed 为 ARGB；传 0 使用默认 M3 紫。 */
        fun dark(seed: Int = 0): GamePalette {
            val s = if (seed == 0) 0xFF6750A4.toInt() else seed
            val base = rgbToHsv(s)
            val h = base[0]
            val sat = base[1]
            val primary = hsv(h, clamp(sat * 0.6f, 0.20f, 0.65f), 0.92f)
            val primaryContainer = hsv(h, clamp(sat * 0.75f, 0.25f, 0.80f), 0.38f)
            val onPrimaryContainer = hsv(h, clamp(sat * 0.35f, 0.08f, 0.40f), 0.95f)
            val surfaceVariant = hsv(h, clamp(sat * 0.25f, 0.04f, 0.25f), 0.30f)
            val onSurfaceVariant = hsv(h, clamp(sat * 0.25f, 0.05f, 0.30f), 0.82f)
            val error = Color(0xFFF2B8B5)
            val text = hsv(h, clamp(sat * 0.20f, 0.03f, 0.20f), 0.93f)
            return GamePalette(
                primary = primary,
                onPrimary = hsv(h, clamp(sat + 0.15f, 0.30f, 1f), 0.25f),
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = onSurfaceVariant,
                error = error,
                background = hsv(h, clamp(sat * 0.25f, 0.03f, 0.20f), 0.09f),
                backgroundLine = surfaceVariant,
                text = text,
                stroke = text,
                playerA = primary,
                playerB = Color(0xFFE6E0E9),
                shortbowArrow = onSurfaceVariant,
                longbowArrow = text,
                longbowLine = error,
                longbowEffect = error,
                longbowStroke = Color(0x80E6E0E9),
                teleportStroke = Color(0xFF50606F),
                teleportEffect = Color(0xFF9FCAFF),
                playerDamaged = error,
                ring = primary,
                particleDefault = text,
                squareParticles = text,
            )
        }

        private fun clamp(v: Float, lo: Float, hi: Float) = if (v < lo) lo else if (v > hi) hi else v

        /** ARGB → HSV（h: 0-360，s/v: 0-1）。 */
        private fun rgbToHsv(argb: Int): FloatArray {
            val r = (argb shr 16 and 0xff) / 255f
            val g = (argb shr 8 and 0xff) / 255f
            val b = (argb and 0xff) / 255f
            val max = maxOf(r, maxOf(g, b))
            val min = minOf(r, minOf(g, b))
            val d = max - min
            val h = when {
                d < 0.0001f -> 0f
                max == r -> (60f * ((g - b) / d) + 360f) % 360f
                max == g -> 60f * ((b - r) / d) + 120f
                else -> 60f * ((r - g) / d) + 240f
            }
            val s = if (max < 0.0001f) 0f else d / max
            return floatArrayOf(h, s, max)
        }

        /** HSV → Color（h: 0-360，s/v 自动 clamp 到 0-1）。 */
        private fun hsv(h: Float, s: Float, v: Float): Color {
            var hue = ((h % 360f) + 360f) % 360f
            val sat = clamp(s, 0f, 1f)
            val value = clamp(v, 0f, 1f)
            val c = value * sat
            val x = c * (1f - Math.abs((hue / 60f) % 2f - 1f))
            val m = value - c
            val (r, g, b) = when {
                hue < 60f -> Triple(c, x, 0f)
                hue < 120f -> Triple(x, c, 0f)
                hue < 180f -> Triple(0f, c, x)
                hue < 240f -> Triple(0f, x, c)
                hue < 300f -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            return Color(r + m, g + m, b + m, 1f)
        }
    }
}
