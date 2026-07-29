package com.geometryduel.neat

import com.geometryduel.game.actor.PlayerActor
import com.geometryduel.game.state.DamagedState
import com.geometryduel.game.state.DrawLongbowState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 射线视觉感知：以 AI 为中心发出 rayCount 条射线，跟随瞄准角旋转。
 * 每条射线 2 个值：编码距离+类型 + 危险度（靠近AI的速度分量）。
 * 叠加 21 项全局状态。
 */
class VisionSensor(val rayCount: Int) {

    companion object {
        // +6: 自身传送冷却、传送锚点相对向量(x2)、敌人传送标记、敌人速度向量(x2)
        const val GLOBAL_INPUTS = 21
        private const val MAX_SIGHT = 860f
        private const val MAX_SPEED = 64f
    }

    private val cos: FloatArray = FloatArray(rayCount)
    private val sin: FloatArray = FloatArray(rayCount)

    // ---- 箭矢数据复用缓冲：sense 每帧填充一次，射线循环内联投影判定 ----
    private var aRelX = FloatArray(16)
    private var aRelY = FloatArray(16)
    private var aR2 = FloatArray(16)
    private var aVX = FloatArray(16)
    private var aVY = FloatArray(16)
    private var aLethal = BooleanArray(16)

    init {
        for (i in 0 until rayCount) {
            val a = i * 6.2831855f / rayCount
            cos[i] = cos(a)
            sin[i] = sin(a)
        }
    }

    private fun ensureArrowCapacity(n: Int) {
        if (n <= aRelX.size) return
        aRelX = FloatArray(n); aRelY = FloatArray(n); aR2 = FloatArray(n)
        aVX = FloatArray(n); aVY = FloatArray(n); aLethal = BooleanArray(n)
    }

    fun inputSize(): Int = rayCount * 2 + GLOBAL_INPUTS

