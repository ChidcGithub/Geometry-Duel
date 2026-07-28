package com.geometryduel.neat;

import com.geometryduel.game.actor.PlayerActor;
import com.geometryduel.game.engine.PlayerEngine;

/**
 * NEAT 驱动的玩家引擎：每 3 帧推理一次（20 Hz），跳过帧复用上次决策。
 * 输出 5 路：moveX/moveY（连续，死区 0.05）、shot（>0.5/0.3）、
 * longShot（>0.3/0.15 低门槛鼓励探索大招）、teleport（>0.5/0.3）。
 */
public class NeatEngine extends PlayerEngine {
    private static final int SKIP_FRAMES = 2;

    private final NeatNetwork net;
    private final VisionSensor sensor;
    private final float[] inputs, outputs;
    private int skipCounter;

    public NeatEngine(Genome genome, int rayCount) {
        this.sensor = new VisionSensor(rayCount);
        this.net = new NeatNetwork(genome, sensor.inputSize() + 1, 5);
        this.inputs = new float[sensor.inputSize() + 1];
        this.outputs = new float[5];
        this.inputs[this.inputs.length - 1] = 1f;
    }

    public void reset() {
        skipCounter = 0;
        net.reset();
    }

    @Override
    public void run(PlayerActor player) {
        if (skipCounter > 0) {
            skipCounter--;
            return;
        }
        skipCounter = SKIP_FRAMES;

        sensor.sense(player, inputs);
        net.eval(inputs, outputs);

        float mx = outputs[0], my = outputs[1];
        mx = Math.max(-1f, Math.min(1f, mx));
        my = Math.max(-1f, Math.min(1f, my));
        if (Math.abs(mx) < 0.05f) mx = 0f;
        if (Math.abs(my) < 0.05f) my = 0f;
        operateMove(mx, my);
        operateShotButton(hysteresis(outputs[2], 0.5f, 0.3f, shotButtonPressed));
        operateLongShotButton(hysteresis(outputs[3], 0.3f, 0.15f, longShotButtonPressed));
        operateTeleportButton(hysteresis(outputs[4], 0.5f, 0.3f, teleportButtonPressed));
    }

    private static boolean hysteresis(float v, float onThresh, float offThresh, boolean current) {
        return current ? v >= offThresh : v > onThresh;
    }
}
