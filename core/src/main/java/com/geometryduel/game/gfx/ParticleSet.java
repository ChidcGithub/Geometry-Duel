package com.geometryduel.game.gfx;

import com.badlogic.gdx.graphics.Color;
import com.geometryduel.render.Shapes;

import java.util.ArrayList;
import java.util.Iterator;

/** 粒子集合 + 建造者（还原 ParticleSet/ParticleBuilder）。 */
public class ParticleSet {
    public final ArrayList<Particle> list = new ArrayList<Particle>();
    private final Builder builder = new Builder();
    /** 无头训练模拟时置 false：所有粒子生成在 buildInto 处统一拦截。 */
    public boolean enabled = true;

    public Builder builder() {
        return builder.initialize();
    }

    /** 击杀/碎箭方块粒子（还原 addSquareParticles）。 */
    public void addSquareParticles(float x, float y, int count, float size,
                                   float speedLo, float speedHi, float lifeSec, Color color) {
        Builder b = builder().type(Particle.SQUARE).position(x, y)
                .particleSize(size).particleColor(color).lifespanSecond(lifeSec);
        for (int i = 0; i < count; i++) {
            float a = (float) (Math.random() * 6.2831855f);
            float sp = speedLo + (float) Math.random() * (speedHi - speedLo);
            b.polarVelocity(a, sp).buildInto();
        }
    }

    public void update() {
        Iterator<Particle> it = list.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.update();
            if (p.dead) it.remove();
        }
    }

    public void display(Shapes s) {
        for (int i = 0; i < list.size(); i++) list.get(i).display(s);
    }

    public void clear() {
        list.clear();
    }

    public class Builder {
        private final Particle template = new Particle();

        Builder initialize() {
            template.particleTypeNumber = 0;
            template.pos.set(0, 0);
            template.vel.set(0, 0);
            template.directionAngle = 0;
            template.speed = 0;
            template.rotationAngle = 0;
            template.displayColor.set(0, 0, 0, 1);
            template.strokeWeightValue = 1f;
            template.displaySize = 10f;
            template.lifespanFrameCount = 60;
            return this;
        }

        public Builder type(int t) { template.particleTypeNumber = t; return this; }

        public Builder position(float x, float y) { template.pos.set(x, y); return this; }

        public Builder polarVelocity(float angle, float speed) {
            template.directionAngle = angle;
            template.speed = speed;
            template.vel.set((float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed);
            return this;
        }

        public Builder rotation(float r) { template.rotationAngle = r; return this; }

        public Builder particleColor(Color c) { template.displayColor.set(c); return this; }

        public Builder weight(float w) { template.strokeWeightValue = w; return this; }

        public Builder particleSize(float sz) { template.displaySize = sz; return this; }

        public Builder lifespan(int frames) { template.lifespanFrameCount = frames; return this; }

        public Builder lifespanSecond(float sec) { return lifespan((int) (sec * 60f)); }

        public Particle buildInto() {
            if (!enabled) return null;
            Particle p = new Particle();
            p.particleTypeNumber = template.particleTypeNumber;
            p.pos.set(template.pos);
            p.vel.set(template.vel);
            p.directionAngle = template.directionAngle;
            p.speed = template.speed;
            p.rotationAngle = template.rotationAngle;
            p.displayColor.set(template.displayColor);
            p.strokeWeightValue = template.strokeWeightValue;
            p.displaySize = template.displaySize;
            p.lifespanFrameCount = template.lifespanFrameCount;
            p.properFrameCount = 0;
            list.add(p);
            return p;
        }
    }
}
