package com.geometryduel.neat;

import com.geometryduel.game.actor.PlayerActor;
import com.geometryduel.game.engine.PlayerEngine;

/**
 * NEAT 驱动的玩家引擎：每帧 感知 → 网络求值 → 写引擎字段。
 * 输出 5 路：moveX/moveY（连续，死区 0.2）、shot/longShot/teleport（滞回阈值：
 * >0.5 按下、<0.3 松开，防止阈值附近抖动）。
 */
public class NeatEngine extends PlayerEngine {
    private final NeatNetwork net;
    private final VisionSensor sensor;
    private final float[] inputs, outputs;

    public NeatEngine(Genome genome, int rayCount) {
        this.sensor = new VisionSensor(rayCount);
        // +1 为恒 1 偏置输入
        this.net = new NeatNetwork(genome, sensor.inputSize() + 1, 5);
        this.inputs = new float[sensor.inputSize() + 1];
        this.outputs = new float[5];
        this.inputs[this.inputs.length - 1] = 1f;
    }

    public void reset() {
        // 为未来LSTM等有状态组件预留接口
    }

    @Override
    public void run(PlayerActor player) {
        sensor.sense(player, inputs);
        net.eval(inputs, outputs);

        float mx = outputs[0], my = outputs[1];
        // 限制输出范围到[-1,1]
        mx = Math.max(-1f, Math.min(1f, mx));
        my = Math.max(-1f, Math.min(1f, my));
        if (Math.abs(mx) < 0.05f) mx = 0f;
        if (Math.abs(my) < 0.05f) my = 0f;
        operateMove(mx, my);
        operateShotButton(hysteresis(2, shotButtonPressed));
        operateLongShotButton(hysteresis(3, longShotButtonPressed));
        operateTeleportButton(hysteresis(4, teleportButtonPressed));
    }

    private boolean hysteresis(int idx, boolean current) {
        float v = outputs[idx];
        return current ? v >= 0.3f : v > 0.5f;
    }
}
