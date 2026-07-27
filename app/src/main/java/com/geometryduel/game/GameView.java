package com.geometryduel.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.geometryduel.game.entity.GameState;

public class GameView extends View {

    private static final int INVALID_POINTER_ID = -1;

    private final GameEngine gameEngine;
    private GameMode gameMode;
    private boolean gameInitialized;

    private final Paint statusPaint;
    private final Paint hintPaint;
    private final Paint controlFillPaint;
    private final Paint controlActivePaint;
    private final Paint controlStrokePaint;
    private final Paint controlLabelPaint;
    private final Paint controlSubLabelPaint;

    private float dpadCenterX;
    private float dpadCenterY;
    private float dpadRadius;
    private float dpadKnobRadius;
    private float dpadKnobOffsetX;
    private float dpadKnobOffsetY;

    private float shootButtonX;
    private float shootButtonY;
    private float shootButtonRadius;

    private float dashButtonX;
    private float dashButtonY;
    private float dashButtonRadius;

    private float ultButtonX;
    private float ultButtonY;
    private float ultButtonRadius;

    private int movePointerId;
    private int shootPointerId;
    private int ultPointerId;
    private boolean shootHeld;
    private boolean ultCharging;
    private Paint ultProgressPaint;
    private android.graphics.RectF ultButtonRect;
    private long dashFlashUntilMs;
    private long lastFrameTimeNs;
    private String fatalErrorMessage;
    private boolean fatalErrorReported;

    public GameView(android.content.Context context) {
        super(context);
        gameEngine = new GameEngine();
        statusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        controlFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        controlActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        controlStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        controlLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        controlSubLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        initView();
    }

    public GameView(android.content.Context context, AttributeSet attrs) {
        super(context, attrs);
        gameEngine = new GameEngine();
        statusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        controlFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        controlActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        controlStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        controlLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        controlSubLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        initView();
    }

    private void initView() {
        setClickable(true);
        setFocusable(true);

        statusPaint.setColor(0xFFFFFFFF);
        statusPaint.setTextSize(40f);
        statusPaint.setTextAlign(Paint.Align.CENTER);

        hintPaint.setColor(0x99FFFFFF);
        hintPaint.setTextSize(22f);
        hintPaint.setTextAlign(Paint.Align.CENTER);

        controlFillPaint.setColor(0x1FFFFFFF);
        controlFillPaint.setStyle(Paint.Style.FILL);

        controlActivePaint.setColor(0x33FFFFFF);
        controlActivePaint.setStyle(Paint.Style.FILL);

        controlStrokePaint.setColor(0xB3FFFFFF);
        controlStrokePaint.setStrokeWidth(3f);
        controlStrokePaint.setStyle(Paint.Style.STROKE);

        controlLabelPaint.setColor(0xFFFFFFFF);
        controlLabelPaint.setTextSize(22f);
        controlLabelPaint.setTextAlign(Paint.Align.CENTER);

        controlSubLabelPaint.setColor(0x80FFFFFF);
        controlSubLabelPaint.setTextSize(16f);
        controlSubLabelPaint.setTextAlign(Paint.Align.CENTER);

        movePointerId = INVALID_POINTER_ID;
        shootPointerId = INVALID_POINTER_ID;
        ultPointerId = INVALID_POINTER_ID;
        ultCharging = false;

        ultProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ultProgressPaint.setStyle(Paint.Style.STROKE);
        ultProgressPaint.setStrokeWidth(5f);

        ultButtonRect = new android.graphics.RectF();
    }

