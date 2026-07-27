package com.geometryduel.game.ai;

import com.geometryduel.game.entity.GameState;
import com.geometryduel.game.entity.Player;
import com.geometryduel.game.entity.Projectile;

public abstract class BaseAI implements AIController {

    protected AIDifficulty difficulty;
    protected Player self;
    protected Player enemy;
    protected int playerIndex;

    protected float timeSinceLastDecision;
    protected AIDecision currentDecision;

    protected float reactionTimer;
    protected boolean reacting;

    protected float lastSelfHp;
    protected float lastEnemyHp;

    public BaseAI(AIDifficulty difficulty) {
        this.difficulty = difficulty;
        this.currentDecision = AIDecision.idle();
        this.timeSinceLastDecision = 0;
        this.reactionTimer = 0;
        this.reacting = false;
    }

    @Override
    public void initialize(GameState gameState, int playerIndex) {
        this.playerIndex = playerIndex;
        if (playerIndex == 0) {
            this.self = gameState.getPlayer1();
            this.enemy = gameState.getPlayer2();
        } else {
            this.self = gameState.getPlayer2();
            this.enemy = gameState.getPlayer1();
        }
        this.lastSelfHp = self.getHp();
        this.lastEnemyHp = enemy.getHp();
        onInitialize(gameState);
    }

    protected void onInitialize(GameState gameState) {}

    @Override
    public AIDecision update(float deltaTime, GameState gameState) {
        if (self == null || enemy == null) {
            return AIDecision.idle();
        }

        if (reacting) {
            reactionTimer -= deltaTime;
            if (reactionTimer <= 0) {
                reacting = false;
            }
            return currentDecision;
        }

        timeSinceLastDecision += deltaTime;

        if (timeSinceLastDecision >= difficulty.getDecisionInterval()) {
            timeSinceLastDecision = 0;
            AIDecision newDecision = computeDecision(gameState);

            if (shouldReact(newDecision, gameState)) {
                reactionTimer = difficulty.getReactionTime();
                reacting = true;
            }

            currentDecision = newDecision;
        }

        lastSelfHp = self.getHp();
        lastEnemyHp = enemy.getHp();

        return currentDecision;
    }

    protected boolean shouldReact(AIDecision newDecision, GameState gameState) {
        if (lastSelfHp > self.getHp()) {
            return true;
        }
        for (Projectile p : gameState.getProjectiles()) {
            if (p.getOwner() != self && isThreatening(p)) {
                return true;
            }
        }
        return false;
    }

    protected boolean isThreatening(Projectile projectile) {
        float dx = projectile.getX() - self.getX();
        float dy = projectile.getY() - self.getY();
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        return dist < self.getRadius() * 6;
    }

    @Override
    public void onDamageTaken(float damage, GameState gameState) {
        reactionTimer = difficulty.getReactionTime();
        reacting = true;
    }

    @Override
    public void onDamageDealt(float damage, GameState gameState) {}

    @Override
    public AIDecision getCurrentDecision() { return currentDecision; }

    @Override
    public AIDifficulty getDifficulty() { return difficulty; }

    @Override
    public String getAIName() { return getClass().getSimpleName(); }

    protected abstract AIDecision computeDecision(GameState gameState);

    protected float distanceToEnemy() {
        float dx = enemy.getX() - self.getX();
        float dy = enemy.getY() - self.getY();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    protected float angleToEnemy() {
        return (float) Math.atan2(enemy.getY() - self.getY(), enemy.getX() - self.getX());
    }

    protected boolean canSeeEnemy() {
        return true;
    }

    protected boolean isProjectileIncoming() {
        for (Projectile p : enemy.getProjectiles()) {
            if (isThreatening(p)) {
                return true;
            }
        }
        return false;
    }

    private static final float PREDICTION_TIME = 0.3f;

    protected float predictEnemyX() {
        return enemy.getX() + enemy.getVx() * PREDICTION_TIME;
    }

    protected float predictEnemyY() {
        return enemy.getY() + enemy.getVy() * PREDICTION_TIME;
    }
}
