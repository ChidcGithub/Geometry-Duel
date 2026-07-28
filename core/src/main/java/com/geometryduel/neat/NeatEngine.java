package com.geometryduel.neat;

import com.geometryduel.game.actor.PlayerActor;
import com.geometryduel.game.engine.PlayerEngine;

/**
 * NEAT 驱动的玩家引擎：每 3 帧推理一次（20 Hz），跳过帧复用上次决策。
 * 输出 5 路：moveX/moveY（连续，死区 0.05）、shot（>0.5/0.3）、
 * longShot（>0.3/0.15 低门槛鼓励探索大招）、teleport（>0.5/0.3）。
 */
public class NeatEngine extends PlayerEngine {
    private int skipFrames = 2;  // 可调：1=30Hz, 2=20Hz(default), 3=15Hz, 5=12Hz

    private NeatNetwork net;
    private VisionSensor sensor;
    private float[] inputs;
    private final float[] outputs;
    private int skipCounter;
    private int engineRayCount;
    private Genome currentGenome;
    /** 网络构建缓存（identity 命中即复用）：同代对战双方交替、冠军切换时避免重复拓扑排序+可达性矩阵。 */
    private final Genome[] cacheGenomes = new Genome[4];
    private final NeatNetwork[] cacheNets = new NeatNetwork[4];
    private int cacheNext;

    public NeatEngine(Genome genome, int rayCount) {
        this.engineRayCount = rayCount;
        this.sensor = new VisionSensor(rayCount);
        this.net = new NeatNetwork(genome, sensor.inputSize() + 1, 5);
        this.currentGenome = genome;
        this.inputs = new float[sensor.inputSize() + 1];
        this.outputs = new float[5];
        this.inputs[this.inputs.length - 1] = 1f;
    }

    public void reset() {
        skipCounter = 0;
        net.reset();
    }

    public int rayCount() { return engineRayCount; }
    /** 当前网络对应的基因组（身份比较，供调用方跳过重复构建）。 */
    public Genome genome() { return currentGenome; }

    public void setSkipFrames(int n) { this.skipFrames = n; }
    public int skipFrames() { return skipFrames; }

    /** 换基因组重建网络（缓存命中则复用），射线数变时重新分配数组并清空缓存 */
    public void setGenome(Genome genome, int rayCount) {
        if (this.engineRayCount != rayCount) {
            this.engineRayCount = rayCount;
            this.sensor = new VisionSensor(rayCount);
            this.inputs = new float[sensor.inputSize() + 1];
            this.inputs[this.inputs.length - 1] = 1f;
            for (int i = 0; i < cacheGenomes.length; i++) { cacheGenomes[i] = null; cacheNets[i] = null; }
            cacheNext = 0;
        }
        for (int i = 0; i < cacheGenomes.length; i++) {
            if (cacheGenomes[i] == genome) {
                this.net = cacheNets[i];
                this.currentGenome = genome;
                this.skipCounter = 0;
                return;
            }
        }
        NeatNetwork built = new NeatNetwork(genome, sensor.inputSize() + 1, 5);
        cacheGenomes[cacheNext] = genome;
        cacheNets[cacheNext] = built;
        cacheNext = (cacheNext + 1) % cacheGenomes.length;
        this.net = built;
        this.currentGenome = genome;
        this.skipCounter = 0;
    }

    @Override
    public void run(PlayerActor player) {
        if (skipCounter > 0) {
            skipCounter--;
            return;
        }
        skipCounter = skipFrames;

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
