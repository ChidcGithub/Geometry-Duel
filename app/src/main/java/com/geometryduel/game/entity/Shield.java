package com.geometryduel.game.entity;

public class Shield {

    public static final float SHIELD_OFFSET = 26f;

    private final Player owner;
    private final float arcDegrees;
    private final Projectile linkedProjectile;
    private boolean alive;

    public Shield(Player owner, float arcDegrees, Projectile linkedProjectile) {
        this.owner = owner;
        this.arcDegrees = arcDegrees;
        this.linkedProjectile = linkedProjectile;
        this.alive = true;
    }

    public boolean blocks(Projectile p) {
        if (!alive || p.getOwner() == owner) return false;

        float dx = p.getX() - owner.getX();
        float dy = p.getY() - owner.getY();
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float blockDist = owner.getRadius() + SHIELD_OFFSET + p.getRadius();
        if (dist > blockDist) return false;

        float ang = (float) Math.atan2(dy, dx);
        float delta = ang - owner.getFacingAngle();
        while (delta > Math.PI) delta -= 2 * Math.PI;
        while (delta < -Math.PI) delta += 2 * Math.PI;
        return Math.abs(delta) <= Math.toRadians(arcDegrees / 2f);
    }

    public void consume() { alive = false; }
    public Player getOwner() { return owner; }
    public float getArcDegrees() { return arcDegrees; }
    public Projectile getLinkedProjectile() { return linkedProjectile; }
    public boolean isAlive() { return alive; }
}
