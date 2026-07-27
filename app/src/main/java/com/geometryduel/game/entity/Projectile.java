package com.geometryduel.game.entity;

public class Projectile extends Entity {

    private final float damage;
    private final Player owner;
    private final float directionX;
    private final float directionY;
    private float lifetime;
    private static final float MAX_LIFETIME = 3.0f;

    public Projectile(float x, float y, float vx, float vy, float radius,
                      int color, Player owner, float damage) {
        super(x, y, radius, color);
        this.vx = vx;
        this.vy = vy;
        this.damage = damage;
        this.owner = owner;
        this.lifetime = MAX_LIFETIME;

        float speed = (float) Math.sqrt(vx * vx + vy * vy);
        if (speed > 0f) {
            this.directionX = vx / speed;
            this.directionY = vy / speed;
        } else {
            this.directionX = 1f;
            this.directionY = 0f;
        }
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        lifetime -= deltaTime;
        if (lifetime <= 0) {
            alive = false;
        }
    }

    public float getDamage() { return damage; }
    public Player getOwner() { return owner; }
    public float getLifetime() { return lifetime; }
    public float getDirectionX() { return directionX; }
    public float getDirectionY() { return directionY; }
}
