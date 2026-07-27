package com.geometryduel.game.entity;

import java.util.Random;

public class ExplosionEffect {

    private static final int PARTICLE_COUNT = 50;
    private static final float MAX_LIFETIME = 0.65f;
    private static final float DRAG = 0.9f;

    private final float[] particleX = new float[PARTICLE_COUNT];
    private final float[] particleY = new float[PARTICLE_COUNT];
    private final float[] velocityX = new float[PARTICLE_COUNT];
    private final float[] velocityY = new float[PARTICLE_COUNT];
    private final float[] size = new float[PARTICLE_COUNT];
    private final int color;

    private float lifetime;
    private boolean alive;

    public ExplosionEffect(float centerX, float centerY, int color) {
        this.color = color;
        this.lifetime = MAX_LIFETIME;
        this.alive = true;

        Random random = new Random((long) (centerX * 31f + centerY * 17f + color));
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            float angle = (float) (Math.PI * 2 * i / PARTICLE_COUNT);
            angle += (random.nextFloat() - 0.5f) * 0.22f;

            float speed = 120f + random.nextFloat() * 340f;
            particleX[i] = centerX;
            particleY[i] = centerY;
            velocityX[i] = (float) Math.cos(angle) * speed;
            velocityY[i] = (float) Math.sin(angle) * speed;
            size[i] = 4f + random.nextFloat() * 6f;
        }
    }

    public void update(float deltaTime) {
        if (!alive) {
            return;
        }

        lifetime -= deltaTime;
        if (lifetime <= 0f) {
            lifetime = 0f;
            alive = false;
            return;
        }

        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particleX[i] += velocityX[i] * deltaTime;
            particleY[i] += velocityY[i] * deltaTime;
            velocityX[i] *= DRAG;
            velocityY[i] *= DRAG;
        }
    }

    public int getParticleCount() { return PARTICLE_COUNT; }
    public float getParticleX(int index) { return particleX[index]; }
    public float getParticleY(int index) { return particleY[index]; }
    public float getParticleSize(int index) { return size[index]; }
    public int getColor() { return color; }
    public float getLifeRatio() { return lifetime / MAX_LIFETIME; }
    public boolean isAlive() { return alive; }
}
