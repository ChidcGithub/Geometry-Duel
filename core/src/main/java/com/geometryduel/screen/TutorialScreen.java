package com.geometryduel.screen;

import com.badlogic.gdx.Gdx;
import com.geometryduel.GeometryDuelGame;
import com.geometryduel.render.Shapes;
import com.geometryduel.ui.TextButton;

/**
 * 教学模式（还原 Tutorial）：
 * - 初始难度 0.02，每从演示进入实战一次 +0.2，超过 1.0 即完成
 * - 界面显示：教学模式 / 难度 / 状态 / 目标：再赢 n 次；右上角"跳过"
 */
public class TutorialScreen extends GameScreen {
    private static final float LEVEL_STEP = 0.2f;

    private float level = 0.02f;
    private final TextButton skipBtn = new TextButton("Skip", 0, 0, 120, 48);

    {
        skipBtn.style = TextButton.STYLE_CONTAINER;
    }

    public TutorialScreen(GeometryDuelGame app) {
        super(app);
        // super 构造时 level 尚未初始化，这里用正确的初始难度重建演示局
        newGame(true, true);
    }

    @Override
    protected float currentLevel() {
        return level;
    }

    @Override
    protected void onNewGame(boolean demo) {
        level += LEVEL_STEP;
        if (level > 1.0f) {
            app.tutorialDone = true;
            app.saveConfig();
        }
    }

    @Override
    public void render(float delta) {
        float w = uiVp.getWorldWidth(), h = uiVp.getWorldHeight();
        float bw = 120f * (h / 640f), bh = 48f * (h / 640f);
        skipBtn.w = bw;
        skipBtn.h = bh;
        skipBtn.setCenter(w - bw / 2f - 8f, h - bh * 2.2f - 8f);
        super.render(delta);
        if (Gdx.input.justTouched()) {
            com.badlogic.gdx.math.Vector3 t = new com.badlogic.gdx.math.Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            uiVp.unproject(t);
            if (skipBtn.contains(t.x, t.y)) {
                app.tutorialDone = true;
                app.saveConfig();
                app.setScreen(new MenuScreen(app));
            }
        }
    }

    @Override
    protected void drawExtraUiShapes(Shapes s) {
        skipBtn.draw(s, app.theme);
    }

    @Override
    protected void drawExtraHud(float w, float h, float unit) {
        app.font.getData().setScale(1.4f * unit);
        app.font.setColor(app.theme.text);
        float x = unit * 12f, y = h - unit * 40f;
        app.font.draw(app.batch, "Tutorial Mode", x, y);
        app.font.draw(app.batch, "Level: " + String.format(java.util.Locale.US, "%.2f", level), x, y - 24f * unit);
        app.font.draw(app.batch, "Status: " + (app.tutorialDone ? "Done" : "Active"), x, y - 48f * unit);
        int remain = (int) Math.ceil((1.0f - level) / LEVEL_STEP);
        app.font.draw(app.batch, "Goal: " + Math.max(0, remain) + " Rounds Left", x, y - 72f * unit);
        skipBtn.drawText(app.batch, app.font, app.theme, 1.2f * unit);
    }
}
