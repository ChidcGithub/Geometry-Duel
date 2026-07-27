package com.geometryduel.game;

import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.geometryduel.game.ai.AIController;
import com.geometryduel.game.ai.AIDifficulty;
import com.geometryduel.game.ai.SimpleAI;
import com.geometryduel.game.entity.ExplosionEffect;
import com.geometryduel.game.entity.GameState;
import com.geometryduel.game.entity.Player;
import com.geometryduel.game.entity.Projectile;
import com.geometryduel.game.entity.Shield;

import java.util.List;

public class GameEngine {

    private GameState gameState;
    private GameMode mode;
    private float arenaWidth;
    private float arenaHeight;

    private final int COLOR_BG = 0xFF0D0D0D;
    private final int COLOR_P1_FILL = 0xFFFFFFFF;
    private final int COLOR_P1_STROKE = 0xFFFFFFFF;
    private final int COLOR_P2_FILL = 0xFF000000;
    private final int COLOR_P2_STROKE = 0xFF000000;
    private final int COLOR_GRID = 0x14FFFFFF;
    private final int COLOR_CENTER = 0x26FFFFFF;
    private final int COLOR_HP_BG = 0xFF2A2A2D;
    private final int COLOR_HP_FILL = 0xFFFFFFFF;
    private final int COLOR_HP_LOW = 0xFF666666;
    private final int COLOR_TEXT = 0xFFFFFFFF;
    private final int COLOR_TEXT_DIM = 0x80FFFFFF;
    private final int COLOR_OVERLAY = 0xE6000000;
    private final int COLOR_SLOW_RING = 0xFFFF4D4D;

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
    private Paint innerPaintP1;
    private Paint innerPaintP2;
    private Paint aiLabelPaint;
    private Paint projectileStrokePaint;
    private Paint hpBorderPaintP1;
    private Paint hpBorderPaintP2;
    private Paint hpTextPaint;
    private Paint namePaintP1;
    private Paint namePaintP2;
    private Paint slowRingPaint;
    private Paint particlePaint;
    private Paint shieldPaint;
    private Paint shieldInnerPaint;
    private Paint aimLinePaint;

    private RectF hpRect;
    private RectF reusableRect;
    private Path arrowPath;
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
        textDimPaint.setTextAlign(Paint.Align.CENTER);

        textLargePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textLargePaint.setColor(COLOR_TEXT);
        textLargePaint.setTextSize(52f);
        textLargePaint.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        textLargePaint.setTextAlign(Paint.Align.CENTER);

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

        innerPaintP1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerPaintP1.setStyle(Paint.Style.FILL);
        innerPaintP1.setColor(COLOR_P1_STROKE);

        innerPaintP2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerPaintP2.setStyle(Paint.Style.FILL);
        innerPaintP2.setColor(COLOR_P2_STROKE);

        aiLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        aiLabelPaint.setColor(COLOR_TEXT_DIM);
        aiLabelPaint.setTextSize(14f);
        aiLabelPaint.setTextAlign(Paint.Align.CENTER);
        aiLabelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        projectileStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        projectileStrokePaint.setStyle(Paint.Style.STROKE);
        projectileStrokePaint.setStrokeWidth(1f);
        projectileStrokePaint.setAlpha(120);

        hpBorderPaintP1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        hpBorderPaintP1.setColor(COLOR_P1_STROKE);
        hpBorderPaintP1.setStyle(Paint.Style.STROKE);
        hpBorderPaintP1.setStrokeWidth(1.5f);

        hpBorderPaintP2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        hpBorderPaintP2.setColor(0xFFFFFFFF);
        hpBorderPaintP2.setStyle(Paint.Style.STROKE);
        hpBorderPaintP2.setStrokeWidth(1.5f);

        hpTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hpTextPaint.setColor(COLOR_TEXT);
        hpTextPaint.setTextSize(13f);
        hpTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hpTextPaint.setTextAlign(Paint.Align.CENTER);

