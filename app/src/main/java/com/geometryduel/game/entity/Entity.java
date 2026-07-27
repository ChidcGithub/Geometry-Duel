package com.geometryduel.game.entity;

public abstract class Entity {
    protected float x, y;
    protected float vx, vy;
    protected float radius;
    protected int color;
    protected boolean alive;

    public Entity(float x, float y, float radius, int color) {
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.color = color;
        this.vx = 0;
        this.vy = 0;
        this.alive = true;
    }

    public void update(float deltaTime) {
        x += vx * deltaTime;
        y += vy * deltaTime;
    }

    public boolean collidesWith(Entity other) {
        float dx = x - other.x;
        float dy = y - other.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        return dist < (radius + other.radius);
    }

    public boolean isOutOfBounds(float arenaWidth, float arenaHeight) {
        return x < -radius || x > arenaWidth + radius
            || y < -radius || y > arenaHeight + radius;
    }

    public float distanceTo(Entity other) {
        float dx = x - other.x;
        float dy = y - other.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public float getX() { return x; }
    public void setX(float x) { this.x = x; }
    public float getY() { return y; }
    public void setY(float y) { this.y = y; }
    public float getVx() { return vx; }
    public void setVx(float vx) { this.vx = vx; }
    public float getVy() { return vy; }
    public void setVy(float vy) { this.vy = vy; }
    public float getRadius() { return radius; }
    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }
    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }
}
