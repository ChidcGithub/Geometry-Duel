package com.geometryduel.neat

import com.geometryduel.game.actor.PlayerActor
import com.geometryduel.game.engine.PlayerEngine

/**
 * 幽灵回放引擎：在无头模拟中逐帧播放 GhostData，扮演「玩家的影子」作为训练对手。
 * 录像耗尽后原地待机（移动归零、按钮全松）。run() 每帧恰好调用一次
 * （act 仅在 PlayGameState 触发），与录制侧逐帧对齐。
 */
class ReplayEngine(private val ghost: GhostData) : PlayerEngine() {
    private var frame = 0

    fun reset() {
        frame = 0
    }

    override fun run(player: PlayerActor) {
        var f = frame++
        // 录像结束后保持最后一帧的状态而非归零
        if (f >= ghost.frames) f = ghost.frames - 1
        operateMove(ghost.moveXAt(f), ghost.moveYAt(f))
        operateShotButton(ghost.shotAt(f))
        operateLongShotButton(ghost.longShotAt(f))
        operateTeleportButton(ghost.teleportAt(f))
    }
}