        namePaintP1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        namePaintP1.setColor(COLOR_TEXT);
        namePaintP1.setTextSize(20f);
        namePaintP1.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));

        namePaintP2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        namePaintP2.setColor(COLOR_TEXT);
        namePaintP2.setTextSize(20f);
        namePaintP2.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        namePaintP2.setTextAlign(Paint.Align.RIGHT);

        slowRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        slowRingPaint.setColor(COLOR_SLOW_RING);
        slowRingPaint.setStyle(Paint.Style.STROKE);
        slowRingPaint.setStrokeWidth(5f);

        particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        particlePaint.setStyle(Paint.Style.FILL);

        shieldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shieldPaint.setStyle(Paint.Style.STROKE);
        shieldPaint.setStrokeWidth(4f);
        shieldPaint.setColor(0xCCFFFFFF);

        shieldInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shieldInnerPaint.setStyle(Paint.Style.STROKE);
        shieldInnerPaint.setStrokeWidth(1.5f);
        shieldInnerPaint.setColor(0x66FFFFFF);

        aimLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        aimLinePaint.setStyle(Paint.Style.STROKE);
        aimLinePaint.setStrokeWidth(3f);

        hpRect = new RectF();
        reusableRect = new RectF();
        arrowPath = new Path();
        running = false;
    }

    public void initialize(GameMode mode, float arenaWidth, float arenaHeight) {
        this.mode = mode;
        this.arenaWidth = arenaWidth;
        this.arenaHeight = arenaHeight;
        this.gameState = new GameState(arenaWidth, arenaHeight);
        this.running = true;

        int p1Color = 0xFFFFFFFF;
        int p2Color = 0xFF000000;

        Player p1 = new Player(arenaWidth * 0.25f, arenaHeight * 0.5f,
            p1Color, 0, "PLAYER", arenaWidth, arenaHeight);
        Player p2 = new Player(arenaWidth * 0.75f, arenaHeight * 0.5f,
            p2Color, 1, "ENEMY", arenaWidth, arenaHeight);

        gameState.setPlayer1(p1);
        gameState.setPlayer2(p2);

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
                p1.setName("AI - HARD");
                p2.setName("AI - MEDIUM");
                ai1.initialize(gameState, 0);
                ai2.initialize(gameState, 1);
                break;
        }

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
        canvas.drawColor(0xFF111118);

        if (arenaWidth <= 0 || arenaHeight <= 0) {
            Paint diagPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            diagPaint.setColor(0xFFFFFFFF);
            diagPaint.setTextSize(32f);
            diagPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Loading...", canvas.getWidth() / 2f, canvas.getHeight() / 2f, diagPaint);
            return;
        }

        drawArenaGrid(canvas);

        Player p1 = gameState.getPlayer1();
        Player p2 = gameState.getPlayer2();

        if (p1 != null) drawPlayer(canvas, p1, true);
        if (p2 != null) drawPlayer(canvas, p2, false);

        drawShields(canvas);
        drawUltAimLines(canvas);

        drawAllProjectiles(canvas);
        drawExplosionEffects(canvas);

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
        float squareHalf = r * 0.88f;
        float innerHalf = squareHalf * 0.33f;

        if (player.isSlowed()) {
            slowRingPaint.setAlpha((int) (110 + 100 * player.getSlowRatio()));
            canvas.drawCircle(x, y, r + 16f, slowRingPaint);
        }

        canvas.save();
        canvas.rotate(player.getRotationDegrees(), x, y);

        Paint fillPaint = isP1 ? p1FillPaint : p2FillPaint;
        Paint strokePaint = isP1 ? p1StrokePaint : p2StrokePaint;
        Paint innerP = isP1 ? innerPaintP1 : innerPaintP2;

        reusableRect.set(x - squareHalf, y - squareHalf, x + squareHalf, y + squareHalf);
        canvas.drawRect(reusableRect, fillPaint);
        canvas.drawRect(reusableRect, strokePaint);

        reusableRect.set(x - innerHalf, y - squareHalf * 0.72f, x + innerHalf, y - squareHalf * 0.1f);
        canvas.drawRect(reusableRect, innerP);

        reusableRect.set(x - squareHalf * 0.18f, y + squareHalf * 0.15f, x + squareHalf * 0.18f, y + squareHalf * 0.48f);
        canvas.drawRect(reusableRect, innerP);
        canvas.restore();

        if (player.isAIControlled()) {
            canvas.drawText("AI", x, y - r - 22, aiLabelPaint);
        }
    }

    private void drawShields(Canvas canvas) {
        Player p1 = gameState.getPlayer1();
        Player p2 = gameState.getPlayer2();
        if (p1 != null) drawShield(canvas, p1);
        if (p2 != null) drawShield(canvas, p2);
    }

    private void drawShield(Canvas canvas, Player player) {
        Shield shield = player.getActiveShield();
        if (shield == null || !shield.isAlive()) return;

        float cx = player.getX();
        float cy = player.getY();
        float r = player.getRadius() + Shield.SHIELD_OFFSET;
        float facingDeg = (float) Math.toDegrees(player.getFacingAngle());
        float arcDeg = shield.getArcDegrees();
        float startDeg = facingDeg - arcDeg / 2f;

        reusableRect.set(cx - r, cy - r, cx + r, cy + r);
        canvas.drawArc(reusableRect, startDeg, arcDeg, false, shieldPaint);
        float r2 = r - 6f;
        reusableRect.set(cx - r2, cy - r2, cx + r2, cy + r2);
        canvas.drawArc(reusableRect, startDeg, arcDeg, false, shieldInnerPaint);

        float a1 = (float) Math.toRadians(startDeg);
        float a2 = (float) Math.toRadians(startDeg + arcDeg);
        canvas.drawLine(cx + (float) Math.cos(a1) * (r - 8), cy + (float) Math.sin(a1) * (r - 8),
            cx + (float) Math.cos(a1) * (r + 8), cy + (float) Math.sin(a1) * (r + 8), shieldPaint);
        canvas.drawLine(cx + (float) Math.cos(a2) * (r - 8), cy + (float) Math.sin(a2) * (r - 8),
            cx + (float) Math.cos(a2) * (r + 8), cy + (float) Math.sin(a2) * (r + 8), shieldPaint);
    }

    private void drawUltAimLines(Canvas canvas) {
        Player p1 = gameState.getPlayer1();
        Player p2 = gameState.getPlayer2();
        if (p1 != null) drawUltAimLine(canvas, p1);
        if (p2 != null) drawUltAimLine(canvas, p2);
    }

    private void drawUltAimLine(Canvas canvas, Player player) {
        if (!player.isUltCharging()) return;

        float x = player.getX();
        float y = player.getY();
        float facing = player.getFacingAngle();
        float dx = (float) Math.cos(facing);
        float dy = (float) Math.sin(facing);

        float startX = x + dx * (player.getRadius() + 12f);
        float startY = y + dy * (player.getRadius() + 12f);
        float endX = x + dx * 900f;
        float endY = y + dy * 900f;

        if (player.isUltFullyCharged()) {
            aimLinePaint.setColor(0xFFFF3B3B);
            aimLinePaint.setAlpha(235);
            aimLinePaint.setPathEffect(null);
        } else {
            float pulse = 0.5f + 0.5f * (float) Math.abs(Math.sin(gameState.getGameTime() * 8f));
            aimLinePaint.setColor(0xFF000000);
            aimLinePaint.setAlpha((int) (90 + 150 * pulse));
            aimLinePaint.setPathEffect(new DashPathEffect(new float[]{18f, 14f}, 0f));
        }
        canvas.drawLine(startX, startY, endX, endY, aimLinePaint);
    }

    private void drawAllProjectiles(Canvas canvas) {
        List<Projectile> projectiles = gameState.getProjectiles();
        for (Projectile p : projectiles) {
            if (!p.isAlive()) continue;

            boolean ult = p.isUltimate();
            int baseColor = ult ? 0xFFFF3B3B
                : (p.getOwner().getPlayerId() == 0 ? 0xFFE0E0E0 : 0xFF444444);
            projectilePaint.setColor(baseColor);
            projectilePaint.setAlpha(240);

            float px = p.getX();
            float py = p.getY();
            float pr = p.getRadius() * (ult ? 4.0f : 2.8f);
            float angle = (float) Math.toDegrees(Math.atan2(p.getDirectionY(), p.getDirectionX()));

            canvas.save();
            canvas.translate(px, py);
            canvas.rotate(angle);

            projectileStrokePaint.setColor(baseColor);
            projectileStrokePaint.setStrokeWidth(ult ? 7f : 4f);
            canvas.drawLine(-pr * 0.7f, 0, pr * 0.45f, 0, projectileStrokePaint);
            canvas.drawLine(pr * 0.45f, 0, pr * 0.05f, -pr * 0.35f, projectileStrokePaint);
            canvas.drawLine(pr * 0.45f, 0, pr * 0.05f, pr * 0.35f, projectileStrokePaint);

            arrowPath.reset();
            arrowPath.moveTo(pr * 0.55f, 0);
            arrowPath.lineTo(pr * 0.04f, -pr * 0.24f);
            arrowPath.lineTo(pr * 0.04f, pr * 0.24f);
            arrowPath.close();
            canvas.drawPath(arrowPath, projectilePaint);
            canvas.restore();
        }
    }

    private void drawExplosionEffects(Canvas canvas) {
        for (ExplosionEffect effect : gameState.getExplosionEffects()) {
            int alpha = Math.max(0, Math.min(255, (int) (255 * effect.getLifeRatio())));
            particlePaint.setColor(effect.getColor());
            particlePaint.setAlpha(alpha);

            for (int i = 0; i < effect.getParticleCount(); i++) {
                float size = effect.getParticleSize(i);
                reusableRect.set(
                    effect.getParticleX(i) - size * 0.5f,
                    effect.getParticleY(i) - size * 0.5f,
                    effect.getParticleX(i) + size * 0.5f,
                    effect.getParticleY(i) + size * 0.5f
                );
                canvas.drawRect(reusableRect, particlePaint);
            }
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

        Paint borderP = isP1 ? hpBorderPaintP1 : hpBorderPaintP2;
        hpRect.set(left, top, left + width, top + height);
        canvas.drawRect(hpRect, borderP);

        String hpLabel = (int) player.getHp() + " / " + (int) player.getMaxHp();
        canvas.drawText(hpLabel, left + width / 2, top + height + 16, hpTextPaint);
    }

    private void drawPlayerNames(Canvas canvas) {
        Player p1 = gameState.getPlayer1();
        Player p2 = gameState.getPlayer2();

        if (p1 != null) {
            canvas.drawText(p1.getName(), 24, 24 + 18 + 30, namePaintP1);
        }
        if (p2 != null) {
            canvas.drawText(p2.getName(), arenaWidth - 24, 24 + 18 + 30, namePaintP2);
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

    public void handlePlayerDash() {
        Player p1 = gameState.getPlayer1();
        if (p1 != null && !p1.isAIControlled()) {
            p1.triggerDash();
        }
    }

    public void handlePlayerUltStart() {
        Player p1 = gameState.getPlayer1();
        if (p1 != null && !p1.isAIControlled()) {
            p1.startUltCharge();
        }
    }

    public void handlePlayerUltRelease() {
        Player p1 = gameState.getPlayer1();
        if (p1 != null && !p1.isAIControlled()) {
            p1.releaseUltCharge();
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
