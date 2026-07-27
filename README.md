# Geometry Duel

A two-player geometric duel game for Android. Two colored circles battle on a 2D arena by shooting projectiles, dodging, and dashing. The game supports two modes: Player vs AI and AI vs AI (spectator mode).

## Modes

- **Player vs AI**: You control the blue circle on the left. The red circle is controlled by the AI. Use the on-screen d-pad and shoot button to fight.
- **AI vs AI**: Both circles are AI-controlled. Watch two AIs battle each other and compare strategies.

## AI Framework

The AI system is designed to be extensible:

| File | Role |
|------|------|
| `AIController.java` | Interface all AI implementations must fulfill |
| `AIDecision.java` | Decision data class (action, target, confidence) |
| `AIDifficulty.java` | Preset difficulty levels (EASY, MEDIUM, HARD) with tunable parameters |
| `BaseAI.java` | Abstract base with reaction time simulation, threat detection, and decision intervals |
| `SimpleAI.java` | Reference implementation: distance-based positioning, projectile dodging, strafe-and-shoot |

### Adding a New AI

Implement `AIController` or extend `BaseAI`, then assign it to a player in `GameEngine.initialize()`.

### Difficulty Parameters

| Parameter | EASY | MEDIUM | HARD |
|-----------|------|--------|------|
| Reaction time (s) | 0.5 | 0.3 | 0.15 |
| Decision interval (s) | 1.2 | 0.8 | 0.4 |
| Aggression | 0.3 | 0.6 | 0.85 |
| Accuracy | 0.2 | 0.55 | 0.8 |

## Project Structure

```
app/src/main/java/com/geometryduel/
├── ui/
│   ├── MenuActivity.java       Mode selection screen
│   └── MainActivity.java       Fullscreen game activity
└── game/
    ├── GameMode.java            PLAYER_VS_AI / AI_VS_AI enum
    ├── GameEngine.java          Core engine: update, render, collision
    ├── GameView.java            SurfaceView game loop with touch controls
    ├── entity/
    │   ├── Entity.java          Base class (position, velocity, collision)
    │   ├── Player.java          Player (movement, shooting, dash, HP)
    │   ├── Projectile.java      Projectile fired by players
    │   └── GameState.java       Game state and event management
    └── ai/
        ├── AIController.java    AI interface
        ├── AIDecision.java      Decision data class
        ├── AIDifficulty.java    Difficulty presets
        ├── BaseAI.java          Abstract AI base class
        └── SimpleAI.java        Simple AI implementation
```

## Build

Open the project in Android Studio and run, or build from command line:

```
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Controls

- **Left side**: Virtual d-pad for movement
- **Right side**: Shoot button (fires toward the enemy)
- **Tap anywhere on game-over screen**: Restart the match
