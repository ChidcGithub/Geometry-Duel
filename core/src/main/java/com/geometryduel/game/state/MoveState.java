package com.geometryduel.game.state;

import com.geometryduel.game.actor.PlayerActor;
import com.geometryduel.render.Shapes;

/**
 * 移动状态（还原 MovePlayerActorState）：
 * 全速移动；按下 Z/X/C 分别进入短弓/长弓/传送状态，进入时瞄准角重置为指向敌人。
 */
public class MoveState extends PlayerState {
    public DrawShortbowState drawShortbowState;
    public DrawLongbowState drawLongbowState;
    public DoTeleportState doTeleportState;

    @Override
    public void act(PlayerActor p) {
        p.addVelocity(p.engine.horizontalMove, p.engine.verticalMove);
        if (p.engine.shotButtonPressed) {
            p.state = drawShortbowState.entryState(p);
            p.aimAngle = enemyAngle(p);
        } else if (p.engine.longShotButtonPressed) {
            p.state = drawLongbowState.entryState(p);
            p.aimAngle = enemyAngle(p);
        } else if (p.engine.teleportButtonPressed) {
            p.state = doTeleportState.entryState(p);
            p.aimAngle = enemyAngle(p);
        }
    }

    @Override
    public void update(PlayerActor p) {
    }

    @Override
    public PlayerState entryState(PlayerActor p) {
        return this;
    }

    @Override
    public void displayEffect(Shapes s, PlayerActor p) {
    }
}
