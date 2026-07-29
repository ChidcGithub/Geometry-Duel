package com.geometryduel.neat

import com.geometryduel.game.actor.ActorGroup
import com.geometryduel.game.actor.ArrowActor
import com.geometryduel.game.actor.LongbowArrowHead
import com.geometryduel.game.actor.PlayerActor
import com.geometryduel.game.actor.ShortbowArrow
import com.geometryduel.game.state.DamagedState
import com.geometryduel.game.state.DrawLongbowState
import kotlin.math.abs
import kotlin.math.sqrt

class MatchTracker(private var group: ActorGroup) {

    companion object {
        const val COMBO_WINDOW = 300
        const val AIM_TOLERANCE = 0.12f
        const val PERFECT_AIM_TOLERANCE = 0.052f  // ~3deg

        private fun isEnemyAlive(enemy: PlayerActor?): Boolean {
            return enemy != null &&
                    !(enemy.state is DamagedState && enemy.damageRemainingFrameCount <= 1)
        }
    }

    private val prevArrows = ArrayList<ArrowActor>()
    private var prevRecallCount = 0
    private var frame = 0
    private var lastRecallFrame = -COMBO_WINDOW
    private var enemyWasAlive = false
    private var lastAttackType = 0 // 0=none, 1=shortbow, 2=longbow

    // counts
    var shotsFired = 0
    var longShotsFired = 0
    var teleportsUsed = 0
    var teleportKills = 0
    var aimedFrames = 0
    var campFrames = 0
    // 1.3
    var longShotsHit = 0
    var teleportsDodged = 0
    var perfectAimFrames = 0
    // 1.1
    var teleportLongbowCombos = 0
    var shortLongbowAlternate = 0
    var teleportHitDefense = 0
    // 1.4
    var activeMoveFrames = 0
    var chaseFrames = 0
    var centerFrames = 0
    private val visitedZones = BooleanArray(9)  // 3x3区域访问记录
    // 1.5 混合回放：关键时刻追踪
    var keyMomentFrames = 0  // 关键时刻总帧数
    private var lastKillFrame = -999  // 上次击杀帧
    private var lastTeleportFrame = -999
    var enemyLongbowAimFrames = 0  // 敌人蓄长弓瞄准自己
    var longbowChargeFrames = 0    // 自身蓄长弓帧数（鼓励尝试大招）

    /** 重置所有计数器供复用 */
    fun reset(newGroup: ActorGroup) {
        this.group = newGroup
        prevArrows.clear()
        prevRecallCount = 0
        frame = 0
        lastRecallFrame = -COMBO_WINDOW
        enemyWasAlive = false
        lastAttackType = 0
        shotsFired = 0; longShotsFired = 0; teleportsUsed = 0; teleportKills = 0
        aimedFrames = 0; campFrames = 0
        longShotsHit = 0; teleportsDodged = 0; perfectAimFrames = 0
        teleportLongbowCombos = 0; shortLongbowAlternate = 0; teleportHitDefense = 0
        activeMoveFrames = 0; chaseFrames = 0; centerFrames = 0
        visitedZones.fill(false)
        keyMomentFrames = 0
        lastKillFrame = -999; lastTeleportFrame = -999
        enemyLongbowAimFrames = 0
        longbowChargeFrames = 0
    }

