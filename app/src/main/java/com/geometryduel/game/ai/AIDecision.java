package com.geometryduel.game.ai;

public class AIDecision {

    public enum Action {
        MOVE,
        SHOOT,
        IDLE,
        DASH
    }

    private final Action action;
    private final float targetX;
    private final float targetY;
    private final float confidence;

    private final float moveDirX;
    private final float moveDirY;

    public AIDecision(Action action, float targetX, float targetY, float confidence) {
        this.action = action;
        this.targetX = targetX;
        this.targetY = targetY;
        this.confidence = Math.max(0, Math.min(1, confidence));
        this.moveDirX = 0;
        this.moveDirY = 0;
    }

    private AIDecision(Action action, float targetX, float targetY, float confidence,
                       float moveDirX, float moveDirY) {
        this.action = action;
        this.targetX = targetX;
        this.targetY = targetY;
        this.confidence = Math.max(0, Math.min(1, confidence));
        this.moveDirX = moveDirX;
        this.moveDirY = moveDirY;
    }

    public static AIDecision idle() {
        return new AIDecision(Action.IDLE, 0, 0, 1.0f);
    }

    public static AIDecision move(float dx, float dy) {
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > 0) {
            return new AIDecision(Action.MOVE, 0, 0,
                Math.min(1.0f, len), dx / len, dy / len);
        }
        return new AIDecision(Action.MOVE, 0, 0, 0, 0, 0);
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
    public float getMoveDirX() { return moveDirX; }
    public float getMoveDirY() { return moveDirY; }

    public boolean isMovingAction() {
        return action == Action.MOVE;
    }

    @Override
    public String toString() {
        return "AIDecision{" + action + ", target=(" + targetX + "," + targetY
            + "), confidence=" + String.format("%.2f", confidence) + "}";
    }
}
