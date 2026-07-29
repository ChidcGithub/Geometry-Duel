package com.geometryduel.game.state

import com.geometryduel.game.actor.PlayerActor
import com.geometryduel.render.GameRenderer

/** 还原 PlayerActorState：玩家动作状态机基类。 */
abstract class PlayerState {
    lateinit var moveState: MoveState

    abstract fun act(p: PlayerActor)

    abstract fun update(p: PlayerActor)

    abstract fun entryState(p: PlayerActor): PlayerState

    abstract fun displayEffect(s: GameRenderer, p: PlayerActor)

    open fun isDamaged() = false

    open fun isDrawingLongBow() = false

    open fun hasCompletedLongBowCharge(p: PlayerActor) = false

    /** 敌方玩家相对本玩家的角度（getEnemyPlayerActorAngle）。 */
    protected fun enemyAngle(p: PlayerActor): Float {
        val enemy = p.group?.enemyGroup?.firstPlayer() ?: return p.aimAngle
        return kotlin.math.atan2(enemy.pos.y - p.pos.y, enemy.pos.x - p.pos.x)
    }
}
