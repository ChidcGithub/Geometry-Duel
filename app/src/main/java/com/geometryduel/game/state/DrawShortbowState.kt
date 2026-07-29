package com.geometryduel.game.state

import com.geometryduel.game.GameSystem
import com.geometryduel.game.actor.PlayerActor
import com.geometryduel.game.actor.ShortbowArrow
import com.geometryduel.render.GameRenderer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 短弓（普通攻击）状态：
 * - 按住 Z：首次按下时瞄准角距离敌人较远则瞬间锁定；已大致对准后以 2°/帧 缓慢追踪
 * - 每 12 帧射一箭（frameCount % 12 == 0）
 * - 箭生成于玩家前方 24，初速 24
 * - 移动减速为 0.25 倍
 * - 特效：70 长瞄准线 + 半径 50、张角 45° 的弓弧
 */
class DrawShortbowState(private val sys: GameSystem) : PlayerState() {

    companion object {
        const val FIRE_INTERVAL = 12
        const val ARROW_OFFSET = 24f
        const val ARROW_SPEED = 24f
        const val MOVE_SCALE = 0.25f
        const val AIM_TURN_SPEED = 0.035f
        const val AIM_SNAP_THRESHOLD = 0.2f
    }

    override fun entryState(p: PlayerActor): PlayerState = this

    override fun act(p: PlayerActor) {
        val target = enemyAngle(p)
        var diff = target - p.aimAngle
        while (diff > 3.1415927f) diff -= 6.2831855f
        while (diff < -3.1415927f) diff += 6.2831855f
        if (abs(diff) > AIM_SNAP_THRESHOLD) {
            p.aimAngle = target
        } else {
            if (diff > AIM_TURN_SPEED) diff = AIM_TURN_SPEED
            else if (diff < -AIM_TURN_SPEED) diff = -AIM_TURN_SPEED
            p.aimAngle += diff
        }
        p.addVelocity(p.engine.horizontalMove * MOVE_SCALE, p.engine.verticalMove * MOVE_SCALE)
        if (sys.frameCount % FIRE_INTERVAL == 0) fire(p)
        if (!p.engine.shotButtonPressed) {
            p.state = moveState.entryState(p)
        }
    }

    private fun fire(p: PlayerActor) {
        sys.playSFire()
        val a = ShortbowArrow(sys)
        val f = p.aimAngle
        a.pos.x = p.pos.x + cos(f) * ARROW_OFFSET
        a.pos.y = p.pos.y + sin(f) * ARROW_OFFSET
        a.rotationAngle = f
        a.vel(f, ARROW_SPEED)
        p.group?.addArrow(a)
    }

    override fun update(p: PlayerActor) {
    }

    override fun displayEffect(s: GameRenderer, p: PlayerActor) {
        s.strokeWeight(3f)
        s.line(0f, 0f, cos(p.aimAngle) * 70f, sin(p.aimAngle) * 70f)
        s.noFill()
        s.arc(0f, 0f, 50f, Math.toDegrees(p.aimAngle.toDouble()).toFloat() - 22.5f, 45f)
    }
}
