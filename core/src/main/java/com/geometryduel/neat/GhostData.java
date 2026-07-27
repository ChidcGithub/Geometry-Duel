package com.geometryduel.neat;

/**
 * 玩家行为录像（幽灵）：逐帧记录人类引擎输出——移动量量化为 ±127 的 byte，
 * 三个动作按钮打包为位掩码（bit0 短弓 / bit1 长弓 / bit2 传送）。
 * 训练时由 ReplayEngine 回放，让候选个体与「玩家的影子」对战，从而学习玩家习惯。
 */
public class GhostData {
    public static final int MAX_FRAMES = 14400; // 4 分钟上限（内存/存档体积保护）

    public int frames;
    public byte[] moveX;
    public byte[] moveY;
    public byte[] buttons;

    public float moveXAt(int f) {
        return f < frames ? moveX[f] / 127f : 0f;
    }

    public float moveYAt(int f) {
        return f < frames ? moveY[f] / 127f : 0f;
    }

    public boolean shotAt(int f) {
        return f < frames && (buttons[f] & 1) != 0;
    }

    public boolean longShotAt(int f) {
        return f < frames && (buttons[f] & 2) != 0;
    }

    public boolean teleportAt(int f) {
        return f < frames && (buttons[f] & 4) != 0;
    }

    static byte quantize(float v) {
        if (v > 1f) v = 1f;
        if (v < -1f) v = -1f;
        return (byte) Math.round(v * 127f);
    }
}
