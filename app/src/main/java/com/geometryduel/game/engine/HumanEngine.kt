package com.geometryduel.game.engine

import com.geometryduel.game.InputData
import com.geometryduel.game.actor.PlayerActor

/**
 * 人类玩家引擎。
 * analog=true 使用摇杆模拟量（触屏）；false 使用方向键（键盘）。
 */
class HumanEngine(private val input: InputData, private val analog: Boolean) : PlayerEngine() {

    override fun run(player: PlayerActor) {
        if (analog) {
            operateMove(input.dx, input.dy)
        } else {
            val x = (if (input.isLeftPressed) -1 else 0) + (if (input.isRightPressed) 1 else 0)
            val y = (if (input.isUpPressed) -1 else 0) + (if (input.isDownPressed) 1 else 0)
            operateMoveButton(x, y)
        }
        operateShotButton(input.isZPressed)
        operateLongShotButton(input.isXPressed)
        operateTeleportButton(input.isCPressed)
    }
}
