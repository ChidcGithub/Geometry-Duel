package com.geometryduel.game.ai;

public enum AIDifficulty {
    EASY(0.5f, 1.2f, 0.3f, 0.2f),
    MEDIUM(0.3f, 0.8f, 0.6f, 0.55f),
    HARD(0.15f, 0.4f, 0.85f, 0.8f);

    private final float reactionTime;
    private final float decisionInterval;
    private final float aggression;
    private final float accuracy;

    AIDifficulty(float reactionTime, float decisionInterval,
                 float aggression, float accuracy) {
        this.reactionTime = reactionTime;
        this.decisionInterval = decisionInterval;
        this.aggression = aggression;
        this.accuracy = accuracy;
    }

    public float getReactionTime() { return reactionTime; }
    public float getDecisionInterval() { return decisionInterval; }
    public float getAggression() { return aggression; }
    public float getAccuracy() { return accuracy; }
}
