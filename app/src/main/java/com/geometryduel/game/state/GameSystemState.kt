package com.geometryduel.game.state

import com.geometryduel.game.GameSystem
import com.geometryduel.render.GameRenderer

/** 对局系统状态基类（还原 ServerGameSystemState / ClientGameSystemState）。 */
abstract class GameSystemState(val system: GameSystem) {
    var properFrameCount = 0

    fun update() {
        checkStateTransition()
        properFrameCount++
        updateSystem()
    }

    protected abstract fun updateSystem()

    protected abstract fun checkStateTransition()

    /** 世界空间绘制（displaySystem）。 */
    abstract fun display(s: GameRenderer)

    open fun getScore(groupId: Int): Float = 0f
}
