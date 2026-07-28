package com.geometryduel.neat;

/**
 * 从 MatchStats 提取的行为特征向量，用于新颖性搜索 (C)。
 * 维度 ~14，归一化到 [0,1]。
 */
public class BehaviorSignature {
    public static final int DIM = 12;
    public final float[] vec = new float[DIM];

    /** 复用对象填充 */
    public void fill(MatchStats m, Genome g) {
        int play = Math.max(1, m.frames - MatchStats.COUNTDOWN_FRAMES);
        float pf = play;
        vec[0]  = clamp(m.hitsDealt / 10f);
        vec[1]  = clamp(m.hitsTaken / 10f);
        vec[2]  = clamp(m.shotsFired / 60f);
        vec[3]  = clamp(m.longShotsFired / 15f);
        vec[4]  = clamp(m.teleportsUsed / 10f);
        vec[5]  = clamp(m.campFrames / pf);
        vec[6]  = clamp(m.centerFrames / pf);
        vec[7]  = clamp(m.activeMoveFrames / pf);
        vec[8]  = clamp(m.teleportKills / 5f);
        vec[9]  = clamp(m.longShotsHit / 5f);
        vec[10] = g != null ? clamp(g.nodes.size() / 60f) : 0f;
        vec[11] = g != null ? clamp(g.conns.size() / 400f) : 0f;
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

    public static BehaviorSignature from(MatchStats m, Genome g) {
        BehaviorSignature s = new BehaviorSignature();
        s.fill(m, g);
        return s;
    }
}
