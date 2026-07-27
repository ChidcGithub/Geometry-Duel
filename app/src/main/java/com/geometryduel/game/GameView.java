package com.geometryduel.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class GameView extends View {

    private GameEngine gameEngine;
    private GameMode gameMode;
    private final Paint testPaint;

    public GameView(Context context) {
        super(context);
        testPaint = new Paint();
        testPaint.setColor(0xFFFFFFFF);
        testPaint.setTextSize(40f);
        testPaint.setTextAlign(Paint.Align.CENTER);
        gameEngine = new GameEngine();
    }

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        testPaint = new Paint();
        testPaint.setColor(0xFFFFFFFF);
        testPaint.setTextSize(40f);
        testPaint.setTextAlign(Paint.Align.CENTER);
        gameEngine = new GameEngine();
    }

    public void setGameMode(GameMode mode) {
        this.gameMode = mode;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0 && gameMode != null) {
            gameEngine.initialize(gameMode, w, h);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (gameEngine.isRunning()) {
            gameEngine.update(0.016f);
            gameEngine.render(canvas);
            postInvalidate();
        } else {
            canvas.drawColor(0xFF222233);
            canvas.drawText("WAITING...", getWidth() / 2f, getHeight() / 2f, testPaint);
            postInvalidate();
        }
    }

    public void stop() {
        if (gameEngine != null) {
            gameEngine.dispose();
        }
    }
}
