package com.geometryduel.neat;

import com.geometryduel.game.engine.PlayerEngine;

import java.io.ByteArrayOutputStream;

/**
 * 幽灵录制器：逐帧采集人类玩家引擎输出，生成 GhostData。
 * 仅在 PlayGameState 调用 frame()（与 act() 调用时机对齐，保证回放逐帧同步）。
 */
public class GhostRecorder {
    private final ByteArrayOutputStream moveX = new ByteArrayOutputStream(8192);
    private final ByteArrayOutputStream moveY = new ByteArrayOutputStream(8192);
    private final ByteArrayOutputStream buttons = new ByteArrayOutputStream(8192);
    private int frames;

    public void frame(PlayerEngine e) {
        if (frames >= GhostData.MAX_FRAMES) return;
        moveX.write(GhostData.quantize(e.horizontalMove));
        moveY.write(GhostData.quantize(e.verticalMove));
        buttons.write((e.shotButtonPressed ? 1 : 0)
                | (e.longShotButtonPressed ? 2 : 0)
                | (e.teleportButtonPressed ? 4 : 0));
        frames++;
    }

    /** 结束录制；未录到任何帧时返回 null。 */
    public GhostData build() {
        if (frames == 0) return null;
        GhostData g = new GhostData();
        g.frames = frames;
        g.moveX = moveX.toByteArray();
        g.moveY = moveY.toByteArray();
        g.buttons = buttons.toByteArray();
        return g;
    }
}
