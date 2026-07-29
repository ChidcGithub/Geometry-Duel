package com.geometryduel.neat

import java.util.Random

/**
 * 新颖性存档 (C)：记录历史上的行为签名，对新个体计算新颖性分数。
 * 新颖性 = 到 K 个最近邻居的平均距离。存档过大的行为会被剔除。
 */
class NoveltyArchive(private val rng: Random) {

    companion object {
        const val K_NEAREST = 15
        const val MAX_ARCHIVE = 500
        const val ADD_PROB = 0.04f

        /** 同代新颖性：与同代其他个体行为签名的平均距离。 */
        fun peerNovelty(sig: BehaviorSignature, peers: Array<BehaviorSignature?>, count: Int): Float {
            var sum = 0f
            var n = 0
            for (i in 0 until count) {
                val p = peers[i]
                if (p == null || p === sig) continue
                sum += sig.distance(p)
                n++
            }
            return if (n > 0) sum / n else 0f
        }
    }

    private val archive = ArrayList<BehaviorSignature>()

    /** 计算行为签名的新颖性分数 */
    fun novelty(sig: BehaviorSignature): Float {
        if (archive.isEmpty()) return 1f

        // 找 K 个最近邻居
        val k = minOf(K_NEAREST, archive.size)
        val dists = FloatArray(archive.size)
        for (i in archive.indices)
            dists[i] = sig.distance(archive[i])

        // 部分排序取前 K 个最小距离
        val nearest = FloatArray(k) { Float.MAX_VALUE }
        for (d in dists) {
            for (i in 0 until k) {
                if (d < nearest[i]) {
                    for (j in k - 1 downTo i + 1) nearest[j] = nearest[j - 1]
                    nearest[i] = d
                    break
                }
            }
        }

        var sum = 0f
        for (d in nearest) sum += d
        return sum / k
    }

    /** 尝试将签名加入存档 */
    fun tryAdd(sig: BehaviorSignature) {
        if (rng.nextFloat() < ADD_PROB) {
            archive.add(sig)
            while (archive.size > MAX_ARCHIVE)
                archive.removeAt(rng.nextInt(archive.size))
        }
    }

    fun size() = archive.size

    fun clear() = archive.clear()
}
