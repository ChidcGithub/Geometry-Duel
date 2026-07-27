package com.geometryduel.game.engine;

import com.geometryduel.game.GameSystem;
import com.geometryduel.game.actor.ArrowActor;
import com.geometryduel.game.actor.PlayerActor;

/**
 * 电脑玩家引擎，逐项还原 pama1234...util.ai.mesh：
 * - 计划（PlayerPlan）：Move（游走）/ Jab（短弓压制）/ Kill（长弓狙杀）
 * - 每 10 帧重选一次计划（ComputerPlayerEngine.planUpdateFrameCount）
 * - 难度 level ∈ [0,1]，影响计划解锁概率（killPlan≥0.5 解锁、≥1 必用；jabPlan≥0.25 概率解锁、≥0.5 必用）
 */
public class ComputerEngine extends PlayerEngine {
    private static final float PI = 3.1415927f, TWO_PI = 6.2831855f;
    private static final float QUARTER_PI = 0.7853982f;

    private final GameSystem sys;
    private final float level;
    private int time;
    private Plan currentPlan;

    private final MovePlan movePlan;
    private final JabPlan jabPlan;
    private final KillPlan killPlan;

    public ComputerEngine(GameSystem sys, float level) {
        this.sys = sys;
        this.level = level;
        this.movePlan = new MovePlan();
        this.jabPlan = new JabPlan();
        this.killPlan = new KillPlan();
        this.currentPlan = movePlan;
    }

    @Override
    public void run(PlayerActor player) {
        currentPlan.execute(player);
        if (time % 10 == 0) currentPlan = currentPlan.nextPlan(player);
        time++;
    }

    private float random(float hi) {
        return sys.random(hi);
    }

    private float random(float lo, float hi) {
        return sys.random(lo, hi);
    }

    // ------------------------------------------------------------------
    private abstract class DefaultPlan implements Plan {
        int horizontalMove, verticalMove;
        final float movePlanAccuracy = 0.7f;   // 原作 DefaultPlayerPlan
        final float jabPlanAccuracy = 0.2f;

        @Override
        public void execute(PlayerActor p) {
            operateMoveButton(horizontalMove, verticalMove);
            operateLongShotButton(false);
        }

        @Override
        public Plan nextPlan(PlayerActor p) {
            PlayerActor enemy = p.group.enemyGroup.firstPlayer();
            if (enemy == null) return movePlan;
            if (enemy.state != null && enemy.state.isDamaged()) return killPlanGated();

            // 找最近的敌方来箭（即敌方射出的箭）
            ArrowActor nearest = null;
            float best = Float.MAX_VALUE;
            for (int i = 0; i < enemy.group.arrows.size(); i++) {
                ArrowActor a = enemy.group.arrows.get(i);
                float d2 = p.distPow2(a);
                if (d2 < best) { best = d2; nearest = a; }
            }
            if (best < 40000f) { // 200 单位内有来箭：侧向闪避
                float toward = nearest.angle(p); // 箭 → 我
                float dir = nearest.directionAngle;
                float dodge = toward - dir > 0
                        ? dir + random(QUARTER_PI) + QUARTER_PI
                        : dir - (random(QUARTER_PI) + QUARTER_PI);
                setMoveDirection(p,
                        p.pos.x + Body_cos(dodge) * 100f,
                        p.pos.y + Body_sin(dodge) * 100f, 0f);
                return random(1f) < movePlanAccuracy ? movePlan : jabPlanGated();
            }
            setMoveDirection(p, enemy);
            if (p.distPow2(enemy) < 100000f) {
                return random(1f) < movePlanAccuracy ? movePlan : jabPlanGated();
            }
            return random(1f) < jabPlanAccuracy ? movePlan : jabPlanGated();
        }

        Plan killPlanGated() {
            if (level >= 1f) return killPlan;
            if (level >= 0.5f && random(1f) < (level - 0.5f) * 2f) return killPlan;
            return jabPlanGated();
        }

        Plan jabPlanGated() {
            if (level >= 0.5f) return jabPlan;
            if (level >= 0.25f && random(1f) < (level - 0.25f) * 4f) return jabPlan;
            return movePlan;
        }

        /** 朝敌人对角半区随机点移动（容差 100）。 */
        void setMoveDirection(PlayerActor p, PlayerActor enemy) {
            setMoveDirection(p,
                    enemy.pos.x > 320f ? random(0f, 320f) : random(320f, 640f),
                    enemy.pos.y > 320f ? random(0f, 320f) : random(320f, 640f),
                    100f);
        }

        void setMoveDirection(PlayerActor p, float tx, float ty, float tol) {
            if (tx > p.pos.x + tol) horizontalMove = 1;
            else if (tx < p.pos.x - tol) horizontalMove = -1;
            else horizontalMove = 0;
            if (ty > p.pos.y + tol) verticalMove = 1;
            else if (ty < p.pos.y - tol) verticalMove = -1;
            else verticalMove = 0;
        }
    }

    private class MovePlan extends DefaultPlan {
        @Override
        public void execute(PlayerActor p) {
            super.execute(p);
            operateShotButton(false);
        }
    }

    private class JabPlan extends DefaultPlan {
        @Override
        public void execute(PlayerActor p) {
            super.execute(p);
            operateShotButton(true);
        }
    }

    private class KillPlan implements Plan {
        @Override
        public void execute(PlayerActor p) {
            PlayerActor enemy = p.group.enemyGroup.firstPlayer();
            if (enemy == null) return;
            float diff = p.angle(enemy) - p.aimAngle;
            // 横向输入转动瞄准角，对准后停转
            int h = Math.abs(diff) < (float) Math.toRadians(1f) ? 0
                    : (diff + TWO_PI) % TWO_PI > PI ? -1 : 1;
            operateMoveButton(h, 0);
            operateShotButton(false);
            // 蓄满后按 level-0.95 概率松开扳机（即 level=1 时每帧 5% 概率放箭）
            if (!p.state.hasCompletedLongBowCharge(p) || random(1f) >= level - 0.95f) {
                operateLongShotButton(true);
            } else {
                operateLongShotButton(false);
            }
        }

        @Override
        public Plan nextPlan(PlayerActor p) {
            PlayerActor enemy = p.group.enemyGroup.firstPlayer();
            if (enemy == null) return this;
            boolean abandon = Math.abs(p.angle(enemy) - p.aimAngle) > QUARTER_PI
                    || p.dist(enemy) < 400f
                    || !p.engine.longShotButtonPressed;
            return abandon ? movePlan : this;
        }
    }

    private interface Plan {
        void execute(PlayerActor p);

        Plan nextPlan(PlayerActor p);
    }

    private static float Body_cos(float a) {
        return (float) Math.cos(a);
    }

    private static float Body_sin(float a) {
        return (float) Math.sin(a);
    }
}
