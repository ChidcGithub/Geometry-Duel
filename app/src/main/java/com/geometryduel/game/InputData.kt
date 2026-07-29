package com.geometryduel.game

/**
 * 还原 ClientInputData/ServerInputData。
 * 键盘：方向键移动，Z 普通攻击，X 致命大招，C 传送，P 暂停。
 * 触屏：左半屏虚拟摇杆（dx/dy 模拟量），右侧 Z/X/C 触控按钮。
 */
class InputData {
    var dx = 0f
    var dy = 0f
    var isUpPressed = false
    var isDownPressed = false
    var isLeftPressed = false
    var isRightPressed = false
    var isZPressed = false
    var isXPressed = false
    var isCPressed = false

    /** 触控摇杆：方向向量按模长归一（对应 targetTouchMoved）。 */
    fun targetTouchMoved(dx: Float, dy: Float, mag: Float) {
        if (mag < 0.01f) {
            this.dx = 0f
            this.dy = 0f
        } else {
            this.dx = dx / mag
            this.dy = dy / mag
        }
    }

    fun clearTouch() {
        dx = 0f
        dy = 0f
    }
}
