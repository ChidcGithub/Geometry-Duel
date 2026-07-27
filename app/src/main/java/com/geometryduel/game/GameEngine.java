package com.geometryduel.game;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

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

    private final int COLOR_BG = 0xFF0D0D0D;
    private final int COLOR_P1_FILL = 0xFFFFFFFF;
    private final int COLOR_P1_STROKE = 0xFF888888;
    private final int COLOR_P2_FILL = 0xFF1C1C1E;
    private final int COLOR_P2_STROKE = 0xFFFFFFFF;
    private final int COLOR_GRID = 0x14FFFFFF;
    private final int COLOR_CENTER = 0x26FFFFFF;
    private final int COLOR_HP_BG = 0xFF2A2A2D;
    private final int COLOR_HP_FILL = 0xFFFFFFFF;
    private final int COLOR_HP_LOW = 0xFF666666;
    private final int COLOR_TEXT = 0xFFFFFFFF;
    private final int COLOR_TEXT_DIM = 0x80FFFFFF;
    private final int COLOR_OVERLAY = 0xE6000000;

    private Paint p1FillPaint;
    private Paint p1StrokePaint;
    private Paint p2FillPaint;
    private Paint p2StrokePaint;
    private Paint projectilePaint;
    private Paint hpBgPaint;
    private Paint hpFillPaint;
    private Paint textPaint;
    private Paint textDimPaint;
    private Paint textLargePaint;
    private Paint gridPaint;
    private Paint centerPaint;
    private Paint overlayPaint;
    private Paint separatorPaint;

    private RectF hpRect;
    private boolean running;

    public GameEngine() {
        this.gameState = new GameState(0, 0);

        p1FillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        p1FillPaint.setColor(COLOR_P1_FILL);
        p1FillPaint.setStyle(Paint.Style.FILL);

        p1StrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        p1StrokePaint.setColor(COLOR_P1_STROKE);
        p1StrokePaint.setStyle(Paint.Style.STROKE);
        p1StrokePaint.setStrokeWidth(2f);

        p2FillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        p2FillPaint.setColor(COLOR_P2_FILL);
        p2FillPaint.setStyle(Paint.Style.FILL);

        p2StrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        p2StrokePaint.setColor(COLOR_P2_STROKE);
        p2StrokePaint.setStyle(Paint.Style.STROKE);
        p2StrokePaint.setStrokeWidth(2f);

        projectilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        projectilePaint.setStyle(Paint.Style.FILL);

        hpBgPaint = new Paint();
        hpBgPaint.setColor(COLOR_HP_BG);
        hpBgPaint.setStyle(Paint.Style.FILL);

        hpFillPaint = new Paint();
        hpFillPaint.setColor(COLOR_HP_FILL);
        hpFillPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(COLOR_TEXT);
        textPaint.setTextSize(28f);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        textDimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textDimPaint.setColor(COLOR_TEXT_DIM);
        textDimPaint.setTextSize(22f);

        textLargePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textLargePaint.setColor(COLOR_TEXT);
        textLargePaint.setTextSize(52f);
        textLargePaint.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));

        gridPaint = new Paint();
        gridPaint.setColor(COLOR_GRID);
        gridPaint.setStrokeWidth(0.5f);

        centerPaint = new Paint();
        centerPaint.setColor(COLOR_CENTER);
        centerPaint.setStrokeWidth(1f);

        overlayPaint = new Paint();
        overlayPaint.setColor(COLOR_OVERLAY);
        overlayPaint.setStyle(Paint.Style.FILL);

        separatorPaint = new Paint();
        separatorPaint.setColor(COLOR_CENTER);
        separatorPaint.setStrokeWidth(2f);

        hpRect = new RectF();
        running = false;
    }

    public void initialize(GameMode mode, float arenaWidth, float arenaHeight) {
        this.mode = mode;
        this.arenaWidth = arenaWidth;
        this.arenaHeight = arenaHeight;
        this.gameState = new GameState(arenaWidth, arenaHeight);
        this.running = true;

        int p1Color = 0xFFFFFFFF;
        int p2Color = 0xFF4A4A4D;

        Player p1 = new Player(arenaWidth * 0.25f, arenaHeight * 0.5f,
            p1Color, 0, "PLAYER", arenaWidth, arenaHeight);
        Player p2 = new Player(arenaWidth * 0.75f, arenaHeight * 0.5f,
            p2Color, 1, "ENEMY", arenaWidth, arenaHeight);

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
                p1.setName("AI — HARD");
                p2.setName("AI — MEDIUM");
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
        canvas.drawColor(COLOR_BG);

        drawArenaGrid(canvas);

        Player p1 = gameState.getPlayer1();
        Player p2 = gameState.getPlayer2();

        if (p1 != null) drawPlayer(canvas, p1, true);
        if (p2 != null) drawPlayer(canvas, p2, false);

        drawAllProjectiles(canvas);

        float barW = Math.min(280f, arenaWidth * 0.35f);
        if (p1 != null) drawHPBar(canvas, p1, 24, 24, barW, true);
        if (p2 != null) drawHPBar(canvas, p2, arenaWidth - barW - 24, 24, barW, false);

        drawPlayerNames(canvas);

        GameState.State state = gameState.getState();
        if (state == GameState.State.PLAYER1_WIN || state == GameState.State.PLAYER2_WIN
            || state == GameState.State.DRAW) {
            drawGameOverOverlay(canvas, state);
        }
    }

    private void drawArenaGrid(Canvas canvas) {
        float gridSize = 100f;
        for (float x = 0; x < arenaWidth; x += gridSize) {
            canvas.drawLine(x, 0, x, arenaHeight, gridPaint);
        }
        for (float y = 0; y < arenaHeight; y += gridSize) {
            canvas.drawLine(0, y, arenaWidth, y, gridPaint);
        }

        canvas.drawLine(arenaWidth / 2, 0, arenaWidth / 2, arenaHeight, centerPaint);
    }

    private void drawPlayer(Canvas canvas, Player player, boolean isP1) {
        if (!player.isAlive()) return;

        float x = player.getX();
        float y = player.getY();
        float r = player.getRadius();

        if (isP1) {
            canvas.drawCircle(x, y, r, p1FillPaint);
            canvas.drawCircle(x, y, r, p1StrokePaint);
        } else {
            canvas.drawCircle(x, y, r, p2FillPaint);
            canvas.drawCircle(x, y, r, p2StrokePaint);
        }

        float innerR = r * 0.3f;
        Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerPaint.setStyle(Paint.Style.FILL);
        if (isP1) {
            innerPaint.setColor(COLOR_P1_STROKE);
        } else {
            innerPaint.setColor(COLOR_P2_STROKE);
        }
        canvas.drawCircle(x - r * 0.2f, y - r * 0.2f, innerR, innerPaint);

        if (player.isAIControlled()) {
            Paint aiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            aiPaint.setColor(COLOR_TEXT_DIM);
            aiPaint.setTextSize(14f);
            aiPaint.setTextAlign(Paint.Align.CENTER);
            aiPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            canvas.drawText("AI", x, y - r - 12, aiPaint);
        }
    }

    private void drawAllProjectiles(Canvas canvas) {
        List<Projectile> projectiles = gameState.getProjectiles();
        for (Projectile p : projectiles) {
            if (!p.isAlive()) continue;

            projectilePaint.setColor(p.getOwner().getPlayerId() == 0
                ? 0xFFE0E0E0 : 0xFF444444);
            projectilePaint.setAlpha(240);

            float px = p.getX();
            float py = p.getY();
            float pr = p.getRadius();
            canvas.drawCircle(px, py, pr, projectilePaint);

            projectilePaint.setStyle(Paint.Style.STROKE);
            projectilePaint.setStrokeWidth(1f);
            projectilePaint.setAlpha(120);
            canvas.drawCircle(px, py, pr + 2, projectilePaint);
            projectilePaint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawHPBar(Canvas canvas, Player player, float left, float top,
                           float width, boolean isP1) {
        float height = 18f;
        float hpRatio = Math.max(0, player.getHp() / player.getMaxHp());

        hpRect.set(left, top, left + width, top + height);
        canvas.drawRect(hpRect, hpBgPaint);

        float fillW = width * hpRatio;
        hpRect.set(left, top, left + fillW, top + height);
        hpFillPaint.setColor(hpRatio > 0.3f ? COLOR_HP_FILL : COLOR_HP_LOW);
        canvas.drawRect(hpRect, hpFillPaint);

        Paint borderP = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderP.setColor(isP1 ? COLOR_P1_STROKE : COLOR_P2_STROKE);
        borderP.setStyle(Paint.Style.STROKE);
        borderP.setStrokeWidth(1.5f);
        hpRect.set(left, top, left + width, top + height);
        canvas.drawRect(hpRect, borderP);

        Paint hpTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hpTextPaint.setColor(COLOR_TEXT);
        hpTextPaint.setTextSize(13f);
        hpTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hpTextPaint.setTextAlign(Paint.Align.CENTER);
        String hpLabel = (int) player.getHp() + " / " + (int) player.getMaxHp();
        canvas.drawText(hpLabel, left + width / 2, top + height + 16, hpTextPaint);
    }

    private void drawPlayerNames(Canvas canvas) {
        Player p1 = gameState.getPlayer1();
        Player p2 = gameState.getPlayer2();

        if (p1 != null) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(COLOR_TEXT);
            p.setTextSize(20f);
            p.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
            canvas.drawText(p1.getName(), 24, 24 + 18 + 30, p);
        }
        if (p2 != null) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(COLOR_TEXT);
            p.setTextSize(20f);
            p.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
            p.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(p2.getName(), arenaWidth - 24, 24 + 18 + 30, p);
        }
    }

    private void drawGameOverOverlay(Canvas canvas, GameState.State state) {
        canvas.drawRect(0, 0, arenaWidth, arenaHeight, overlayPaint);

        String title;
        switch (state) {
            case PLAYER1_WIN:
                title = "PLAYER 1 WINS";
                break;
            case PLAYER2_WIN:
                title = "PLAYER 2 WINS";
                break;
            default:
                title = "DRAW";
                break;
        }

        float cx = arenaWidth / 2;
        float cy = arenaHeight / 2;

        canvas.drawLine(cx - 80, cy - 80, cx - 40, cy - 80, separatorPaint);
        canvas.drawLine(cx + 40, cy - 80, cx + 80, cy - 80, separatorPaint);

        canvas.drawText(title, cx, cy - 12, textLargePaint);

        canvas.drawText("TAP TO RESTART", cx, cy + 48, textDimPaint);
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
