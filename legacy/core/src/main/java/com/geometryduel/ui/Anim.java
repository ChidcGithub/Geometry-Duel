package com.geometryduel.ui;

/**
 * Material 3 Expressive 动画曲线。
 * 所有函数输入 t∈[0,1]，输出多数 ∈[0,1]（弹簧会过冲 >1）。
 */
public class Anim {

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Expressive 弹簧：明显回弹（入场、按压释放）。 */
    public static float spring(float t) {
        t = clamp01(t);
        return 1f - (float) (Math.cos(t * 3.5 * Math.PI) * Math.exp(-6.5 * t));
    }

    /** 轻弹簧：小回弹（开关、滑块等小组件）。 */
    public static float softSpring(float t) {
        t = clamp01(t);
        return 1f - (float) (Math.cos(t * 2.5 * Math.PI) * Math.exp(-7.5 * t));
    }

    /** EmphasizedDecelerate：快启动慢停止（入场位移、淡入）。 */
    public static float decelerate(float t) {
        t = clamp01(t);
        float u = 1f - t;
        return 1f - u * u * u;
    }

    /** EmphasizedAccelerate：慢启动快消失（退场）。 */
    public static float accelerate(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    /** Standard 对称加减速（状态切换）。 */
    public static float standard(float t) {
        t = clamp01(t);
        return t < 0.5f ? 4f * t * t * t
                : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    /** 分元素 stagger：第 index 个元素延迟 index*delay 秒后的局部进度。 */
    public static float stagger(float clock, int index, float delay, float duration) {
        return clamp01((clock - index * delay) / duration);
    }
}
