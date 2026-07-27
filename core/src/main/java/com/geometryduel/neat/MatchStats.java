package com.geometryduel.neat;

/** 一局对战的统计数据（从被评估 AI 的视角），供适应度计算与实战上报。 */
public class MatchStats {
    public static final int COUNTDOWN_FRAMES = 180;        // 开场倒计时（不计入对战时间）
    public static final int QUICK_WIN_FRAMES = 3600;       // 60 秒内结束不受罚
    public static final float OVERTIME_PENALTY_PER_SEC = 2f; // 超时每秒扣分

    public boolean aiWon;
    public int frames;          // 存活帧数（对局总帧数，含倒计时）
    public int hitsDealt;       // 对敌方造成的受击次数
    public int hitsTaken;       // 自身受击次数
    public int shotsFired;      // 射出的短箭数
    public int longShotsFired;  // 射出的长箭数（大招）
    public int teleportsUsed;   // 传送（闪避）使用次数

    /**
     * 适应度：胜利大分 + 存活时间 + 命中奖励 - 受击惩罚 + 技能使用行为奖励。
     * 行为奖励（reward shaping）带封顶、权重远小于胜负/命中：
     * 引导网络先学会「会用技能」，再由胜负大分主导进化，避免学会刷招。
     *
     * @param shaping 课程式系数：前期 >1 强行引导技能使用，随世代衰减到 0.2 保底
     */
    public float fitness(float shaping) {
        float f = aiWon ? 100f : 0f;
        f += Math.min(frames, 7200) / 7200f * 20f;
        f += hitsDealt * 15f;
        f -= hitsTaken * 5f;
        f += Math.min(shotsFired, 40) * 0.5f * shaping;
        f += Math.min(longShotsFired, 10) * 5f * shaping;
        f += Math.min(teleportsUsed, 8) * 3f * shaping;
        // 超时惩罚：对战时间超过 60 秒仍未分出胜负，分数开始线性降低（-2 分/秒），
        // 2 分钟打满时 -120 分已超过胜利分——逼迫 AI 主动进攻尽快击杀，而非拖时间
        int playFrames = frames - COUNTDOWN_FRAMES;
        if (playFrames > QUICK_WIN_FRAMES) {
            f -= (playFrames - QUICK_WIN_FRAMES) / 60f * OVERTIME_PENALTY_PER_SEC;
        }
        return f;
    }

    /** 无课程缩放（shaping=1）。 */
    public float fitness() {
        return fitness(1f);
    }
}
