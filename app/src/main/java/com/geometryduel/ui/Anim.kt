package com.geometryduel.ui

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow

/**
 * Material 3 Expressive 动画曲线。
 * 所有函数输入 t∈[0,1]，输出多数 ∈[0,1]（弹簧会过冲 >1）。
 */
object Anim {

    fun clamp01(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v

    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** Expressive 弹簧：明显回弹（入场、按压释放）。 */
    fun spring(t0: Float): Float {
        val t = clamp01(t0)
        return 1f - (cos(t * 3.5 * Math.PI) * exp(-6.5 * t)).toFloat()
    }

    /** 轻弹簧：小回弹（开关、滑块等小组件）。 */
    fun softSpring(t0: Float): Float {
        val t = clamp01(t0)
        return 1f - (cos(t * 2.5 * Math.PI) * exp(-7.5 * t)).toFloat()
    }

    /** EmphasizedDecelerate：快启动慢停止（入场位移、淡入）。 */
    fun decelerate(t0: Float): Float {
        val t = clamp01(t0)
        val u = 1f - t
        return 1f - u * u * u
    }

    /** EmphasizedAccelerate：慢启动快消失（退场）。 */
    fun accelerate(t0: Float): Float {
        val t = clamp01(t0)
        return t * t * t
    }

    /** Standard 对称加减速（状态切换）。 */
    fun standard(t0: Float): Float {
        val t = clamp01(t0)
        return if (t < 0.5f) 4f * t * t * t
        else 1f - (-2f * t + 2f).pow(3) / 2f
    }

    /** 分元素 stagger：第 index 个元素延迟 index*delay 秒后的局部进度。 */
    fun stagger(clock: Float, index: Int, delay: Float, duration: Float): Float =
        clamp01((clock - index * delay) / duration)
}
