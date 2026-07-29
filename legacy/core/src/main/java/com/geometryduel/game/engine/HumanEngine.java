package com.geometryduel.game.engine;

import com.geometryduel.game.InputData;
import com.geometryduel.game.actor.PlayerActor;

/**
 * 人类玩家引擎。
 * 桌面端还原 ServerHumanPlayerEngine（方向键 ±1 数字量）；
 * 安卓端还原 ClientAndroidHumanPlayerEngine（摇杆模拟量 dx/dy）。
 */
public class HumanEngine extends PlayerEngine {
    private final InputData in;
    private final boolean analog;

    /** analog=true 使用摇杆模拟量（安卓）；false 使用方向键（桌面）。 */
    public HumanEngine(InputData in, boolean analog) {
        this.in = in;
        this.analog = analog;
    }

    @Override
    public void run(PlayerActor player) {
        if (analog) {
            operateMove(in.dx, in.dy);
        } else {
            int x = (in.isLeftPressed ? -1 : 0) + (in.isRightPressed ? 1 : 0);
            int y = (in.isUpPressed ? -1 : 0) + (in.isDownPressed ? 1 : 0);
            operateMoveButton(x, y);
        }
        operateShotButton(in.isZPressed);
        operateLongShotButton(in.isXPressed);
        operateTeleportButton(in.isCPressed);
    }
}
