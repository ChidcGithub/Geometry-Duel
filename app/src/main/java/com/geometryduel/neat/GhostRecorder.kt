package com.geometryduel.neat

import com.geometryduel.game.engine.PlayerEngine
import java.io.ByteArrayOutputStream

/**
 * 幽灵录制器：逐帧采集人类玩家引擎输出，生成 GhostData。
 * 仅在 PlayGameState 调用 frame()（与 act() 调用时机对齐，保证回放逐帧同步）。
 */
class GhostRecorder {
    private val moveX = ByteArrayOutputStream(8192)
    private val moveY = ByteArrayOutputStream(8192)
    private val buttons = ByteArrayOutputStream(8192)
    private var frames = 0

    fun frame(e: PlayerEngine) {
        if (frames >= GhostData.MAX_FRAMES) return
        moveX.write(GhostData.quantize(e.horizontalMove).toInt())
        moveY.write(GhostData.quantize(e.verticalMove).toInt())
        buttons.write(
            (if (e.shotButtonPressed) 1 else 0)
                    or (if (e.longShotButtonPressed) 2 else 0)
                    or (if (e.teleportButtonPressed) 4 else 0)
        )
        frames++
    }

    /** 结束录制；未录到任何帧时返回 null。 */
    fun build(): GhostData? {
        if (frames == 0) return null
        val g = GhostData()
        g.frames = frames
        g.moveX = moveX.toByteArray()
        g.moveY = moveY.toByteArray()
        g.buttons = buttons.toByteArray()
        return g
    }
}
