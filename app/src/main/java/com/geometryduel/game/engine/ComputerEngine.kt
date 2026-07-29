package com.geometryduel.game.engine

import com.geometryduel.game.Body
import com.geometryduel.game.GameSystem
import com.geometryduel.game.actor.ArrowActor
import com.geometryduel.game.actor.PlayerActor
import kotlin.math.abs

/**
 * 电脑玩家引擎，逐项还原 pama1234...util.ai.mesh：
 * - 计划（PlayerPlan）：Move（游走）/ Jab（短弓压制）/ Kill（长弓狙杀）
 * - 每 10 帧重选一次计划（ComputerPlayerEngine.planUpdateFrameCount）
 * - 难度 level ∈ [0,1]，影响计划解锁概率（killPlan≥0.5 解锁、≥1 必用；jabPlan≥0.25 概率解锁、≥0.5 必用）
 */
class ComputerEngine(private val sys: GameSystem, private val level: Float) : PlayerEngine() {

    private var time = 0
    private var currentPlan: Plan
    private val movePlan = MovePlan()
    private val jabPlan = JabPlan()
    private val killPlan = KillPlan()

    init {
        currentPlan = movePlan
    }

    override fun run(player: PlayerActor) {
        currentPlan.execute(player)
        if (time % 10 == 0) currentPlan = currentPlan.nextPlan(player)
        time++
    }

    private fun random(hi: Float) = sys.random(hi)
    private fun random(lo: Float, hi: Float) = sys.random(lo, hi)

    // ------------------------------------------------------------------
    private abstract inner class DefaultPlan : Plan {
        var horizontalMove = 0
        var verticalMove = 0
        val movePlanAccuracy = 0.7f   // 原作 DefaultPlayerPlan
        val jabPlanAccuracy = 0.2f

        override fun execute(p: PlayerActor) {
            operateMoveButton(horizontalMove, verticalMove)
            operateLongShotButton(false)
        }

        override fun nextPlan(p: PlayerActor): Plan {
            val enemy = p.group?.enemyGroup?.firstPlayer() ?: return movePlan
            if (enemy.state.isDamaged()) return killPlanGated()

            // 检测敌人是否在角落（1.4：增强对抗角落战术）
            if (isEnemyInCorner(enemy) && level > 0.3f) {
                // 敌人在角落时，主动占据中心区域压制
                setMoveDirectionToCenter(p)
                return if (random(1f) < 0.8f) jabPlanGated() else movePlan  // 倾向攻击
            }

            // 找最近的敌方来箭（即敌方射出的箭）
            var nearest: ArrowActor? = null
            var best = Float.MAX_VALUE
            for (a in enemy.group?.arrows ?: emptyList()) {
                val d2 = p.distPow2(a)
                if (d2 < best) { best = d2; nearest = a }
            }
            if (best < 40000f) { // 200 单位内有来箭：侧向闪避
                val toward = nearest!!.angle(p) // 箭 → 我
                val dir = nearest.directionAngle
                val dodge = if (toward - dir > 0) {
                    dir + random(QUARTER_PI) + QUARTER_PI
                } else {
                    dir - (random(QUARTER_PI) + QUARTER_PI)
                }
                setMoveDirection(p,
                    p.pos.x + Body.cos(dodge) * 100f,
                    p.pos.y + Body.sin(dodge) * 100f, 0f)
                return if (random(1f) < movePlanAccuracy) movePlan else jabPlanGated()
            }
            setMoveDirection(p, enemy)
            if (p.distPow2(enemy) < 100000f) {
                return if (random(1f) < movePlanAccuracy) movePlan else jabPlanGated()
            }
            return if (random(1f) < jabPlanAccuracy) movePlan else jabPlanGated()
        }

        /** 检测敌人是否在角落（1.4） */
        private fun isEnemyInCorner(enemy: PlayerActor): Boolean {
            val x = enemy.pos.x
            val y = enemy.pos.y
            // 距离任意角落<100px
            return (x < 100f || x > 540f) && (y < 100f || y > 540f)
        }

        /** 移动到中心区域（1.4） */
        private fun setMoveDirectionToCenter(p: PlayerActor) {
            val centerX = 320f + random(-80f, 80f)
            val centerY = 320f + random(-80f, 80f)
            setMoveDirection(p, centerX, centerY, 50f)
        }

        fun killPlanGated(): Plan {
            if (level >= 1f) return killPlan
            if (level >= 0.5f && random(1f) < (level - 0.5f) * 2f) return killPlan
            return jabPlanGated()
        }

        fun jabPlanGated(): Plan {
            if (level >= 0.5f) return jabPlan
            if (level >= 0.25f && random(1f) < (level - 0.25f) * 4f) return jabPlan
            return movePlan
        }

        /** 朝敌人对角半区随机点移动（容差 100）。 */
        fun setMoveDirection(p: PlayerActor, enemy: PlayerActor) {
            setMoveDirection(p,
                if (enemy.pos.x > 320f) random(0f, 320f) else random(320f, 640f),
                if (enemy.pos.y > 320f) random(0f, 320f) else random(320f, 640f),
                100f)
        }

        fun setMoveDirection(p: PlayerActor, tx: Float, ty: Float, tol: Float) {
            horizontalMove = when {
                tx > p.pos.x + tol -> 1
                tx < p.pos.x - tol -> -1
                else -> 0
            }
            verticalMove = when {
                ty > p.pos.y + tol -> 1
                ty < p.pos.y - tol -> -1
                else -> 0
            }
        }
    }

    private inner class MovePlan : DefaultPlan() {
        override fun execute(p: PlayerActor) {
            super.execute(p)
            operateShotButton(false)
        }
    }

    private inner class JabPlan : DefaultPlan() {
        override fun execute(p: PlayerActor) {
            super.execute(p)
            operateShotButton(true)
        }
    }

    private inner class KillPlan : Plan {
        override fun execute(p: PlayerActor) {
            val enemy = p.group?.enemyGroup?.firstPlayer() ?: return
            val diff = p.angle(enemy) - p.aimAngle
            // 横向输入转动瞄准角，对准后停转
            val h = if (abs(diff) < Math.toRadians(1.0).toFloat()) 0
            else if ((diff + TWO_PI) % TWO_PI > PI) -1 else 1
            operateMoveButton(h, 0)
            operateShotButton(false)
            // 蓄满后按 level-0.95 概率松开扳机（即 level=1 时每帧 5% 概率放箭）
            if (!p.state.hasCompletedLongBowCharge(p) || random(1f) >= level - 0.95f) {
                operateLongShotButton(true)
            } else {
                operateLongShotButton(false)
            }
        }

        override fun nextPlan(p: PlayerActor): Plan {
            val enemy = p.group?.enemyGroup?.firstPlayer() ?: return this
            val abandon = abs(p.angle(enemy) - p.aimAngle) > QUARTER_PI
                    || p.dist(enemy) < 400f
                    || !p.engine.longShotButtonPressed
            return if (abandon) movePlan else this
        }
    }

    private interface Plan {
        fun execute(p: PlayerActor)
        fun nextPlan(p: PlayerActor): Plan
    }

    companion object {
        private const val PI = 3.1415927f
        private const val TWO_PI = 6.2831855f
        private const val QUARTER_PI = 0.7853982f
    }
}
