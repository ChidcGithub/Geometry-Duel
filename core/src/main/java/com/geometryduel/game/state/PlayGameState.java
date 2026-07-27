package com.geometryduel.game.state;

import com.geometryduel.game.GameSystem;
import com.geometryduel.game.actor.ActorGroup;
import com.geometryduel.game.actor.ArrowActor;
import com.geometryduel.game.actor.PlayerActor;
import com.geometryduel.render.Shapes;

/**
 * 对战状态（还原 ClientPlayGameState / ServerPlayGameState）：
 * - 每帧：两组 update+act → 碰撞检测 → 粒子更新
 * - 箭-箭相撞：双方碎裂（10 个方块粒子：size 7、速度 1..5、寿命 1s）
 * - 箭-人相撞：致命箭 → 击杀（50 个方块粒子：size 16、速度 2..10、寿命 4s，震动=50）；
 *   非致命箭 → 击退（沿箭→人方向 ±π/4 随机，冲量 20，进入 45 帧受击，震动+10）
 * - 前 60 帧显示"冲啊！！"（渐隐）
 */
public class PlayGameState extends GameSystemState {
    public static final int MESSAGE_DURATION = 60;

    public PlayGameState(GameSystem system) {
        super(system);
        system.stateIndex = 2;
    }

    @Override
    protected void updateSystem() {
        system.myGroup.update();
        system.myGroup.act();
        system.otherGroup.update();
        system.otherGroup.act();
        checkCollision();
        system.particles.update();
    }

    @Override
    protected void checkStateTransition() {
        if (system.myGroup.players.isEmpty()) {
            system.currentState(new ResultGameState(system, system.otherGroup.id, false));
        } else if (system.otherGroup.players.isEmpty()) {
            system.currentState(new ResultGameState(system, system.myGroup.id, true));
        }
    }

    private void checkCollision() {
        ActorGroup my = system.myGroup, other = system.otherGroup;

        // 箭 vs 箭（跨组）
        for (int i = 0; i < my.arrows.size(); i++) {
            ArrowActor a = my.arrows.get(i);
            for (int j = 0; j < other.arrows.size(); j++) {
                ArrowActor b = other.arrows.get(j);
                if (a.isCollided(b)) {
                    breakArrow(a, my);
                    breakArrow(b, other);
                }
            }
        }

        // 我方箭 vs 敌方玩家
        PlayerActor enemy = other.firstPlayer();
        if (enemy != null) {
            for (int i = 0; i < my.arrows.size(); i++) {
                ArrowActor a = my.arrows.get(i);
                if (a.isCollided(enemy)) {
                    if (a.isLethal()) killPlayer(enemy);
                    else thrustPlayerActor(a, enemy);
                    breakArrow(a, my);
                }
            }
        }

        // 敌方箭 vs 我方玩家
        PlayerActor me = my.firstPlayer();
        if (me != null) {
            for (int i = 0; i < other.arrows.size(); i++) {
                ArrowActor a = other.arrows.get(i);
                if (a.isCollided(me)) {
                    if (a.isLethal()) killPlayer(me);
                    else thrustPlayerActor(a, me);
                    breakArrow(a, other);
                }
            }
        }
    }

    public void killPlayer(PlayerActor p) {
        system.particles.addSquareParticles(p.pos.x, p.pos.y, 50, 16f, 2f, 10f, 4f,
                system.theme().squareParticles);
        p.group.removePlayer(p);
        system.screenShakeValue = 50f;
        system.playHurt();
    }

    public void breakArrow(ArrowActor a, ActorGroup group) {
        system.particles.addSquareParticles(a.pos.x, a.pos.y, 10, 7f, 1f, 5f, 1f,
                system.theme().squareParticles);
        group.breakArrow(a);
    }

    public void thrustPlayerActor(ArrowActor a, PlayerActor p) {
        float ang = (float) Math.atan2(p.pos.y - a.pos.y, p.pos.x - a.pos.x)
                + system.random(-0.7853982f, 0.7853982f);
        p.vel.x += (float) Math.cos(ang) * 20f;
        p.vel.y += (float) Math.sin(ang) * 20f;
        p.state = system.damagedState.entryState(p);
        p.group.damageCount++;
        system.screenShakeValue += 10f;
        system.playHurt();
    }

    @Override
    public void display(Shapes s) {
        system.myGroup.displayPlayers(s);
        system.otherGroup.displayPlayers(s);
        system.myGroup.displayArrows(s);
        system.otherGroup.displayArrows(s);
        system.particles.display(s);
    }

    @Override
    public float getScore(int groupId) {
        return -(groupId == 0 ? system.myGroup.damageCount : system.otherGroup.damageCount);
    }
}
