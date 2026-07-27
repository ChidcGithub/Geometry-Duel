package com.geometryduel.game;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import com.geometryduel.game.ai.AIController;
import com.geometryduel.game.ai.AIDifficulty;
import com.geometryduel.game.ai.SimpleAI;
import com.geometryduel.game.entity.GameState;
import com.geometryduel.game.entity.Player;
import com.geometryduel.game.entity.Projectile;

import java.util.List;

public class GameEngine {

    private GameState gameState;
    private GameMode mode;
    private float arenaWidth;
    private float arenaHeight;

    private Paint player1Paint;
    private Paint player2Paint;
    private Paint projectilePaint;
    private Paint hpBarBgPaint;
    private Paint hpBarPaint;
    private Paint arenaPaint;
    private Paint textPaint;
    private Paint glowPaint;

    private RectF hpBarRect;
    private boolean running;

    public GameEngine() {
        this.gameState = new GameState(0, 0);

        player1Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        player1Paint.setColor(0xFF448AFF);
        player1Paint.setStyle(Paint.Style.FILL);

        player2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        player2Paint.setColor(0xFFFF4444);
        player2Paint.setStyle(Paint.Style.FILL);

        projectilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        projectilePaint.setColor(0xFFFFEB3B);
        projectilePaint.setStyle(Paint.Style.FILL);

        hpBarBgPaint = new Paint();
        hpBarBgPaint.setColor(0xFF333333);
        hpBarBgPaint.setStyle(Paint.Style.FILL);

        hpBarPaint = new Paint();
        hpBarPaint.setStyle(Paint.Style.FILL);

        arenaPaint = new Paint();
        arenaPaint.setColor(0xFF1A1A2E);
        arenaPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(36f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(3f);

        hpBarRect = new RectF();
        running = false;
    }

    public void initialize(GameMode mode, float arenaWidth, float arenaHeight) {
        this.mode = mode;
        this.arenaWidth = arenaWidth;
        this.arenaHeight = arenaHeight;
        this.gameState = new GameState(arenaWidth, arenaHeight);
        this.running = true;

        Player p1 = new Player(arenaWidth * 0.25f, arenaHeight * 0.5f,
            0xFF448AFF, 0, "Player", arenaWidth, arenaHeight);
        Player p2 = new Player(arenaWidth * 0.75f, arenaHeight * 0.5f,
            0xFFFF4444, 1, "Enemy", arenaWidth, arenaHeight);

        switch (mode) {
            case PLAYER_VS_AI:
                AIController enemyAI = new SimpleAI(AIDifficulty.MEDIUM);
                p2.setAiController(enemyAI);
                enemyAI.initialize(gameState, 1);
                break;
            case AI_VS_AI:
                AIController ai1 = new SimpleAI(AIDifficulty.HARD);
                AIController ai2 = new SimpleAI(AIDifficulty.MEDIUM);
                p1.setAiController(ai1);
                p2.setAiController(ai2);
                p1.setName("AI-Hard");
                p2.setName("AI-Medium");
                ai1.initialize(gameState, 0);
                ai2.initialize(gameState, 1);
                break;
        }

        gameState.setPlayer1(p1);
        gameState.setPlayer2(p2);

        gameState.start();
    }

    public void update(float deltaTime) {
        if (!running) return;

        Player p1 = gameState.getPlayer1();
        Player p2 = gameState.getPlayer2();

        if (p1 != null && p1.isAIControlled() && p1.getAIController() != null) {
            p1.getAIController().update(deltaTime, gameState);
        }
        if (p2 != null && p2.isAIControlled() && p2.getAIController() != null) {
            p2.getAIController().update(deltaTime, gameState);
        }

        gameState.update(deltaTime);
    }

    public void render(Canvas canvas) {
        canvas.drawColor(0xFF1A1A2E);

        drawArenaGrid(canvas);

        Player p1 = gameState.getPlayer1();
        Player p2 = gameState.getPlayer2();

        if (p1 != null) drawPlayer(canvas, p1);
        if (p2 != null) drawPlayer(canvas, p2);

        drawAllProjectiles(canvas);

        if (p1 != null) drawHPBar(canvas, p1, 20, 20, 300);
        if (p2 != null) drawHPBar(canvas, p2, arenaWidth - 320, 20, 300);

        drawPlayerNames(canvas);

        switch (gameState.getState()) {
            case PLAYER1_WIN:
                drawGameOverText(canvas, "Player 1 Wins!");
                break;
            case PLAYER2_WIN:
                drawGameOverText(canvas, "Player 2 Wins!");
                break;
            case DRAW:
                drawGameOverText(canvas, "Draw!");
                break;
            default:
                break;
        }
    }

    private void drawArenaGrid(Canvas canvas) {
        Paint gridPaint = new Paint();
        gridPaint.setColor(0x18FFFFFF);
        gridPaint.setStrokeWidth(1f);

        float gridSize = 80f;
        for (float x = 0; x < arenaWidth; x += gridSize) {
            canvas.drawLine(x, 0, x, arenaHeight, gridPaint);
        }
        for (float y = 0; y < arenaHeight; y += gridSize) {
            canvas.drawLine(0, y, arenaWidth, y, gridPaint);
        }

        Paint centerLinePaint = new Paint();
        centerLinePaint.setColor(0x30FFFFFF);
        centerLinePaint.setStrokeWidth(2f);
        canvas.drawLine(arenaWidth / 2, 0, arenaWidth / 2, arenaHeight, centerLinePaint);
    }

    private void drawPlayer(Canvas canvas, Player player) {
        if (!player.isAlive()) return;

        float x = player.getX();
        float y = player.getY();
        float r = player.getRadius();

        glowPaint.setColor(player.getColor());
        glowPaint.setAlpha(80);
        canvas.drawCircle(x, y, r + 6, glowPaint);

        canvas.drawCircle(x, y, r, player1Paint.getColor() == player.getColor()
            ? player1Paint : player2Paint);

        Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerPaint.setColor(0x40FFFFFF);
        innerPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x - r * 0.25f, y - r * 0.25f, r * 0.4f, innerPaint);

        if (player.isAIControlled()) {
            Paint aiLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            aiLabelPaint.setColor(0xCCFFFFFF);
            aiLabelPaint.setTextSize(16f);
            aiLabelPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("AI", x, y - r - 10, aiLabelPaint);
        }
    }