    public void setGameMode(GameMode mode) {
        this.gameMode = mode;
        this.gameInitialized = false;
        maybeInitializeGame();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateControlLayout(w, h);
        maybeInitializeGame();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (fatalErrorMessage != null) {
            drawFatalError(canvas);
            return;
        }

        try {
            maybeInitializeGame();

            if (gameEngine.isRunning()) {
                long nowNs = SystemClock.elapsedRealtimeNanos();
                float deltaTime = 0.016f;
                if (lastFrameTimeNs != 0L) {
                    deltaTime = (nowNs - lastFrameTimeNs) / 1_000_000_000f;
                }
                lastFrameTimeNs = nowNs;
                if (deltaTime <= 0f || deltaTime > 0.05f) {
                    deltaTime = 0.016f;
                }

                if (shootHeld && isPlayerControlledMode()) {
                    gameEngine.handlePlayerShoot();
                }

                gameEngine.update(deltaTime);
                gameEngine.render(canvas);

                if (gameMode == GameMode.PLAYER_VS_AI && !isGameOver()) {
                    drawControls(canvas);
                }

                postInvalidateOnAnimation();
                return;
            }
        } catch (Exception e) {
            reportFatalError(e);
            drawFatalError(canvas);
            return;
        }

        canvas.drawColor(0xFF111118);
        canvas.drawText("WAITING...", getWidth() / 2f, getHeight() / 2f, statusPaint);
        canvas.drawText("Preparing arena", getWidth() / 2f, getHeight() / 2f + 42f, hintPaint);
        postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (fatalErrorMessage != null) {
            return true;
        }

        try {
            maybeInitializeGame();

            if (isGameOver() && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                resetControlState();
                gameEngine.restart();
                lastFrameTimeNs = SystemClock.elapsedRealtimeNanos();
                invalidate();
                return true;
            }

            if (!isPlayerControlledMode()) {
                return true;
            }

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN: {
                    int index = event.getActionIndex();
                    handlePointerDown(event.getPointerId(index), event.getX(index), event.getY(index));
                    break;
                }
                case MotionEvent.ACTION_MOVE:
                    for (int i = 0; i < event.getPointerCount(); i++) {
                        int pointerId = event.getPointerId(i);
                        if (pointerId == movePointerId) {
                            updateMovementFromTouch(event.getX(i), event.getY(i));
                        } else if (pointerId == shootPointerId && !isInsideCircle(
                            event.getX(i), event.getY(i), shootButtonX, shootButtonY, shootButtonRadius * 1.35f)) {
                            shootPointerId = INVALID_POINTER_ID;
                            shootHeld = false;
                        } else if (pointerId == ultPointerId && !isInsideCircle(
                            event.getX(i), event.getY(i), ultButtonX, ultButtonY, ultButtonRadius * 1.6f)) {
                            ultPointerId = INVALID_POINTER_ID;
                            ultCharging = false;
                            gameEngine.handlePlayerUltRelease();
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP: {
                    int index = event.getActionIndex();
                    handlePointerUp(event.getPointerId(index));
                    break;
                }
                case MotionEvent.ACTION_CANCEL:
                    resetControlState();
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            reportFatalError(e);
        }

        invalidate();
        return true;
    }

    public void stop() {
        gameEngine.dispose();
        resetControlState();
    }

    private void maybeInitializeGame() {
        if (gameInitialized || gameMode == null || getWidth() <= 0 || getHeight() <= 0 || fatalErrorReported) {
            return;
        }

        try {
            resetControlState();
            gameEngine.initialize(gameMode, getWidth(), getHeight());
            gameInitialized = true;
            lastFrameTimeNs = SystemClock.elapsedRealtimeNanos();
        } catch (Exception e) {
            reportFatalError(e);
        }
    }

    private void updateControlLayout(int width, int height) {
        float minSize = Math.min(width, height);
        float margin = minSize * 0.08f;

        dpadRadius = minSize * 0.14f;
        dpadKnobRadius = dpadRadius * 0.42f;
        dpadCenterX = margin + dpadRadius;
        dpadCenterY = height - margin - dpadRadius;

        shootButtonRadius = minSize * 0.10f;
        shootButtonX = width - margin - shootButtonRadius;
        shootButtonY = height - margin - shootButtonRadius;

        dashButtonRadius = minSize * 0.075f;
        dashButtonX = width - margin - shootButtonRadius;
        dashButtonY = shootButtonY - shootButtonRadius - dashButtonRadius - minSize * 0.05f;

        ultButtonRadius = minSize * 0.085f;
        ultButtonX = shootButtonX - shootButtonRadius - ultButtonRadius - minSize * 0.04f;
        ultButtonY = shootButtonY;
    }

    private void handlePointerDown(int pointerId, float x, float y) {
        if (isInsideCircle(x, y, dashButtonX, dashButtonY, dashButtonRadius * 1.25f)) {
            dashFlashUntilMs = SystemClock.uptimeMillis() + 140L;
            gameEngine.handlePlayerDash();
            return;
        }

        if (isInsideCircle(x, y, ultButtonX, ultButtonY, ultButtonRadius * 1.3f)) {
            ultPointerId = pointerId;
            ultCharging = true;
            gameEngine.handlePlayerUltStart();
            return;
        }

        if (isInsideCircle(x, y, shootButtonX, shootButtonY, shootButtonRadius * 1.35f)) {
            shootPointerId = pointerId;
            shootHeld = true;
            gameEngine.handlePlayerShoot();
            return;
        }

        if (x <= getWidth() * 0.5f && movePointerId == INVALID_POINTER_ID) {
            movePointerId = pointerId;
            updateMovementFromTouch(x, y);
        }
    }

    private void handlePointerUp(int pointerId) {
        if (pointerId == movePointerId) {
            movePointerId = INVALID_POINTER_ID;
            dpadKnobOffsetX = 0f;
            dpadKnobOffsetY = 0f;
            gameEngine.handlePlayerInput(0, 0);
        }

        if (pointerId == shootPointerId) {
            shootPointerId = INVALID_POINTER_ID;
            shootHeld = false;
        }

        if (pointerId == ultPointerId) {
            ultPointerId = INVALID_POINTER_ID;
            ultCharging = false;
            gameEngine.handlePlayerUltRelease();
        }
    }

    private void updateMovementFromTouch(float x, float y) {
        float dx = x - dpadCenterX;
        float dy = y - dpadCenterY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        if (distance > dpadRadius) {
            dx = dx / distance * dpadRadius;
            dy = dy / distance * dpadRadius;
            distance = dpadRadius;
        }

        dpadKnobOffsetX = dx;
        dpadKnobOffsetY = dy;

        float threshold = dpadRadius * 0.28f;
        int moveX = Math.abs(dx) >= threshold ? (dx > 0 ? 1 : -1) : 0;
        int moveY = Math.abs(dy) >= threshold ? (dy > 0 ? 1 : -1) : 0;

        if (distance < threshold * 0.8f) {
            moveX = 0;
            moveY = 0;
        }

        gameEngine.handlePlayerInput(moveX, moveY);
    }

    private void resetControlState() {
        boolean wasUltCharging = ultCharging;
        movePointerId = INVALID_POINTER_ID;
        shootPointerId = INVALID_POINTER_ID;
        shootHeld = false;
        ultPointerId = INVALID_POINTER_ID;
        ultCharging = false;
        dpadKnobOffsetX = 0f;
        dpadKnobOffsetY = 0f;
        if (gameInitialized) {
            gameEngine.handlePlayerInput(0, 0);
            if (wasUltCharging) {
                gameEngine.handlePlayerUltRelease();
            }
        }
    }

    private boolean isPlayerControlledMode() {
        if (!gameInitialized || gameMode != GameMode.PLAYER_VS_AI) {
            return false;
        }

        GameState gameState = gameEngine.getGameState();
        return gameState != null
            && gameState.getState() == GameState.State.RUNNING
            && gameState.getPlayer1() != null
            && !gameState.getPlayer1().isAIControlled();
    }

    private boolean isGameOver() {
        if (!gameInitialized) {
            return false;
        }

        GameState.State state = gameEngine.getGameState().getState();
        return state == GameState.State.PLAYER1_WIN
            || state == GameState.State.PLAYER2_WIN
            || state == GameState.State.DRAW;
    }

    private boolean isInsideCircle(float x, float y, float centerX, float centerY, float radius) {
        float dx = x - centerX;
        float dy = y - centerY;
        return dx * dx + dy * dy <= radius * radius;
    }

    private void drawControls(Canvas canvas) {
        boolean dashActive = SystemClock.uptimeMillis() < dashFlashUntilMs;

        canvas.drawCircle(dpadCenterX, dpadCenterY, dpadRadius,
            movePointerId == INVALID_POINTER_ID ? controlFillPaint : controlActivePaint);
        canvas.drawCircle(dpadCenterX, dpadCenterY, dpadRadius, controlStrokePaint);
        canvas.drawLine(dpadCenterX - dpadRadius * 0.55f, dpadCenterY,
            dpadCenterX + dpadRadius * 0.55f, dpadCenterY, controlStrokePaint);
        canvas.drawLine(dpadCenterX, dpadCenterY - dpadRadius * 0.55f,
            dpadCenterX, dpadCenterY + dpadRadius * 0.55f, controlStrokePaint);
        canvas.drawCircle(dpadCenterX + dpadKnobOffsetX, dpadCenterY + dpadKnobOffsetY,
            dpadKnobRadius, movePointerId == INVALID_POINTER_ID ? controlStrokePaint : controlActivePaint);
        canvas.drawText("MOVE", dpadCenterX, dpadCenterY + dpadRadius + 34f, controlLabelPaint);

        canvas.drawCircle(shootButtonX, shootButtonY, shootButtonRadius,
            shootHeld ? controlActivePaint : controlFillPaint);
        canvas.drawCircle(shootButtonX, shootButtonY, shootButtonRadius, controlStrokePaint);
        canvas.drawText("SHOOT", shootButtonX, shootButtonY + 8f, controlLabelPaint);

        canvas.drawCircle(dashButtonX, dashButtonY, dashButtonRadius,
            dashActive ? controlActivePaint : controlFillPaint);
        canvas.drawCircle(dashButtonX, dashButtonY, dashButtonRadius, controlStrokePaint);
        canvas.drawText("DASH", dashButtonX, dashButtonY + 6f, controlSubLabelPaint);

        float ultProgress = 0f;
        boolean ultChargingNow = false;
        boolean ultCharged = false;
        GameState gs = gameEngine.getGameState();
        if (gs != null && gs.getPlayer1() != null) {
            ultChargingNow = gs.getPlayer1().isUltCharging();
            ultProgress = gs.getPlayer1().getUltChargeProgress();
            ultCharged = gs.getPlayer1().isUltFullyCharged();
        }

        canvas.drawCircle(ultButtonX, ultButtonY, ultButtonRadius,
            ultChargingNow ? controlActivePaint : controlFillPaint);
        if (ultChargingNow) {
            ultProgressPaint.setColor(ultCharged ? 0xFFFF3B3B : 0xFF000000);
            if (ultCharged) {
                ultProgressPaint.setAlpha(255);
            } else {
                ultProgressPaint.setAlpha((int) (120 + 135 * Math.abs(Math.sin(SystemClock.uptimeMillis() * 0.012))));
            }
            ultButtonRect.set(ultButtonX - ultButtonRadius, ultButtonY - ultButtonRadius,
                ultButtonX + ultButtonRadius, ultButtonY + ultButtonRadius);
            canvas.drawArc(ultButtonRect, -90f, 360f * ultProgress, false, ultProgressPaint);
        }
        canvas.drawCircle(ultButtonX, ultButtonY, ultButtonRadius, controlStrokePaint);
        canvas.drawText("ULT", ultButtonX, ultButtonY + 6f, controlSubLabelPaint);

        if (!isGameOver()) {
            canvas.drawText("Move: left  •  Shoot/Dash/Ult: right", getWidth() / 2f, getHeight() - 18f, controlSubLabelPaint);
        }
    }

    private void drawFatalError(Canvas canvas) {
        canvas.drawColor(0xFF111118);
        canvas.drawText("CRASH", getWidth() / 2f, getHeight() / 2f - 20f, statusPaint);
        canvas.drawText("See dialog for details", getWidth() / 2f, getHeight() / 2f + 22f, hintPaint);
    }

    private void reportFatalError(Exception error) {
        if (fatalErrorReported) {
            return;
        }

        fatalErrorReported = true;
        fatalErrorMessage = android.util.Log.getStackTraceString(error);
        gameEngine.dispose();

        if (getContext() instanceof Activity) {
            post(() -> showFatalDialog((Activity) getContext(), fatalErrorMessage));
        }
    }

    private void showFatalDialog(Activity activity, String message) {
        if (activity.isFinishing()) {
            return;
        }

        new AlertDialog.Builder(activity)
            .setTitle("Crash")
            .setMessage(message)
            .setPositiveButton("Copy", (dialog, which) -> {
                android.content.ClipboardManager clipboardManager =
                    (android.content.ClipboardManager) activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("err", message));
            })
            .setNegativeButton("Close", (dialog, which) -> activity.finish())
            .show();
    }
}
