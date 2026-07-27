package com.geometryduel.game;

import android.content.Context;
import android.graphics.Canvas;
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
            }
        } finally {
            if (canvas != null) {
                holder.unlockCanvasAndPost(canvas);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        float w = getWidth();
        float h = getHeight();

        float dPadCenterX = w * 0.15f;
        float dPadCenterY = h * 0.75f;
        float dPadSize = 90f;

        float actionButtonX = w * 0.85f;
        float actionButtonY = h * 0.75f;
        float actionButtonSize = 70f;

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

        float dx = x - dPadCenterX;
        float dy = y - dPadCenterY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        if (dist < dPadSize + 30f) {
            if (Math.abs(dx) > Math.abs(dy)) {
                rightPressed = dx > 15f && down;
                leftPressed = dx < -15f && down;
                upPressed = false;
                downPressed = false;
            } else if (Math.abs(dy) > 15f) {
                downPressed = dy > 15f && down;
                upPressed = dy < -15f && down;
                leftPressed = false;
                rightPressed = false;
            }
        }

        float adx = x - actionButtonX;
        float ady = y - actionButtonY;
        float adist = (float) Math.sqrt(adx * adx + ady * ady);

        if (adist < actionButtonSize + 20f) {
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
