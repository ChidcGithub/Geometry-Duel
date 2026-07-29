package com.geometryduel.game.state

import com.geometryduel.game.GameSystem
import com.geometryduel.render.GameRenderer

/**
 * 结算状态（还原 ClientGameResultState）：
 * - 显示"你赢了/你输了"；60 帧后显示"按 X 键重新开始"
 * - 演示模式：180 帧后自动重开；对战模式：60 帧后按 X 重开（回到演示）
 */
class ResultGameState(
    system: GameSystem,
    val winGroup: Int,
    val playerWon: Boolean,
) : GameSystemState(system) {

    companion object {
        const val DURATION = 60
    }

    init {
        system.stateIndex = 3
    }

    override fun updateSystem() {
        system.myGroup.update()
        system.otherGroup.update()
        system.particles.update()
    }

    override fun checkStateTransition() {
        if (system.demoPlay) {
            if (properFrameCount > DURATION * 3) system.requestRestart()
        } else if (properFrameCount > DURATION && system.restartPressed) {
            system.requestRestart()
        }
    }

    override fun display(s: GameRenderer) {
        system.myGroup.displayPlayers(s)
        system.otherGroup.displayPlayers(s)
        system.particles.display(s)
    }

    override fun getScore(groupId: Int): Float = if (winGroup == groupId) 1f else 0f
}
