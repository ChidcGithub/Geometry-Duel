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
    private static final float PROJECTILE_SPEED = 500f;
    private static final float PROJECTILE_DAMAGE = 12f;

    private float hp;
    private float maxHp;
    private float shootCooldownTimer;
    private float dashTimer;
    private float dashCooldownTimer;
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

    private final float arenaWidth;
    private final float arenaHeight;

    public Player(float x, float y, int color, int playerId, String name,
                  float arenaWidth, float arenaHeight) {
        super(x, y, 25f, color);
        this.playerId = playerId;
        this.name = name;
        this.hp = MAX_HP;
        this.maxHp = MAX_HP;
        this.projectiles = new ArrayList<>();
        this.isAIControlled = false;
        this.shootCooldownTimer = 0;
        this.dashTimer = 0;
        this.dashCooldownTimer = 0;
        this.isDashing = false;
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

        applyAIInput(deltaTime);

        if (!isDashing && (moveDirX != 0 || moveDirY != 0)) {
            vx += moveDirX * MAX_SPEED * 6f * deltaTime;
            vy += moveDirY * MAX_SPEED * 6f * deltaTime;

            float currentSpeed = (float) Math.sqrt(vx * vx + vy * vy);
            if (currentSpeed > MAX_SPEED) {
                vx = vx / currentSpeed * MAX_SPEED;
                vy = vy / currentSpeed * MAX_SPEED;
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
                vx = dirX / len * DASH_SPEED;
                vy = dirY / len * DASH_SPEED;
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
            6f, 0xFF00FF00, this, PROJECTILE_DAMAGE
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
        vx = dirX * DASH_SPEED;
        vy = dirY * DASH_SPEED;
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
}
