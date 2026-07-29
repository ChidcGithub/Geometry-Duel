package com.geometryduel.game.state;

import com.geometryduel.game.GameSystem;
import com.geometryduel.render.Shapes;

/**
 * 开局倒计时状态（还原 ClientStartGameState）：
 * - 3→2→1，每个数字 60 帧，共 180 帧
 * - 中央半径 100 的进度圆环（线宽 3，ring 色）
 * - 结束时在 (320,320) 生成半径 200 的扩散环粒子（1s）并进入对战
 */
public class StartGameState extends GameSystemState {
    public static final int FRAMES_PER_NUMBER = 60;
    public static final float RING_RADIUS = 100f;

    public StartGameState(GameSystem system) {
        super(system);
        system.stateIndex = 1;
    }

    @Override
    protected void updateSystem() {
        system.myGroup.update();
        system.otherGroup.update();
    }

    @Override
    protected void checkStateTransition() {
        if (properFrameCount >= FRAMES_PER_NUMBER * 3) {
            system.particles.builder()
                    .type(3).position(320f, 320f)
                    .polarVelocity(0, 0)
                    .particleSize(200f)
                    .particleColor(system.theme().ring)
                    .weight(5f).lifespanSecond(1f)
                    .buildInto();
            system.currentState(new PlayGameState(system));
        }
    }

    /** 当前应显示的数字（3→2→1，结束后为 0）。 */
    public int displayNumber() {
        return 3 - properFrameCount / FRAMES_PER_NUMBER;
    }

    /** 当前秒内的环进度 [0,1]。 */
    public float ringProgress() {
        return (properFrameCount % FRAMES_PER_NUMBER) / (float) FRAMES_PER_NUMBER;
    }

    @Override
    public void display(Shapes s) {
        system.myGroup.displayPlayers(s);
        system.otherGroup.displayPlayers(s);
        s.push();
        s.translate(320f, 320f);
        s.noFill();
        s.stroke(system.theme().ring);
        s.strokeWeight(3f);
        s.arc(0, 0, RING_RADIUS, 90f, ringProgress() * 360f);
        s.pop();
    }
}
