package com.geometryduel.neat;

/**
 * 一局对战的统计数据（从被评估 AI 的视角），供适应度计算与实战上报。
 *
 * 适应度设计原则：
 *   1. 胜利永远最大：胜利分(100) > 全部行为奖励之和
 *   2. 只看效果：奖励「命中」而非「射箭次数」，避免刷招
 *   3. 技能组合 > 单一技能：传送+长弓组合重奖
 *   4. 精度 > 次数：长弓命中 > 长弓射出
 */
public class MatchStats {
    public static final int COUNTDOWN_FRAMES = 180;
    public static final int QUICK_WIN_FRAMES = 3600;
    public static final float BEHAVIOR_CAP = 50f;

    // ---- 基础 ----
    public boolean aiWon;
    public int frames;
    public int hitsDealt;
    public int hitsTaken;

    // ---- 行为次数 ----
    public int shotsFired;
    public int longShotsFired;
    public int teleportsUsed;
    public int teleportKills;
    public int aimedFrames;
    public int campFrames;

    // ---- 技能精度 (1.3) ----
    public int longShotsHit;       // 长弓命中次数
    public int teleportsDodged;    // 传送成功躲避次数
    public int perfectAimFrames;   // 完美瞄准帧数（误差<3deg）

    // ---- 技能组合 (1.1) ----
    public int teleportLongbowCombos;   // 传送后长弓次数
    public int shortLongbowAlternate;   // 短弓+长弓交替次数
    public int teleportHitDefense;      // 传送成功躲避并反击

    // ---- 主动移动 (1.4) ----
    public int activeMoveFrames;        // 主动移动帧数（速度>阈值）
    public int chaseFrames;             // 追击帧数（向敌人方向移动）
    public int centerFrames;            // 在中心区域帧数（奖励占据有利位置）
    public float positionDiversity;     // 位置多样性（基于访问不同区域的数量）

    // ---- 混合回放 (1.5) ----
    public int keyMomentFrames;
    public int enemyLongbowAimFrames;   // 敌人蓄长弓瞄准自己的帧数
    public int longbowChargeFrames;     // 自身蓄长弓帧数

    public float fitness(float shaping) {
        int playFrames = Math.max(1, frames - COUNTDOWN_FRAMES);
        float wallRatio = campFrames / (float) playFrames;
        if (wallRatio > 1f) wallRatio = 1f;

        // —— 效果项 ——
        float f = aiWon ? 100f : 0f;
        f += Math.min(frames, 7200) / 7200f * 40f * (1f - wallRatio * 0.8f);
        f += hitsDealt * 15f;
        f -= hitsTaken * 10f;  // 受击翻倍（-5→-10，角落更容易被瞄准）
        f -= wallRatio * 250f;  // 靠墙重罚（绝对值>>胜利分，彻底抑制蹲坑）
        f -= enemyLongbowAimFrames / (float) playFrames * 30f;

        // —— 精度奖励 (1.3) ——
        f += longShotsHit * 30f * shaping;  // 长弓命中重奖（20→30）
        f += teleportsDodged * 10f * shaping;
        f += Math.min(perfectAimFrames, 300) * 0.08f * shaping;

        // —— 大招尝试奖励：即使没中也鼓励蓄力和射出 ——
        f += longbowChargeFrames / (float) playFrames * 20f * shaping;  // 蓄力时间比奖励
        f += Math.min(longShotsFired, 10) * 8f * shaping;  // 发射长弓即奖（不必命中）

        // —— 技能组合奖励 (1.1) ——
        float combo = 0f;
        if (longShotsFired > 0 && teleportsUsed > 0) combo += 15f * shaping;
        if (shotsFired > 5 && longShotsFired > 0) combo += 10f * shaping;
        combo += teleportKills * 25f;
        combo += teleportLongbowCombos * 15f;
        combo += shortLongbowAlternate * 3f;
        combo += teleportHitDefense * 8f;
        f += combo;

        // —— 主动移动奖励 (1.4) ——
        float moveBonus = 0f;
        float activeRatio = activeMoveFrames / (float) playFrames;
        moveBonus += activeRatio * 15f;  // 主动移动奖励
        moveBonus += chaseFrames / (float) playFrames * 10f;  // 追击奖励
        moveBonus += centerFrames / (float) playFrames * 8f;  // 占据中心奖励
        moveBonus += positionDiversity * 5f;  // 位置多样性奖励
        f += moveBonus;

        // —— 关键时刻：处于关键时刻=积极战斗，奖励活跃度 (1.5) ——
        float keyMomentRatio = keyMomentFrames / (float) playFrames;
        f += keyMomentRatio * 15f;  // 越活跃（传送/蓄力/击杀附近），越奖励

        // —— 行为项（cap 50） ——
        float behavior = 0f;
        behavior += Math.min(longShotsFired, 5) * 3f * shaping;
        behavior += Math.min(teleportsUsed, 3) * 1f * shaping;
        behavior += Math.min(aimedFrames, 300) * 0.03f * shaping;
        if (behavior > BEHAVIOR_CAP) behavior = BEHAVIOR_CAP;
        f += behavior;

        // —— 超时惩罚（二次方加速，靠墙者刑罚加倍） ——
        if (playFrames > QUICK_WIN_FRAMES) {
            float overtimeSec = (playFrames - QUICK_WIN_FRAMES) / 60f;
            // 平方增长: 10s→150, 20s→600, 30s→1350
            f -= overtimeSec * overtimeSec * 1.5f * (1f + wallRatio);
            // 超 20 秒未胜 → 归零，强推击杀欲望
            if (!aiWon && overtimeSec > 20f) f = Math.min(f, -10f);
        }
        return f;
    }

    public float fitness() {
        return fitness(1f);
    }
}
