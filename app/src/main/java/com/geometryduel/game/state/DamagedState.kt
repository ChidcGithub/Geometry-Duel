package com.geometryduel.game.state

import com.geometryduel.game.actor.PlayerActor
import com.geometryduel.render.GameRenderer

/**
 * 受击状态（还原 Server/ClientDamagedPlayerActorState）：
 * 持续 45 帧，期间无法操作；特效为半径 16 的红色圆环，
 * 透明度随剩余帧数线性衰减（remaining*256/45）。
 */
class DamagedState : PlayerState() {

    companion object {
        const val DURATION = 45
    }

    override fun act(p: PlayerActor) {
        p.damageRemainingFrameCount--
        if (p.damageRemainingFrameCount <= 0) {
            p.state = moveState.entryState(p)
        }
    }

    override fun update(p: PlayerActor) {
    }

    override fun entryState(p: PlayerActor): PlayerState {
        p.damageRemainingFrameCount = DURATION
        return this
    }

    override fun isDamaged() = true

    override fun displayEffect(s: GameRenderer, p: PlayerActor) {
        s.noFill()
        val alpha = (p.damageRemainingFrameCount * 256f / DURATION).toInt()
        s.stroke(p.sys.theme().playerDamaged, alpha)
        s.circle(0f, 0f, 32f)
    }
}
