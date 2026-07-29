package com.geometryduel.neat

import kotlin.math.sqrt

/**
 * 从 MatchStats 提取的行为特征向量，用于新颖性搜索 (C)。
 * 维度 12，归一化到 [0,1]。
 */
class BehaviorSignature {

    companion object {
        const val DIM = 12

        fun from(m: MatchStats, g: Genome?): BehaviorSignature {
            val s = BehaviorSignature()
            s.fill(m, g)
            return s
        }
    }

    val vec = FloatArray(DIM)

    /** 复用对象填充 */
    fun fill(m: MatchStats, g: Genome?) {
        val play = maxOf(1, m.frames - MatchStats.COUNTDOWN_FRAMES)
        val pf = play.toFloat()
        vec[0] = clamp(m.hitsDealt / 10f)
        vec[1] = clamp(m.hitsTaken / 10f)
        vec[2] = clamp(m.shotsFired / 60f)
        vec[3] = clamp(m.longShotsFired / 15f)
        vec[4] = clamp(m.teleportsUsed / 10f)
        vec[5] = clamp(m.campFrames / pf)
        vec[6] = clamp(m.centerFrames / pf)
        vec[7] = clamp(m.activeMoveFrames / pf)
        vec[8] = clamp(m.teleportKills / 5f)
        vec[9] = clamp(m.longShotsHit / 5f)
        vec[10] = if (g != null) clamp(g.nodes.size / 60f) else 0f
        vec[11] = if (g != null) clamp(g.conns.size / 400f) else 0f
    }

    /** 与另一个签名的欧氏距离 */
    fun distance(o: BehaviorSignature): Float {
        var sum = 0f
        for (i in 0 until DIM) {
            val d = vec[i] - o.vec[i]
            sum += d * d
        }
        return sqrt(sum)
    }

    /**
     * 行为策略档案：关键行为维度离散化为 5 位三档编码（共 243 种），
     * 供训练器统计稀有度。基于真实对局行为，而非适应度数值。
     */
    fun profile(): String {
        val sb = StringBuilder(5)
        sb.append(bin(vec[0]))  // 攻击性（命中）
        sb.append(bin(vec[3]))  // 长弓倾向
        sb.append(bin(vec[4]))  // 传送机动
        sb.append(bin(vec[5]))  // 蹲坑程度
        sb.append(bin(vec[7]))  // 主动移动
        return sb.toString()
    }

    private fun bin(v: Float): Char = if (v < 0.25f) '0' else if (v < 0.6f) '1' else '2'

    private fun clamp(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v
}
