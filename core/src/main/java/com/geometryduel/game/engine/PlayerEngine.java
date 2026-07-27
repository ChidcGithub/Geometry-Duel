package com.geometryduel.game.engine;

import com.geometryduel.game.actor.PlayerActor;

/** 还原 PlayerEngine：每帧向 inputDevice 写入操作意图。 */
public abstract class PlayerEngine {
    public float horizontalMove, verticalMove;
    public boolean shotButtonPressed, longShotButtonPressed, teleportButtonPressed;

    public abstract void run(PlayerActor player);

    public void operateMove(float x, float y) {
        this.horizontalMove = x;
        this.verticalMove = y;
    }

    public void operateMoveButton(int x, int y) {
        this.horizontalMove = x;
        this.verticalMove = y;
    }

    public void operateShotButton(boolean b) {
        this.shotButtonPressed = b;
    }

    public void operateLongShotButton(boolean b) {
        this.longShotButtonPressed = b;
    }

    public void operateTeleportButton(boolean b) {
        this.teleportButtonPressed = b;
    }
}
