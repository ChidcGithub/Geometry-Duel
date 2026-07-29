package com.geometryduel.game.state;

import com.geometryduel.game.actor.PlayerActor;
import com.geometryduel.render.Shapes;

/**
 * 受击状态（还原 Server/ClientDamagedPlayerActorState）：
 * 持续 45 帧，期间无法操作；特效为半径 16 的红色圆环，
 * 透明度随剩余帧数线性衰减（remaining*256/45）。
 */
public class DamagedState extends PlayerState {
    public static final int DURATION = 45;

    @Override
    public void act(PlayerActor p) {
        p.damageRemainingFrameCount--;
        if (p.damageRemainingFrameCount <= 0) {
            p.state = moveState.entryState(p);
        }
    }

    @Override
    public void update(PlayerActor p) {
    }

    @Override
    public PlayerState entryState(PlayerActor p) {
        p.damageRemainingFrameCount = DURATION;
        return this;
    }

    @Override
    public boolean isDamaged() {
        return true;
    }

    @Override
    public void displayEffect(Shapes s, PlayerActor p) {
        s.noFill();
        int alpha = (int) (p.damageRemainingFrameCount * 256f / DURATION);
        s.stroke(p.sys.theme().playerDamaged, alpha);
        s.circle(0, 0, 32f);
    }
}
