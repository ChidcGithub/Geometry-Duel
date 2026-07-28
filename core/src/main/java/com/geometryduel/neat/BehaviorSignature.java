package com.geometryduel.neat;

/**
 * 从 MatchStats 提取的行为特征向量，用于新颖性搜索 (C)。
 * 维度 ~14，归一化到 [0,1]。
 */
public class BehaviorSignature {
    public static final int DIM = 12;
    public final float[] vec = new float[DIM];

    /** 从对战统计构建行为签名 */
    public static BehaviorSignature from(MatchStats m, Genome g) {
        BehaviorSignature s = new BehaviorSignature();
        int play = Math.max(1, m.frames - MatchStats.COUNTDOWN_FRAMES);
        float pf = play;

        s.vec[0]  = clamp(m.hitsDealt / 10f);                // 命中数
        s.vec[1]  = clamp(m.hitsTaken / 10f);                // 受击数
        s.vec[2]  = clamp(m.shotsFired / 60f);               // 短弓频率
        s.vec[3]  = clamp(m.longShotsFired / 15f);           // 长弓频率
        s.vec[4]  = clamp(m.teleportsUsed / 10f);            // 传送频率
        s.vec[5]  = clamp(m.campFrames / pf);                // 墙壁时间比
        s.vec[6]  = clamp(m.centerFrames / pf);              // 中心时间比
        s.vec[7]  = clamp(m.activeMoveFrames / pf);          // 主动移动比
        s.vec[8]  = clamp(m.teleportKills / 5f);             // 传送击杀
        s.vec[9]  = clamp(m.longShotsHit / 5f);              // 长弓命中
        s.vec[10] = g != null ? clamp(g.nodes.size() / 60f) : 0f;  // 网络规模
        s.vec[11] = g != null ? clamp(g.conns.size() / 400f) : 0f; // 连接数
        return s;
    }

    /** 与另一个签名的欧氏距离 */
    public float distance(BehaviorSignature o) {
        float sum = 0f;
        for (int i = 0; i < DIM; i++) {
            float d = vec[i] - o.vec[i];
            sum += d * d;
        }
        return (float) Math.sqrt(sum);
    }

    private static float clamp(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
}
