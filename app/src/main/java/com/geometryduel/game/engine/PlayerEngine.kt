package com.geometryduel.game.engine

import com.geometryduel.game.actor.PlayerActor

/** 还原 PlayerEngine：每帧向 inputDevice 写入操作意图。 */
abstract class PlayerEngine {
    var horizontalMove = 0f
    var verticalMove = 0f
    var shotButtonPressed = false
    var longShotButtonPressed = false
    var teleportButtonPressed = false

    abstract fun run(player: PlayerActor)

    fun operateMove(x: Float, y: Float) {
        this.horizontalMove = x
        this.verticalMove = y
    }

    fun operateMoveButton(x: Int, y: Int) {
        this.horizontalMove = x.toFloat()
        this.verticalMove = y.toFloat()
    }

    fun operateShotButton(b: Boolean) {
        this.shotButtonPressed = b
    }

    fun operateLongShotButton(b: Boolean) {
        this.longShotButtonPressed = b
    }

    fun operateTeleportButton(b: Boolean) {
        this.teleportButtonPressed = b
    }
}