    fun sense(self: PlayerActor, out: FloatArray) {
        val enemy = self.group?.enemyGroup?.firstPlayer()
        val px = self.pos.x
        val py = self.pos.y
        val cosAim = cos(self.aimAngle)
        val sinAim = sin(self.aimAngle)

        // ---- 每帧一次：提举敌人与箭矢相对数据（射线循环内只做投影判定）----
        val eRelX: Float
        val eRelY: Float
        val evx: Float
        val evy: Float
        var arrowCount = 0
        if (enemy != null) {
            eRelX = enemy.pos.x - px; eRelY = enemy.pos.y - py
            evx = enemy.vel.x; evy = enemy.vel.y
            val arrows = enemy.group?.arrows ?: emptyList()
            val n = arrows.size
            ensureArrowCapacity(n)
            for (j in 0 until n) {
                val a = arrows[j]
                aRelX[j] = a.pos.x - px
                aRelY[j] = a.pos.y - py
                val r = a.collisionRadius + 4f
                aR2[j] = r * r
                aVX[j] = a.vel.x
                aVY[j] = a.vel.y
                aLethal[j] = a.isLethal()
            }
            arrowCount = n
        } else {
            eRelX = 0f; eRelY = 0f; evx = 0f; evy = 0f
        }

        for (i in 0 until rayCount) {
            val dx = cos[i] * cosAim - sin[i] * sinAim
            val dy = sin[i] * cosAim + cos[i] * sinAim

            var tWall = when {
                dx > 0.0001f -> (624f - px) / dx
                dx < -0.0001f -> (16f - px) / dx
                else -> Float.MAX_VALUE
            }
            val tY = when {
                dy > 0.0001f -> (624f - py) / dy
                dy < -0.0001f -> (16f - py) / dy
                else -> Float.MAX_VALUE
            }
            if (tY < tWall) tWall = tY

            var best = tWall
            var type = 0
            var vDanger = 0f // 靠近AI的速度分量（正=迫近）

            if (enemy != null) {
                val proj = eRelX * dx + eRelY * dy
                if (proj >= 0f) {
                    val d2 = eRelX * eRelX + eRelY * eRelY - proj * proj
                    if (d2 <= 256f) { // 敌方玩家判定半径 16
                        var t = proj - sqrt(256f - d2)
                        if (t < 0f) t = 0f
                        if (t < best) {
                            best = t
                            type = 1
                            vDanger = -(evx * dx + evy * dy) / MAX_SPEED
                        }
                    }
                }
            }
            for (j in 0 until arrowCount) {
                val proj = aRelX[j] * dx + aRelY[j] * dy
                if (proj < 0f) continue
                val d2 = aRelX[j] * aRelX[j] + aRelY[j] * aRelY[j] - proj * proj
                val r2 = aR2[j]
                if (d2 > r2) continue
                var t = proj - sqrt(r2 - d2)
                if (t < 0f) t = 0f
                if (t < best) {
                    best = t
                    type = if (aLethal[j]) 3 else 2
                    vDanger = -(aVX[j] * dx + aVY[j] * dy) / MAX_SPEED
                }
            }

            val near = maxOf(0f, 1f - min(best, MAX_SIGHT) / MAX_SIGHT)
            val base = i * 2
            when (type) {
                1 -> out[base] = near * 0.5f + 0.5f
                2 -> out[base] = -(near * 0.5f + 0.5f)
                3 -> out[base] = -near * 0.5f
                else -> out[base] = near * 0.5f
            }
            out[base + 1] = (vDanger * near).coerceIn(-1f, 1f)
        }

        val g = rayCount * 2
        if (enemy != null) {
            val ex = enemy.pos.x - px
            val ey = enemy.pos.y - py
            val dist = sqrt(ex * ex + ey * ey)
            out[g] = if (dist > 0.0001f) ex / dist else 0f
            out[g + 1] = if (dist > 0.0001f) ey / dist else 0f
            out[g + 2] = min(1f, dist / 905f)
            var diff = kotlin.math.atan2(ey, ex) - self.aimAngle
            while (diff > 3.1415927f) diff -= 6.2831855f
            while (diff < -3.1415927f) diff += 6.2831855f
            out[g + 6] = diff / 3.1415927f
        } else {
            out[g] = 0f; out[g + 1] = 0f; out[g + 6] = 0f
            out[g + 2] = 1f
        }
        out[g + 3] = if (self.state.isDamaged())
            self.damageRemainingFrameCount / DamagedState.DURATION.toFloat() else 0f
        out[g + 4] = if (self.state is DrawLongbowState)
            min(1f, self.chargedFrameCount / DrawLongbowState.CHARGE_REQUIRED.toFloat()) else 0f
        out[g + 5] = if (self.teleportMarked)
            self.teleportMarkRemaining / PlayerActor.TELEPORT_MARK_DURATION.toFloat() else 0f
        out[g + 7] = if (enemy != null && enemy.state is DrawLongbowState) 1f else 0f
        out[g + 8] = if (enemy != null && enemy.state.isDamaged())
            enemy.damageRemainingFrameCount / DamagedState.DURATION.toFloat() else 0f

        // ---- 1.5：新增感知信息 ----
        // 自身位置归一化（0-1）
        out[g + 9] = (px - 16f) / 608f  // x位置
        out[g + 10] = (py - 16f) / 608f  // y位置

        // 距离最近墙壁的距离（归一化，越小越危险）
        val distLeft = px - 16f
        val distRight = 624f - px
        val distTop = py - 16f
        val distBottom = 624f - py
        val minWallDist = minOf(minOf(distLeft, distRight), minOf(distTop, distBottom))
        out[g + 11] = minWallDist / 320f  // 归一化到0-1

        // 自身速度大小（归一化）
        val speed = sqrt(self.vel.x * self.vel.x + self.vel.y * self.vel.y)
        out[g + 12] = min(1f, speed / 10f)  // 最大速度约10

        // 中心区域标记（1=在中心区域，0=不在）
        // 中心区域定义为200-440范围内
        val inCenter = px > 200f && px < 440f && py > 200f && py < 440f
        out[g + 13] = if (inCenter) 1f else 0f

        // 敌人长弓瞄准危险度：0=不蓄力，>0=敌人正在瞄自己
        if (enemy != null && enemy.state is DrawLongbowState) {
            val want = kotlin.math.atan2(py - enemy.pos.y, px - enemy.pos.x)
            var err = enemy.aimAngle - want
            while (err > 3.1415927f) err -= 6.2831855f
            while (err < -3.1415927f) err += 6.2831855f
            out[g + 14] = 1f - abs(err) / 3.1415927f
        } else {
            out[g + 14] = 0f
        }

        // ---- 2.0：传送与速度感知补全 ----
        // 自身传送冷却（0=就绪，1=刚回传）——消除"不知道传送何时可用"的盲区
        out[g + 15] = min(1f, self.teleportCooldown / PlayerActor.TELEPORT_COOLDOWN.toFloat())

        // 传送锚点相对向量（未标记为 0）——让 AI 学会评估回传落点
        // /640 与场地尺度对齐：/320 会让半场外锚点饱和在 ±1，丢失远锚点分辨力
        if (self.teleportMarked) {
            out[g + 16] = clamp1((self.teleportAnchorX - px) / 640f)
            out[g + 17] = clamp1((self.teleportAnchorY - py) / 640f)
        } else {
            out[g + 16] = 0f; out[g + 17] = 0f
        }

        // 敌人传送标记剩余时间比（0=未标记）——预判对手回传时机
        out[g + 18] = if (enemy != null && enemy.teleportMarked)
            enemy.teleportMarkRemaining / PlayerActor.TELEPORT_MARK_DURATION.toFloat() else 0f

        // 敌人速度向量（预判走位与箭路，射线危险度只有径向分量）
        if (enemy != null) {
            out[g + 19] = clamp1(enemy.vel.x / PlayerActor.MAX_VX)
            out[g + 20] = clamp1(enemy.vel.y / PlayerActor.MAX_VY)
        } else {
            out[g + 19] = 0f; out[g + 20] = 0f
        }
    }

    private fun clamp1(v: Float) = if (v < -1f) -1f else if (v > 1f) 1f else v
}
