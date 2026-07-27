package com.geometryduel.game.entity;

import com.geometryduel.game.ai.AIController;
import com.geometryduel.game.ai.AIDecision;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Player extends Entity {

    private static final float MAX_SPEED = 280f;
    private static final float DASH_SPEED = 800f;
    private static final float DASH_DURATION = 0.15f;
    private static final float DASH_COOLDOWN = 2.0f;
    private static final float SHOOT_COOLDOWN = 0.35f;
    private static final float FRICTION = 0.92f;
    private static final float MAX_HP = 100f;
    private static final float PROJECTILE_SPEED = 920f;
    private static final float PROJECTILE_DAMAGE = 12f;
    private static final float SLOW_DURATION = 3.0f;
    private static final float SLOW_MULTIPLIER = 0.42f;
    private static final float ROTATION_IDLE_RETURN = 7.0f;
    private static final float ROTATION_SPIN_BASE = 180f;
    private static final float ROTATION_SPIN_BOOST = 640f;

    private float hp;
    private float maxHp;
    private float shootCooldownTimer;
    private float dashTimer;
    private float dashCooldownTimer;
    private float slowTimer;
    private boolean isDashing;
    private int playerId;
    private String name;

    private final List<Projectile> projectiles;

    private AIController aiController;
    private boolean isAIControlled;
    private int moveDirX;
    private int moveDirY;
    private boolean wantsShoot;
    private boolean wantsDash;

    private float shootTargetX;
    private float shootTargetY;
    private float rotationDegrees;

    private final float arenaWidth;
    private final float arenaHeight;

    public Player(float x, float y, int color, int playerId, String name,
                  float arenaWidth, float arenaHeight) {
        super(x, y, 50f, color);
        this.playerId = playerId;
        this.name = name;
        this.hp = MAX_HP;
        this.maxHp = MAX_HP;
        this.projectiles = new ArrayList<>();
        this.isAIControlled = false;
        this.shootCooldownTimer = 0;
        this.dashTimer = 0;
        this.dashCooldownTimer = 0;
        this.slowTimer = 0;
        this.isDashing = false;
        this.rotationDegrees = 0f;
        this.arenaWidth = arenaWidth;
        this.arenaHeight = arenaHeight;
    }

    @Override
    public void update(float deltaTime) {
        if (!alive) return;

        if (isDashing) {
            dashTimer -= deltaTime;
            if (dashTimer <= 0) {
                isDashing = false;
                vx *= 0.3f;
                vy *= 0.3f;
            }
        } else {
            vx *= FRICTION;
            vy *= FRICTION;
        }

        if (dashCooldownTimer > 0) {
            dashCooldownTimer -= deltaTime;
        }

        if (shootCooldownTimer > 0) {
            shootCooldownTimer -= deltaTime;
        }

        if (slowTimer > 0f) {
            slowTimer -= deltaTime;
            if (slowTimer < 0f) {
                slowTimer = 0f;
            }
        }

        applyAIInput(deltaTime);

        float moveSpeedMultiplier = getMoveSpeedMultiplier();
        float effectiveMaxSpeed = MAX_SPEED * moveSpeedMultiplier;
        if (!isDashing && (moveDirX != 0 || moveDirY != 0)) {
            vx += moveDirX * effectiveMaxSpeed * 6f * deltaTime;
            vy += moveDirY * effectiveMaxSpeed * 6f * deltaTime;

            float currentSpeed = (float) Math.sqrt(vx * vx + vy * vy);
            if (currentSpeed > effectiveMaxSpeed) {
                vx = vx / currentSpeed * effectiveMaxSpeed;
                vy = vy / currentSpeed * effectiveMaxSpeed;
            }
        }

        super.update(deltaTime);

        clampToArena();

        for (Iterator<Projectile> it = projectiles.iterator(); it.hasNext(); ) {
            Projectile p = it.next();
            p.update(deltaTime);
            if (!p.isAlive()) {
                it.remove();
            }
        }

        if (wantsShoot && canShoot()) {
            shoot();
            wantsShoot = false;
        }
    }

    private void applyAIInput(float deltaTime) {
        if (!isAIControlled || aiController == null) return;

        moveDirX = 0;
        moveDirY = 0;
        wantsShoot = false;
        wantsDash = false;

        AIDecision decision = aiController.getCurrentDecision();
        if (decision == null) return;

        switch (decision.getAction()) {
            case MOVE:
                moveDirX = Math.round(decision.getMoveDirX());
                moveDirY = Math.round(decision.getMoveDirY());
                break;
            case SHOOT:
                wantsShoot = true;
                break;
            case DASH:
                wantsDash = true;
                break;
            case IDLE:
            default:
                break;
        }

        if (wantsDash && canDash()) {
            float dirX = decision.getTargetX();
            float dirY = decision.getTargetY();
            float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
            if (len > 0) {
                float dashSpeed = DASH_SPEED * Math.max(0.6f, getMoveSpeedMultiplier());
                vx = dirX / len * dashSpeed;
                vy = dirY / len * dashSpeed;
            }
            isDashing = true;
            dashTimer = DASH_DURATION;
            dashCooldownTimer = DASH_COOLDOWN;
            wantsDash = false;
        }
    }

    public void setMoveDirection(int dx, int dy) {
        if (isAIControlled) return;
        moveDirX = dx;
        moveDirY = dy;
    }

    public void shoot() {
        if (!canShoot()) return;

        float targetX, targetY;
        if (isAIControlled && aiController != null) {
            AIDecision decision = aiController.getCurrentDecision();
            if (decision != null) {
                targetX = decision.getTargetX();
                targetY = decision.getTargetY();
            } else {
                targetX = x + 100;
                targetY = y;
            }
        } else {
            targetX = shootTargetX;
            targetY = shootTargetY;
        }

        float dx = targetX - x;
        float dy = targetY - y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist > 0) {
            dx /= dist;
            dy /= dist;
        } else {
            dx = 1;
            dy = 0;
        }

        Projectile projectile = new Projectile(
            x + dx * (radius + 5), y + dy * (radius + 5),
            dx * PROJECTILE_SPEED, dy * PROJECTILE_SPEED,
            12f, 0xFF00FF00, this, PROJECTILE_DAMAGE
        );
        projectiles.add(projectile);
        shootCooldownTimer = SHOOT_COOLDOWN;
    }

    public boolean canShoot() {
        return shootCooldownTimer <= 0 && !isDashing;
    }

    public boolean canDash() {
        return dashCooldownTimer <= 0 && !isDashing;
    }

    public void triggerDash() {
        if (!canDash()) return;
        float dirX = moveDirX != 0 ? moveDirX : (playerId == 0 ? 1 : -1);
        float dirY = moveDirY;
        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        if (len > 0) {
            dirX /= len;
            dirY /= len;
        }
        float dashSpeed = DASH_SPEED * Math.max(0.6f, getMoveSpeedMultiplier());
        vx = dirX * dashSpeed;
        vy = dirY * dashSpeed;
        isDashing = true;
        dashTimer = DASH_DURATION;
        dashCooldownTimer = DASH_COOLDOWN;
    }

    public void setShootTarget(float x, float y) {
        this.shootTargetX = x;
        this.shootTargetY = y;
    }

    public void takeDamage(float damage) {
        if (!alive) return;
        hp -= damage;
        if (hp <= 0) {
            hp = 0;
            alive = false;
        }
    }

    public void applySlow() {
        slowTimer = SLOW_DURATION;
    }

    public void updateVisualState(float deltaTime, Player opponent) {
        if (!alive || opponent == null) {
            return;
        }

        float toOpponentX = opponent.getX() - x;
        float toOpponentY = opponent.getY() - y;
        float dist = (float) Math.sqrt(toOpponentX * toOpponentX + toOpponentY * toOpponentY);

        if (dist <= 0.001f) {
            rotationDegrees *= Math.max(0f, 1f - deltaTime * ROTATION_IDLE_RETURN);
            return;
        }

        float tangentX = -toOpponentY / dist;
        float tangentY = toOpponentX / dist;
        float lateralSpeed = vx * tangentX + vy * tangentY;
        float speed = (float) Math.sqrt(vx * vx + vy * vy);
        float normalizedSpeed = Math.min(1f, speed / DASH_SPEED);
        float normalizedLateral = Math.min(1f, Math.abs(lateralSpeed) / DASH_SPEED);

        if (normalizedSpeed < 0.08f && !isDashing) {
            rotationDegrees *= Math.max(0f, 1f - deltaTime * ROTATION_IDLE_RETURN);
            return;
        }

        float spinDirection = lateralSpeed == 0f ? 0f : Math.signum(lateralSpeed);
        if (spinDirection == 0f && speed > 0f) {
            spinDirection = Math.signum(vy == 0f ? vx : vy);
        }

        float nonlinearSpin = ROTATION_SPIN_BASE
            + ROTATION_SPIN_BOOST * (float) Math.pow(normalizedSpeed, 1.75f)
            * (0.35f + (float) Math.pow(Math.max(normalizedLateral, 0.2f), 1.45f));

        if (isDashing) {
            nonlinearSpin *= 1.25f;
        }

        rotationDegrees += spinDirection * nonlinearSpin * deltaTime;
    }

    private float getMoveSpeedMultiplier() {
        return slowTimer > 0f ? SLOW_MULTIPLIER : 1f;
    }

    private void clampToArena() {
        float margin = radius + 5;
        if (x < margin) x = margin;
        if (x > arenaWidth - margin) x = arenaWidth - margin;
        if (y < margin) y = margin;
        if (y > arenaHeight - margin) y = arenaHeight - margin;
    }

    public List<Projectile> getProjectiles() { return projectiles; }
    public float getHp() { return hp; }
    public float getMaxHp() { return maxHp; }
    public int getPlayerId() { return playerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isAIControlled() { return isAIControlled; }
    public AIController getAiController() { return aiController; }

    public void setAiController(AIController controller) {
        this.aiController = controller;
        this.isAIControlled = (controller != null);
    }

    public AIController getAIController() { return aiController; }
    public int getMoveDirX() { return moveDirX; }
    public int getMoveDirY() { return moveDirY; }
    public float getRotationDegrees() { return rotationDegrees; }
    public boolean isSlowed() { return slowTimer > 0f; }
    public float getSlowRatio() { return Math.min(1f, slowTimer / SLOW_DURATION); }
}
