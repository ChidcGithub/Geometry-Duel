package com.geometryduel.game.state;

import com.geometryduel.game.actor.PlayerActor;
import com.geometryduel.render.Shapes;

/** 还原 PlayerActorState：玩家动作状态机基类。 */
public abstract class PlayerState {
    public MoveState moveState;

    public abstract void act(PlayerActor p);

    public abstract void update(PlayerActor p);

    public abstract PlayerState entryState(PlayerActor p);

    public abstract void displayEffect(Shapes s, PlayerActor p);

    public boolean isDamaged() {
        return false;
    }

    public boolean isDrawingLongBow() {
        return false;
    }

    public boolean hasCompletedLongBowCharge(PlayerActor p) {
        return false;
    }

    /** 敌方玩家相对本玩家的角度（getEnemyPlayerActorAngle）。 */
    protected float enemyAngle(PlayerActor p) {
        PlayerActor enemy = p.group.enemyGroup.firstPlayer();
        if (enemy == null) return p.aimAngle;
        return (float) Math.atan2(enemy.pos.y - p.pos.y, enemy.pos.x - p.pos.x);
    }
}
