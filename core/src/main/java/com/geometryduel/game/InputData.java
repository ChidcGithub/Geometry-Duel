package com.geometryduel.game;

/**
 * 还原 ClientInputData/ServerInputData。
 * 桌面端：方向键移动，Z 普通攻击，X 致命大招，C 传送，P 暂停。
 * 安卓端：左半屏虚拟摇杆（dx/dy 模拟量），右侧 Z/X/C 触控按钮。
 */
public class InputData {
    public float dx, dy;
    public boolean isUpPressed, isDownPressed, isLeftPressed, isRightPressed;
    public boolean isZPressed, isXPressed, isCPressed;

    /** 触控摇杆：方向向量按模长归一（对应 targetTouchMoved）。 */
    public void targetTouchMoved(float dx, float dy, float mag) {
        if (mag < 0.01f) {
            this.dx = 0;
            this.dy = 0;
        } else {
            this.dx = dx / mag;
            this.dy = dy / mag;
        }
    }

    public void clearTouch() {
        this.dx = 0;
        this.dy = 0;
    }
}
