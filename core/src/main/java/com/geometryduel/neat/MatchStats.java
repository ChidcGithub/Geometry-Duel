package com.geometryduel.neat;

/**
 * 一局对战的统计数据（从被评估 AI 的视角），供适应度计算与实战上报。
 *
 * 适应度设计原则：
 *   1. 胜利永远最大：胜利分(100) > 全部行为奖励之和(≤30)
 *   2. 只看效果：奖励「命中」而非「射箭次数」，避免刷招
 *   3. 存活有价值：输了但活得久的个体 > 秒死的个体
 */
public class MatchStats {
    public static final int COUNTDOWN_FRAMES = 180;
    public static final int QUICK_WIN_FRAMES = 3600;
    public static final float OVERTIME_PENALTY_PER_SEC = 2f;

    /** 所有行为奖励（长弓/传送/瞄准）的总软上限：不超过胜利分的 30%。 */
    public static final float BEHAVIOR_CAP = 30f;

    public boolean aiWon;
    public int frames;
    public int hitsDealt;
    public int hitsTaken;
    public int shotsFired;
    public int longShotsFired;
    public int teleportsUsed;
    public int teleportKills;
    public int aimedFrames;
    public int campFrames;

    /**
     * 适应度 = 效果项（无上限） + 行为项（软上限 30） - 超时惩罚。
     *
     * @param shaping 课程式系数（前期高，引导技能探索；后期低，由胜负主导）
     */
    public float fitness(float shaping) {
        int playFrames = Math.max(1, frames - COUNTDOWN_FRAMES);
        float wallRatio = campFrames / (float) playFrames;
        if (wallRatio > 1f) wallRatio = 1f;

        // —— 效果项 ——
        float f = aiWon ? 100f : 0f;
        // 存活分：露营时缩水（wallRatio=0.8 → 存活分只值 20%）
        f += Math.min(frames, 7200) / 7200f * 40f * (1f - wallRatio * 0.8f);
        f += hitsDealt * 15f;
        f -= hitsTaken * 5f;
        // 露营惩罚：直接扣分
        f -= wallRatio * 40f;

        // —— 行为项 ——
        float behavior = 0f;
        behavior += Math.min(longShotsFired, 5) * 3f * shaping;
        behavior += Math.min(teleportsUsed, 3) * 1f * shaping;
        behavior += teleportKills * 20f;
        behavior += Math.min(aimedFrames, 300) * 0.03f * shaping;
        float nonComboCap = behavior - teleportKills * 20f;
        if (nonComboCap > BEHAVIOR_CAP) behavior = BEHAVIOR_CAP + teleportKills * 20f;
        f += behavior;

        // —— 超时惩罚 ——
        if (playFrames > QUICK_WIN_FRAMES) {
            f -= (playFrames - QUICK_WIN_FRAMES) / 60f * OVERTIME_PENALTY_PER_SEC;
        }
        return f;
    }

    public float fitness() {
        return fitness(1f);
    }
}
