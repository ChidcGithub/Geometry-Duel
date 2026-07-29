package com.geometryduel.game.state

import com.geometryduel.game.actor.PlayerActor
import com.geometryduel.render.GameRenderer

/**
 * 移动状态（还原 MovePlayerActorState）：
 * 全速移动；按下 Z/X 分别进入短弓/长弓状态，进入时瞄准角重置为指向敌人。
 * （传送不再占用状态机，由 PlayerActor.handleTeleport 中央处理，任何状态下都可标记/回传）
 */
class MoveState : PlayerState() {
    lateinit var drawShortbowState: DrawShortbowState
    lateinit var drawLongbowState: DrawLongbowState

    override fun act(p: PlayerActor) {
        p.addVelocity(p.engine.horizontalMove, p.engine.verticalMove)
        if (p.engine.shotButtonPressed) {
            p.state = drawShortbowState.entryState(p)
            // 短弓不再入场瞬瞄，由 DrawShortbowState 缓慢追踪
        } else if (p.engine.longShotButtonPressed) {
            p.state = drawLongbowState.entryState(p)
            p.aimAngle = enemyAngle(p)
        }
    }

    override fun update(p: PlayerActor) {
    }

    override fun entryState(p: PlayerActor): PlayerState = this

    override fun displayEffect(s: GameRenderer, p: PlayerActor) {
    }
}
