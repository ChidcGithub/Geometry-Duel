package com.geometryduel.game.ai;

public class AIDecision {

    public enum Action {
        MOVE_LEFT,
        MOVE_RIGHT,
        MOVE_UP,
        MOVE_DOWN,
        SHOOT,
        IDLE,
        DASH
    }

    private final Action action;
    private final float targetX;
    private final float targetY;
    private final float confidence;

    public AIDecision(Action action, float targetX, float targetY, float confidence) {
        this.action = action;
        this.targetX = targetX;
        this.targetY = targetY;
        this.confidence = Math.max(0, Math.min(1, confidence));
    }

    public static AIDecision idle() {
        return new AIDecision(Action.IDLE, 0, 0, 1.0f);
    }

    public static AIDecision move(float dx, float dy) {
        Action action;
        if (Math.abs(dx) > Math.abs(dy)) {
            action = dx > 0 ? Action.MOVE_RIGHT : Action.MOVE_LEFT;
        } else {
            action = dy > 0 ? Action.MOVE_DOWN : Action.MOVE_UP;
        }
        return new AIDecision(action, dx, dy, Math.min(1.0f, Math.abs(dx) + Math.abs(dy)));
    }

    public static AIDecision shoot(float targetX, float targetY, float accuracy) {
        return new AIDecision(Action.SHOOT, targetX, targetY, accuracy);
    }

    public static AIDecision dash(float dirX, float dirY) {
        return new AIDecision(Action.DASH, dirX, dirY, 0.8f);
    }

    public Action getAction() { return action; }
    public float getTargetX() { return targetX; }
    public float getTargetY() { return targetY; }
    public float getConfidence() { return confidence; }

    public boolean isMovingAction() {
        return action == Action.MOVE_LEFT || action == Action.MOVE_RIGHT
            || action == Action.MOVE_UP || action == Action.MOVE_DOWN;
    }

    @Override
    public String toString() {
        return "AIDecision{" + action + ", target=(" + targetX + "," + targetY
            + "), confidence=" + String.format("%.2f", confidence) + "}";
    }
}
