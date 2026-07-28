package com.geometryduel.neat;

import java.util.ArrayList;

/**
 * 新颖性存档 (C)：记录历史上的行为签名，对新个体计算新颖性分数。
 * 新颖性 = 到 K 个最近邻居的平均距离。存档过大的行为会被剔除。
 */
public class NoveltyArchive {
    public static final int K_NEAREST = 15;
    public static final int MAX_ARCHIVE = 500;
    public static final float ADD_PROB = 0.04f;

    private final ArrayList<BehaviorSignature> archive = new ArrayList<BehaviorSignature>();
    private final java.util.Random rng;

    public NoveltyArchive(java.util.Random rng) {
        this.rng = rng;
    }

    /** 计算行为签名的新颖性分数 */
    public float novelty(BehaviorSignature sig) {
        if (archive.isEmpty()) return 1f;

        // 找 K 个最近邻居
        int k = Math.min(K_NEAREST, archive.size());
        float[] dists = new float[archive.size()];
        for (int i = 0; i < archive.size(); i++)
            dists[i] = sig.distance(archive.get(i));

        // 部分排序取前 K 个最小距离
        float[] nearest = new float[k];
        for (int i = 0; i < k; i++) nearest[i] = Float.MAX_VALUE;
        for (float d : dists) {
            for (int i = 0; i < k; i++) {
                if (d < nearest[i]) {
                    for (int j = k - 1; j > i; j--) nearest[j] = nearest[j - 1];
                    nearest[i] = d;
                    break;
                }
            }
        }

        float sum = 0f;
        for (float d : nearest) sum += d;
        return sum / k;
    }

    /** 同代新颖性：与同代其他个体行为签名的平均距离。 */
    public static float peerNovelty(BehaviorSignature sig, BehaviorSignature[] peers, int count) {
        float sum = 0f;
        int n = 0;
        for (int i = 0; i < count; i++) {
            BehaviorSignature p = peers[i];
            if (p == null || p == sig) continue;
            sum += sig.distance(p);
            n++;
        }
        return n > 0 ? sum / n : 0f;
    }

    /** 尝试将签名加入存档 */
    public void tryAdd(BehaviorSignature sig) {
        if (rng.nextFloat() < ADD_PROB) {
            archive.add(sig);
            while (archive.size() > MAX_ARCHIVE)
                archive.remove(rng.nextInt(archive.size()));
        }
    }

    public int size() { return archive.size(); }

    public void clear() { archive.clear(); }
}
