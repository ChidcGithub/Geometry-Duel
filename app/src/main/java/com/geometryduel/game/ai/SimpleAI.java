package com.geometryduel.game.ai;

import com.geometryduel.game.entity.GameState;
import com.geometryduel.game.entity.Projectile;

import java.util.List;
import java.util.Random;

public class SimpleAI extends BaseAI {

    private static final float ATTACK_RANGE = 400f;
    private static final float TOO_CLOSE_RANGE = 180f;
    private static final float IDEAL_RANGE = 320f;
    private static final float DODGE_THRESHOLD = 200f;

    private float shootCooldown;
    private static final float SHOOT_COOLDOWN_BASE = 0.6f;
    private final Random random = new Random();

    public SimpleAI(AIDifficulty difficulty) {
        super(difficulty);
        this.shootCooldown = 0;
    }

    @Override
    protected AIDecision computeDecision(GameState gameState) {
        if (self == null || enemy == null || self.getHp() <= 0) {
            return AIDecision.idle();
        }

        float dt = timeSinceLastDecision > 0 ? timeSinceLastDecision : 0.016f;
        shootCooldown -= dt;
        float dist = distanceToEnemy();
        float angle = angleToEnemy();

        if (isProjectileIncoming()) {
            return dodge(gameState);
        }

        if (dist < TOO_CLOSE_RANGE) {
            return retreat(gameState, angle);
        }

        if (dist > ATTACK_RANGE) {
            return approach(gameState, angle);
        }

        if (dist < IDEAL_RANGE && dist > TOO_CLOSE_RANGE) {
            return strafeAndShoot(gameState, angle);
        }

        if (canShoot()) {
            shootCooldown = SHOOT_COOLDOWN_BASE / difficulty.getAggression();
            return aimAndShoot(gameState);
        }

        return maintainDistance(gameState, angle, dist);
    }

    private AIDecision dodge(GameState gameState) {
        Projectile nearestThreat = findNearestThreat(gameState.getProjectiles());
        if (nearestThreat == null) {
            return AIDecision.idle();
        }

        float dx = nearestThreat.getX() - self.getX();
        float dy = nearestThreat.getY() - self.getY();

        float perpX = -dy;
        float perpY = dx;

        float len = (float) Math.sqrt(perpX * perpX + perpY * perpY);
        if (len > 0) {
            perpX /= len;
            perpY /= len;
        }

        return AIDecision.move(perpX * 3, perpY * 3);
    }

    private AIDecision retreat(GameState gameState, float angleToEnemy) {
        float retreatX = (float) -Math.cos(angleToEnemy);
        float retreatY = (float) -Math.sin(angleToEnemy);
        return AIDecision.move(retreatX, retreatY);
    }

    private AIDecision approach(GameState gameState, float angleToEnemy) {
        float dx = (float) Math.cos(angleToEnemy);
        float dy = (float) Math.sin(angleToEnemy);
        return AIDecision.move(dx, dy);
    }

    private AIDecision strafeAndShoot(GameState gameState, float angleToEnemy) {
        float strafeDir = Math.signum((float) Math.sin(System.currentTimeMillis() * 0.001f));
        float perpX = (float) -Math.sin(angleToEnemy) * strafeDir;
        float perpY = (float) Math.cos(angleToEnemy) * strafeDir;

        if (canShoot()) {
            shootCooldown = SHOOT_COOLDOWN_BASE / difficulty.getAggression();
            return AIDecision.shoot(predictEnemyX(), predictEnemyY(), difficulty.getAccuracy());
        }

        return AIDecision.move(perpX, perpY);
    }

    private AIDecision aimAndShoot(GameState gameState) {
        return AIDecision.shoot(predictEnemyX(), predictEnemyY(), difficulty.getAccuracy());
    }

    @Override
    protected float predictEnemyX() {
        return super.predictEnemyX() + (random.nextFloat() * 2 - 1) * (1.0f - difficulty.getAccuracy()) * 40f;
    }

    @Override
    protected float predictEnemyY() {
        return super.predictEnemyY() + (random.nextFloat() * 2 - 1) * (1.0f - difficulty.getAccuracy()) * 40f;
    }

    private AIDecision maintainDistance(GameState gameState, float angle, float dist) {
        if (dist < IDEAL_RANGE) {
            return retreat(gameState, angle);
        } else if (dist > IDEAL_RANGE + 80) {
            return approach(gameState, angle);
        }
        return AIDecision.idle();
    }

    private Projectile findNearestThreat(List<Projectile> projectiles) {
        Projectile nearest = null;
        float minDist = Float.MAX_VALUE;

        for (Projectile p : projectiles) {
            if (p.getOwner() == self) continue;

            float dx = p.getX() - self.getX();
            float dy = p.getY() - self.getY();
            float dist = (float) Math.sqrt(dx * dx + dy * dy);

            if (dist < DODGE_THRESHOLD && dist < minDist) {
                minDist = dist;
                nearest = p;
            }
        }
        return nearest;
    }

    private boolean canShoot() {
        return shootCooldown <= 0;
    }

    @Override
    public String getAIName() {
        return "SimpleAI-" + difficulty.name();
    }
}