    private void drawAllProjectiles(Canvas canvas) {
        List<Projectile> projectiles = gameState.getProjectiles();
        for (Projectile p : projectiles) {
            if (!p.isAlive()) continue;

            Paint pPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (p.getOwner() != null) {
                pPaint.setColor(p.getOwner().getColor());
            } else {
                pPaint.setColor(0xFFFFEB3B);
            }
            pPaint.setStyle(Paint.Style.FILL);
            pPaint.setAlpha(200);

            canvas.drawCircle(p.getX(), p.getY(), p.getRadius(), pPaint);

            Paint glowP = new Paint(Paint.ANTI_ALIAS_FLAG);
            glowP.setColor(pPaint.getColor());
            glowP.setStyle(Paint.Style.STROKE);
            glowP.setStrokeWidth(2f);
            glowP.setAlpha(100);
            canvas.drawCircle(p.getX(), p.getY(), p.getRadius() + 3, glowP);
        }
    }

    private void drawHPBar(Canvas canvas, Player player, float left, float top, float width) {
        if (!player.isAlive()) return;

        float height = 22f;
        float hpRatio = player.getHp() / player.getMaxHp();

        hpBarRect.set(left, top, left + width, top + height);
        canvas.drawRoundRect(hpBarRect, 6, 6, hpBarBgPaint);

        hpBarRect.set(left + 2, top + 2, left + 2 + (width - 4) * hpRatio, top + height - 2);
        hpBarPaint.setColor(hpRatio > 0.5f ? 0xFF4CAF50 : (hpRatio > 0.25f ? 0xFFFFC107 : 0xFFF44336));
        canvas.drawRoundRect(hpBarRect, 4, 4, hpBarPaint);

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(0x80FFFFFF);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1.5f);
        hpBarRect.set(left, top, left + width, top + height);
        canvas.drawRoundRect(hpBarRect, 6, 6, borderPaint);
    }

    private void drawPlayerNames(Canvas canvas) {
        Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        namePaint.setTextSize(22f);
        namePaint.setTextAlign(Paint.Align.LEFT);

        Player p1 = gameState.getPlayer1();
        Player p2 = gameState.getPlayer2();

        if (p1 != null) {
            namePaint.setColor(0xFF448AFF);
            canvas.drawText(p1.getName(), 20, 60, namePaint);
        }
        if (p2 != null) {
            namePaint.setColor(0xFFFF4444);
            namePaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(p2.getName(), arenaWidth - 20, 60, namePaint);
        }
    }

    private void drawGameOverText(Canvas canvas, String text) {
        Paint overlayPaint = new Paint();
        overlayPaint.setColor(0x80000000);
        overlayPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, arenaWidth, arenaHeight, overlayPaint);

        Paint gameOverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gameOverPaint.setColor(Color.WHITE);
        gameOverPaint.setTextSize(56f);
        gameOverPaint.setTextAlign(Paint.Align.CENTER);
        gameOverPaint.setFakeBoldText(true);
        canvas.drawText(text, arenaWidth / 2, arenaHeight / 2 - 20, gameOverPaint);

        Paint restartPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        restartPaint.setColor(0xCCCCCCCC);
        restartPaint.setTextSize(28f);
        restartPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Tap to restart", arenaWidth / 2, arenaHeight / 2 + 40, restartPaint);
    }

    public void handlePlayerInput(int dx, int dy) {
        Player p1 = gameState.getPlayer1();
        if (p1 != null && !p1.isAIControlled()) {
            p1.setMoveDirection(dx, dy);
        }
    }

    public void handlePlayerShoot() {
        Player p1 = gameState.getPlayer1();
        if (p1 != null && !p1.isAIControlled()) {
            p1.shoot();
        }
    }

    public GameState getGameState() { return gameState; }
    public boolean isRunning() { return running; }

    public void restart() {
        running = false;
        initialize(mode, arenaWidth, arenaHeight);
    }

    public void dispose() {
        running = false;
    }

    public GameMode getMode() { return mode; }
}
