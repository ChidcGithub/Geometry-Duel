package com.geometryduel.game.ai;

import com.geometryduel.game.entity.GameState;

public interface AIController {

    void initialize(GameState gameState, int playerIndex);

    AIDecision update(float deltaTime, GameState gameState);

    void onDamageTaken(float damage, GameState gameState);

    void onDamageDealt(float damage, GameState gameState);

    AIDecision getCurrentDecision();

    String getAIName();

    AIDifficulty getDifficulty();
}
