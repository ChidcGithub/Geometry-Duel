package com.geometryduel.game.entity;

public class Projectile extends Entity {

    private final float damage;
    private final Player owner;
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
}
