package com.geometryduel.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.geometryduel.game.entity.GameState;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private SurfaceHolder holder;
    private Thread gameThread;
    private volatile boolean running;
    private GameEngine gameEngine;
    private GameMode gameMode;

    private long lastFrameTime;
    private static final float TARGET_FPS = 60f;
    private static final float FRAME_TIME = 1f / TARGET_FPS;

    private boolean leftPressed;
    private boolean rightPressed;
    private boolean upPressed;
    private boolean downPressed;
    private boolean shootPressed;

    private ControlsCallback controlsCallback;

    private float buttonSize;
    private float dpadCenterX;
    private float dpadCenterY;
    private float shootCenterX;
    private float shootCenterY;

    private final Paint ctrlFillPaint;
    private final Paint ctrlStrokePaint;
    private final Paint ctrlActivePaint;
    private final Paint ctrlTextPaint;
    private final RectF ctrlRect;

    public interface ControlsCallback {
        void onGameOver();
    }

    public GameView(Context context) {
        super(context);
        init(context);
    }

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        holder = getHolder();
        holder.addCallback(this);
        gameEngine = new GameEngine();

        ctrlFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ctrlFillPaint.setColor(0x08FFFFFF);
        ctrlFillPaint.setStyle(Paint.Style.FILL);

        ctrlStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ctrlStrokePaint.setColor(0x40FFFFFF);
        ctrlStrokePaint.setStyle(Paint.Style.STROKE);
        ctrlStrokePaint.setStrokeWidth(1.5f);

        ctrlActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ctrlActivePaint.setColor(0x20FFFFFF);
        ctrlActivePaint.setStyle(Paint.Style.FILL);

        ctrlTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ctrlTextPaint.setColor(0x80FFFFFF);
        ctrlTextPaint.setTextSize(13f);
        ctrlTextPaint.setTextAlign(Paint.Align.CENTER);
        ctrlTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        ctrlRect = new RectF();

        buttonSize = 80f;
        dpadCenterX = 120f;
        dpadCenterY = 600f;
        shootCenterX = 900f;
        shootCenterY = 600f;
    }

    public void setGameMode(GameMode mode) {
        this.gameMode = mode;
    }

    public void setControlsCallback(ControlsCallback callback) {
        this.controlsCallback = callback;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        startGameLoop();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        gameEngine.initialize(gameMode, width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopGameLoop();
    }

    private void startGameLoop() {
        if (gameThread != null) return;

        running = true;
        gameThread = new Thread(this);
        gameThread.start();
    }

    private void stopGameLoop() {
        running = false;
        try {
            if (gameThread != null) {
                gameThread.join(500);
                gameThread = null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run() {
        lastFrameTime = System.nanoTime();

        while (running) {
            long now = System.nanoTime();
            float deltaTime = (now - lastFrameTime) / 1_000_000_000f;
            lastFrameTime = now;

            if (deltaTime > 0.1f) deltaTime = 0.1f;

            updateGame(deltaTime);
            renderGame();

            long elapsed = (System.nanoTime() - now) / 1_000_000;
            long sleepTime = (long) (FRAME_TIME * 1000) - elapsed;

            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void updateGame(float deltaTime) {
        if (gameEngine.isRunning()) {
            processPlayerInput();
            gameEngine.update(deltaTime);

            if (gameEngine.getGameState().getState() == GameState.State.PLAYER1_WIN
                || gameEngine.getGameState().getState() == GameState.State.PLAYER2_WIN
                || gameEngine.getGameState().getState() == GameState.State.DRAW) {
                if (controlsCallback != null) {
                    controlsCallback.onGameOver();
                }
            }
        }
    }

    private void processPlayerInput() {
        int dx = 0;
        int dy = 0;

        if (leftPressed) dx--;
        if (rightPressed) dx++;
        if (upPressed) dy--;
        if (downPressed) dy++;

        gameEngine.handlePlayerInput(dx, dy);

        if (shootPressed) {
            gameEngine.handlePlayerShoot();
            shootPressed = false;
        }
    }

    private void renderGame() {
        Canvas canvas = null;
        try {
            canvas = holder.lockCanvas();
            if (canvas != null) {
                gameEngine.render(canvas);
                if (gameEngine.getMode() == GameMode.PLAYER_VS_AI) {
                    drawControls(canvas);
                }
            }
        } finally {
            if (canvas != null) {
                holder.unlockCanvasAndPost(canvas);
            }
        }
    }

    private void drawControls(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();

        buttonSize = Math.min(w, h) * 0.12f;
        float gap = buttonSize * 0.15f;

        dpadCenterX = w * 0.13f;
        dpadCenterY = h * 0.78f;

        shootCenterX = w * 0.87f;
        shootCenterY = h * 0.78f;

        drawDpadButton(canvas, dpadCenterX - buttonSize - gap, dpadCenterY, leftPressed, "L");
        drawDpadButton(canvas, dpadCenterX + buttonSize + gap, dpadCenterY, rightPressed, "R");
        drawDpadButton(canvas, dpadCenterX, dpadCenterY - buttonSize - gap, upPressed, "U");
        drawDpadButton(canvas, dpadCenterX, dpadCenterY + buttonSize + gap, downPressed, "D");

        float shootSize = buttonSize * 1.4f;
        ctrlRect.set(shootCenterX - shootSize / 2, shootCenterY - shootSize / 2,
            shootCenterX + shootSize / 2, shootCenterY + shootSize / 2);
        canvas.drawRect(ctrlRect, shootPressed ? ctrlActivePaint : ctrlFillPaint);
        canvas.drawRect(ctrlRect, ctrlStrokePaint);

        Paint shootTextP = new Paint(ctrlTextPaint);
        shootTextP.setTextSize(buttonSize * 0.28f);
        shootTextP.setColor(0x80FFFFFF);
        canvas.drawText("FIRE", shootCenterX, shootCenterY + shootTextP.getTextSize() / 3, shootTextP);
    }

    private void drawDpadButton(Canvas canvas, float cx, float cy, boolean active, String label) {
        float half = buttonSize / 2;
        ctrlRect.set(cx - half, cy - half, cx + half, cy + half);

        Paint fill = active ? ctrlActivePaint : ctrlFillPaint;
        canvas.drawRect(ctrlRect, fill);
        canvas.drawRect(ctrlRect, ctrlStrokePaint);

        Paint labelP = new Paint(ctrlTextPaint);
        labelP.setTextSize(buttonSize * 0.35f);
        labelP.setColor(active ? 0xFFCCCCCC : 0x60FFFFFF);
        canvas.drawText(label, cx, cy + labelP.getTextSize() / 3, labelP);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        float btnHalf = buttonSize / 2;
        float gap = buttonSize * 0.15f;

        float leftCX = dpadCenterX - buttonSize - gap;
        float rightCX = dpadCenterX + buttonSize + gap;
        float upCY = dpadCenterY - buttonSize - gap;
        float downCY = dpadCenterY + buttonSize + gap;

        float shootHalf = buttonSize * 0.7f;

        boolean down = event.getAction() == MotionEvent.ACTION_DOWN
            || event.getAction() == MotionEvent.ACTION_MOVE;
        boolean up = event.getAction() == MotionEvent.ACTION_UP
            || event.getAction() == MotionEvent.ACTION_CANCEL;

        if (up) {
            leftPressed = false;
            rightPressed = false;
            upPressed = false;
            downPressed = false;
            shootPressed = false;
            return true;
        }

        if (Math.abs(x - leftCX) < btnHalf + 10 && Math.abs(y - dpadCenterY) < btnHalf + 10) {
            leftPressed = down;
            rightPressed = false;
            upPressed = false;
            downPressed = false;
        } else if (Math.abs(x - rightCX) < btnHalf + 10 && Math.abs(y - dpadCenterY) < btnHalf + 10) {
            rightPressed = down;
            leftPressed = false;
            upPressed = false;
            downPressed = false;
        } else if (Math.abs(y - upCY) < btnHalf + 10 && Math.abs(x - dpadCenterX) < btnHalf + 10) {
            upPressed = down;
            downPressed = false;
            leftPressed = false;
            rightPressed = false;
        } else if (Math.abs(y - downCY) < btnHalf + 10 && Math.abs(x - dpadCenterX) < btnHalf + 10) {
            downPressed = down;
            upPressed = false;
            leftPressed = false;
            rightPressed = false;
        }

        if (Math.abs(x - shootCenterX) < shootHalf + 15 && Math.abs(y - shootCenterY) < shootHalf + 15) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                shootPressed = true;
            }
        }

        if (gameEngine.getGameState().getState() == GameState.State.PLAYER1_WIN
            || gameEngine.getGameState().getState() == GameState.State.PLAYER2_WIN
            || gameEngine.getGameState().getState() == GameState.State.DRAW) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                gameEngine.restart();
            }
        }

        return true;
    }

    public GameEngine getGameEngine() {
        return gameEngine;
    }

    public void stop() {
        stopGameLoop();
        if (gameEngine != null) {
            gameEngine.dispose();
        }
    }
}
