package com.geometryduel.neat

/**
 * 一局对战的统计数据（从被评估 AI 的视角），供适应度计算与实战上报。
 *
 * 适应度设计原则：
 *   1. 胜利永远最大：胜利分(100) > 全部行为奖励之和
 *   2. 只看效果：奖励「命中」而非「射箭次数」，避免刷招
 *   3. 技能组合 > 单一技能：传送+长弓组合重奖
 *   4. 精度 > 次数：长弓命中 > 长弓射出
 */
class MatchStats {

    companion object {
        const val COUNTDOWN_FRAMES = 180
        const val QUICK_WIN_FRAMES = 3600
        const val BEHAVIOR_CAP = 30f
        /** 胜局基础分：任何败局的总分都必须低于它（胜利永远最大）。 */
        const val WIN_SCORE = 100f
    }

    // ---- 基础 ----
    var aiWon = false
    var frames = 0
    var hitsDealt = 0
    var hitsTaken = 0

    // ---- 行为次数 ----
    var shotsFired = 0
    var longShotsFired = 0
    var teleportsUsed = 0
    var teleportKills = 0
    var aimedFrames = 0
    var campFrames = 0

    // ---- 技能精度 (1.3) ----
    var longShotsHit = 0       // 长弓命中次数
    var teleportsDodged = 0    // 传送成功躲避次数
    var perfectAimFrames = 0   // 完美瞄准帧数（误差<3deg）

    // ---- 技能组合 (1.1) ----
    var teleportLongbowCombos = 0   // 传送后长弓次数
    var shortLongbowAlternate = 0   // 短弓+长弓交替次数
    var teleportHitDefense = 0      // 传送成功躲避并反击

    // ---- 主动移动 (1.4) ----
    var activeMoveFrames = 0        // 主动移动帧数（速度>阈值）
    var chaseFrames = 0             // 追击帧数（向敌人方向移动）
    var centerFrames = 0            // 在中心区域帧数（奖励占据有利位置）
    var positionDiversity = 0f      // 位置多样性（基于访问不同区域的数量）

    // ---- 混合回放 (1.5) ----
    var keyMomentFrames = 0
    var enemyLongbowAimFrames = 0   // 敌人蓄长弓瞄准自己的帧数
    var longbowChargeFrames = 0     // 自身蓄长弓帧数

    fun reset() {
        aiWon = false; frames = 0; hitsDealt = 0; hitsTaken = 0
        shotsFired = 0; longShotsFired = 0; teleportsUsed = 0; teleportKills = 0
        aimedFrames = 0; campFrames = 0
        longShotsHit = 0; teleportsDodged = 0; perfectAimFrames = 0
        teleportLongbowCombos = 0; shortLongbowAlternate = 0; teleportHitDefense = 0
        activeMoveFrames = 0; chaseFrames = 0; centerFrames = 0
        positionDiversity = 0f
        keyMomentFrames = 0; enemyLongbowAimFrames = 0; longbowChargeFrames = 0
    }

    fun fitness(shaping: Float): Float {
        val playFrames = maxOf(1, frames - COUNTDOWN_FRAMES)
        var wallRatio = campFrames / playFrames.toFloat()
        if (wallRatio > 1f) wallRatio = 1f

        // —— 效果项 ——
        var f = if (aiWon) WIN_SCORE else 0f
        f += minOf(frames, 7200) / 7200f * 40f * (1f - wallRatio * 0.8f)
        f += hitsDealt * 15f
        f -= hitsTaken * 10f  // 受击翻倍（-5→-10，角落更容易被瞄准）
        f -= wallRatio * 250f  // 靠墙重罚（绝对值>>胜利分，彻底抑制蹲坑）
        f -= enemyLongbowAimFrames / playFrames.toFloat() * 30f

        // —— 精度奖励 (1.3) ——
        f += longShotsHit * 30f * shaping  // 长弓命中重奖（20→30）
        f += teleportsDodged * 10f * shaping
        f += minOf(perfectAimFrames, 300) * 0.08f * shaping

        // —— 大招尝试奖励：即使没中也鼓励蓄力和射出 ——
        f += longbowChargeFrames / playFrames.toFloat() * 20f * shaping  // 蓄力时间比奖励
        f += minOf(longShotsFired, 10) * 8f * shaping  // 发射长弓即奖（不必命中）

        // —— 技能组合奖励 (1.1) ——
        var combo = 0f
        if (longShotsFired > 0 && teleportsUsed > 0) combo += 15f * shaping
        if (shotsFired > 5 && longShotsFired > 0) combo += 10f * shaping
        combo += teleportKills * 25f
        combo += teleportLongbowCombos * 15f
        combo += shortLongbowAlternate * 3f
        combo += teleportHitDefense * 8f
        f += combo

        // —— 主动移动奖励 (1.4) ——
        var moveBonus = 0f
        val activeRatio = activeMoveFrames / playFrames.toFloat()
        moveBonus += activeRatio * 15f  // 主动移动奖励
        moveBonus += chaseFrames / playFrames.toFloat() * 10f  // 追击奖励
        moveBonus += centerFrames / playFrames.toFloat() * 8f  // 占据中心奖励
        moveBonus += positionDiversity * 5f  // 位置多样性奖励
        f += moveBonus

        // —— 关键时刻：处于关键时刻=积极战斗，奖励活跃度 (1.5) ——
        val keyMomentRatio = keyMomentFrames / playFrames.toFloat()
        f += keyMomentRatio * 15f  // 越活跃（传送/蓄力/击杀附近），越奖励

        // —— 行为项（cap 30） ——
        var behavior = 0f
        behavior += minOf(longShotsFired, 5) * 3f * shaping
        behavior += minOf(teleportsUsed, 3) * 1f * shaping
        behavior += minOf(aimedFrames, 300) * 0.03f * shaping
        if (behavior > BEHAVIOR_CAP) behavior = BEHAVIOR_CAP
        f += behavior

        // —— 超时惩罚（二次方加速，靠墙者刑罚加倍） ——
        if (playFrames > QUICK_WIN_FRAMES) {
            val overtimeSec = (playFrames - QUICK_WIN_FRAMES) / 60f
            // 平方增长: 10s→150, 20s→600, 30s→1350
            f -= overtimeSec * overtimeSec * 1.5f * (1f + wallRatio)
            // 超 20 秒未胜 → 归零，强推击杀欲望
            if (!aiWon && overtimeSec > 20f) f = minOf(f, -10f)
        }
        // 胜利永远最大：败局总分封顶在胜局基础分之下，
        // 防止行为奖励让"打得热闹但输了"反超一场胜利
        if (!aiWon && f > WIN_SCORE - 1f) f = WIN_SCORE - 1f
        return f
    }

    fun fitness(): Float = fitness(1f)
}