    fun update() {
        frame++

        val arrows = group.arrows
        val p = group.firstPlayer()
        val enemy = group.enemyGroup?.firstPlayer()

        // ---- 箭发射检测 ----
        for (a in arrows) {
            if (!prevArrows.contains(a)) {
                if (a is LongbowArrowHead) {
                    longShotsFired++
                    // 传送+长弓连招：传送后30帧内放长弓
                    if (frame - lastRecallFrame <= 30) teleportLongbowCombos++
                    // 攻击类型切换检测
                    if (lastAttackType == 1) shortLongbowAlternate++
                    lastAttackType = 2
                } else if (a is ShortbowArrow) {
                    shotsFired++
                    if (lastAttackType == 2) shortLongbowAlternate++
                    lastAttackType = 1
                }
            }
        }
        prevArrows.clear()
        prevArrows.addAll(arrows)

        // ---- 传送追踪 ----
        if (p != null) {
            val newRecalls = p.teleportRecallCount - prevRecallCount
            if (newRecalls > 0) {
                lastRecallFrame = frame
                lastTeleportFrame = frame  // 记录传送时刻 (1.5)
                teleportsUsed += newRecalls

                // 传送躲避：传送时附近有敌方箭
                val enemyArrows = group.enemyGroup?.arrows
                if (enemyArrows != null) {
                    for (ea in enemyArrows) {
                        val dx = ea.pos.x - p.pos.x
                        val dy = ea.pos.y - p.pos.y
                        if (dx * dx + dy * dy < 40000f) { // within 200px
                            teleportsDodged++
                            break
                        }
                    }
                }
            }
            prevRecallCount = p.teleportRecallCount
        }

        // ---- 敌方击杀 = 长弓命中（长弓=致命） ----
        val enemyAliveNow = isEnemyAlive(enemy)
        if (enemyWasAlive && !enemyAliveNow && frame - lastRecallFrame <= COMBO_WINDOW) {
            teleportKills++
        }
        if (enemyWasAlive && !enemyAliveNow && frame - lastRecallFrame <= 120) {
            teleportHitDefense++
        }
        if (enemyWasAlive && !enemyAliveNow) {
            longShotsHit++ // 敌方消失=被击杀（长弓是唯一一击必杀方式）
            lastKillFrame = frame  // 记录击杀时刻 (1.5)
        }
        enemyWasAlive = enemyAliveNow

        // ---- 大招瞄准追踪 ----
        if (p != null && enemy != null && p.state is DrawLongbowState) {
            longbowChargeFrames++  // 只要蓄力就给基础奖励
            val want = kotlin.math.atan2(enemy.pos.y - p.pos.y, enemy.pos.x - p.pos.x)
            var err = p.aimAngle - want
            while (err > 3.1415927f) err -= 6.2831855f
            while (err < -3.1415927f) err += 6.2831855f
            val absErr = abs(err)
            if (absErr < AIM_TOLERANCE) aimedFrames++
            if (absErr < PERFECT_AIM_TOLERANCE) perfectAimFrames++
        }

        // ---- 靠墙检测 ----
        if (p != null) {
            val x = p.pos.x
            val y = p.pos.y
            // 缩小安全区域：从64-576改为100-540
            if (x < 100f || x > 540f || y < 100f || y > 540f) campFrames++
            // 角落特别惩罚：距离任意角落<80px
            if (x < 80f || x > 560f) {
                if (y < 80f || y > 560f) campFrames += 2  // 角落双倍计数
            }

            // 主动移动检测：速度>1.0
            val speed = sqrt(p.vel.x * p.vel.x + p.vel.y * p.vel.y)
            if (speed > 1.0f) activeMoveFrames++

            // 追击检测：向敌人方向移动
            if (enemy != null && speed > 0.5f) {
                val toEnemyX = enemy.pos.x - x
                val toEnemyY = enemy.pos.y - y
                val dist = sqrt(toEnemyX * toEnemyX + toEnemyY * toEnemyY)
                if (dist > 0.0001f) {
                    val dot = (p.vel.x * toEnemyX + p.vel.y * toEnemyY) / dist
                    if (dot > 0.5f) chaseFrames++  // 向敌人方向移动
                }
            }

            // 中心区域检测：在200-440范围内
            if (x > 200f && x < 440f && y > 200f && y < 440f) centerFrames++

            // 位置多样性：记录访问的3x3区域
            val zoneX = if (x < 213f) 0 else if (x < 426f) 1 else 2
            val zoneY = if (y < 213f) 0 else if (y < 426f) 1 else 2
            visitedZones[zoneY * 3 + zoneX] = true
        }

        // ---- 关键时刻检测 (1.5) ----
        var isKeyMoment = false

        // 击杀前后30帧
        if (frame - lastKillFrame <= 30) isKeyMoment = true

        // 传送后30帧
        if (frame - lastTeleportFrame <= 30) isKeyMoment = true

        // 长弓蓄力期间
        if (p != null && p.state is DrawLongbowState) isKeyMoment = true

        // 敌人蓄长弓瞄准自己 → 每帧扣分（角落=活靶子）
        if (p != null && enemy != null && enemy.state is DrawLongbowState) {
            val want = kotlin.math.atan2(p.pos.y - enemy.pos.y, p.pos.x - enemy.pos.x)
            var err = enemy.aimAngle - want
            while (err > 3.1415927f) err -= 6.2831855f
            while (err < -3.1415927f) err += 6.2831855f
            if (abs(err) < 0.12f) enemyLongbowAimFrames++
        }

        // 敌方受击期间（反击机会）
        if (enemy != null && enemy.state.isDamaged()) isKeyMoment = true

        if (isKeyMoment) keyMomentFrames++
    }

    fun fill(m: MatchStats) {
        m.shotsFired = shotsFired
        m.longShotsFired = longShotsFired
        m.teleportsUsed = teleportsUsed
        m.teleportKills = teleportKills
        m.aimedFrames = aimedFrames
        m.campFrames = campFrames
        m.longShotsHit = longShotsHit
        m.teleportsDodged = teleportsDodged
        m.perfectAimFrames = perfectAimFrames
        m.teleportLongbowCombos = teleportLongbowCombos
        m.shortLongbowAlternate = shortLongbowAlternate
        m.teleportHitDefense = teleportHitDefense
        m.activeMoveFrames = activeMoveFrames
        m.chaseFrames = chaseFrames
        m.centerFrames = centerFrames
        m.keyMomentFrames = keyMomentFrames
        m.enemyLongbowAimFrames = enemyLongbowAimFrames
        m.longbowChargeFrames = longbowChargeFrames

        // 计算位置多样性：访问的不同区域数量 / 9
        var visitedCount = 0
        for (visited in visitedZones) if (visited) visitedCount++
        m.positionDiversity = visitedCount / 9f
    }

}
