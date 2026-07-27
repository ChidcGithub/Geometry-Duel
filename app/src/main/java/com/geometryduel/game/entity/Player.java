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
    private static final float ACCEL_RATE = 6.0f;
    private static final float DECEL_RATE = 4.0f;
    private static final float MAX_HP = 100f;
    private static final float PROJECTILE_SPEED = 1400f;
    private static final float PROJECTILE_DAMAGE = 12f;
    private static final float SLOW_DURATION = 3.0f;
    private static final float SLOW_MULTIPLIER = 0.42f;
    private static final float ANGULAR_TRACK = 12.0f;
    private static final float MAX_ANG_VEL = 10.0f;
    private static final float ANG_ACCEL = 8.0f;
    private static final float ULT_CHARGE_TIME = 3.0f;
    private static final float ULT_SPEED = 1200f;
    private static final float ULT_RADIUS = 30f;
    private static final float ULT_DAMAGE = 80f;
    private static final float ULT_COOLDOWN = 1.5f;
    private static final float SHIELD_ARC_SHOOT = 35f;
    private static final float SHIELD_ARC_ULT = 135f;

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

    private float rotationDegrees;
    private float facingAngle;
    private float angularVelocity;
    private float desiredFacingAngle;
    private Shield activeShield;
    private boolean isUltCharging;
    private float ultChargeTimer;
    private float ultCooldownTimer;

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
        this.facingAngle = playerId == 0 ? 0f : (float) Math.PI;
        this.desiredFacingAngle = this.facingAngle;
        this.isUltCharging = false;
        this.ultChargeTimer = 0f;
        this.ultCooldownTimer = 0f;
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

        if (isUltCharging) {
            ultChargeTimer += deltaTime;
            if (ultChargeTimer > ULT_CHARGE_TIME) {
                ultChargeTimer = ULT_CHARGE_TIME;
            }
        }
        if (ultCooldownTimer > 0f) {
            ultCooldownTimer -= deltaTime;
        }

        updateFacing(deltaTime);

        float moveSpeedMultiplier = getMoveSpeedMultiplier();
        float effectiveMaxSpeed = MAX_SPEED * moveSpeedMultiplier;

        if (!isDashing) {
            float dirLen = (float) Math.sqrt(moveDirX * moveDirX + moveDirY * moveDirY);
            if (dirLen > 0.001f) {
                float ndx = moveDirX / dirLen;
                float ndy = moveDirY / dirLen;
                float targetVx = ndx * effectiveMaxSpeed;
                float targetVy = ndy * effectiveMaxSpeed;
                float alpha = 1f - (float) Math.exp(-ACCEL_RATE * deltaTime);
                vx += (targetVx - vx) * alpha;
                vy += (targetVy - vy) * alpha;
            } else {
                float alpha = 1f - (float) Math.exp(-DECEL_RATE * deltaTime);
                vx += (0f - vx) * alpha;
                vy += (0f - vy) * alpha;
            }
        }

        super.update(deltaTime);

        clampToArena();

        for (Iterator<Projectile> it = projectiles.iterator(); it.hasNext(); ) {
            Projectile p = it.next();
            p.update(deltaTime);
            if (!p.isAlive()) {
                it.remove();
                if (activeShield != null && activeShield.getLinkedProjectile() == p) {
                    activeShield = null;
                }
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

        float dx = (float) Math.cos(facingAngle);
        float dy = (float) Math.sin(facingAngle);

        Projectile projectile = new Projectile(
            x + dx * (radius + 30f), y + dy * (radius + 30f),
            dx * PROJECTILE_SPEED, dy * PROJECTILE_SPEED,
            24f, 0xFF00FF00, this, PROJECTILE_DAMAGE
        );
        projectiles.add(projectile);
        activeShield = new Shield(this, SHIELD_ARC_SHOOT, projectile);
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

    public void applyKnockback(float dirX, float dirY, float force) {
        if (!alive) return;
        vx += dirX * force;
        vy += dirY * force;
    }

    private void updateFacing(float deltaTime) {
        if (!isAIControlled && (moveDirX != 0 || moveDirY != 0)) {
            desiredFacingAngle = (float) Math.atan2(moveDirY, moveDirX);
        }

        float turnMul = isUltCharging ? 0.3f : 1f;
        float delta = desiredFacingAngle - facingAngle;
        while (delta > Math.PI) delta -= 2 * Math.PI;
        while (delta < -Math.PI) delta += 2 * Math.PI;

        float maxVel = MAX_ANG_VEL * turnMul;
        float targetAngVel = delta * ANGULAR_TRACK;
        if (targetAngVel > maxVel) targetAngVel = maxVel;
        if (targetAngVel < -maxVel) targetAngVel = -maxVel;

        float angAlpha = 1f - (float) Math.exp(-ANG_ACCEL * turnMul * deltaTime);
        angularVelocity += (targetAngVel - angularVelocity) * angAlpha;
        facingAngle += angularVelocity * deltaTime;
        while (facingAngle > Math.PI) facingAngle -= 2 * Math.PI;
        while (facingAngle < -Math.PI) facingAngle += 2 * Math.PI;
    }

    public void startUltCharge() {
        if (isUltCharging || ultCooldownTimer > 0f || !alive) return;
        isUltCharging = true;
        ultChargeTimer = 0f;
    }

    public void releaseUltCharge() {
        if (!isUltCharging) return;
        isUltCharging = false;
        boolean full = ultChargeTimer >= ULT_CHARGE_TIME;
        ultChargeTimer = 0f;
        if (!full || isDashing) return;

        float dx = (float) Math.cos(facingAngle);
        float dy = (float) Math.sin(facingAngle);
        Projectile ult = new Projectile(
            x + dx * (radius + 30f), y + dy * (radius + 30f),
            dx * ULT_SPEED, dy * ULT_SPEED,
            ULT_RADIUS, 0xFFFF3B3B, this, ULT_DAMAGE
        );
        ult.setUltimate(true);
        projectiles.add(ult);
        activeShield = new Shield(this, SHIELD_ARC_ULT, ult);
        ultCooldownTimer = ULT_COOLDOWN;
        shootCooldownTimer = SHOOT_COOLDOWN;
    }

    public void cancelLinkedProjectile() {
        if (activeShield != null) {
            Projectile linked = activeShield.getLinkedProjectile();
            if (linked != null && linked.isAlive()) {
                linked.setAlive(false);
            }
            activeShield = null;
        }
    }

    public void updateVisualState(float deltaTime, Player opponent) {
        rotationDegrees = (float) Math.toDegrees(facingAngle);
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

    public Shield getActiveShield() { return activeShield; }
    public float getFacingAngle() { return facingAngle; }
    public void setDesiredFacingAngle(float angle) { this.desiredFacingAngle = angle; }
    public boolean isUltCharging() { return isUltCharging; }
    public float getUltChargeProgress() {
        return Math.min(1f, ultChargeTimer / ULT_CHARGE_TIME);
    }
    public boolean isUltFullyCharged() {
        return ultChargeTimer >= ULT_CHARGE_TIME;
    }
    public boolean isUltOnCooldown() { return ultCooldownTimer > 0f; }
}
