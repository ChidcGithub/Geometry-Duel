package com.geometryduel.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.geometryduel.GeometryDuelGame;
import com.geometryduel.ThemeData;
import com.geometryduel.ui.TextButton;

/**
 * 设置（还原 Settings 的核心项）：
 * 主题：亮色/暗色；音量：0..100%；返回。
 */
public class SettingsScreen extends ScreenAdapter {
    private final GeometryDuelGame app;
    private final ScreenViewport uiVp;
    private final TextButton themeBtn, volumeMinus, volumePlus, backBtn;
    private final TextButton opponentBtn, raysMinus, raysPlus, resetAiBtn;
    private final GlyphLayout layout = new GlyphLayout();
    private final Vector3 touch = new Vector3();

    public SettingsScreen(GeometryDuelGame app) {
        this.app = app;
        uiVp = new ScreenViewport();
        themeBtn = new TextButton("", 0, 0, 300, 64);
        volumeMinus = new TextButton("-", 0, 0, 64, 64);
        volumePlus = new TextButton("+", 0, 0, 64, 64);
        opponentBtn = new TextButton("", 0, 0, 300, 64);
        raysMinus = new TextButton("-", 0, 0, 64, 64);
        raysPlus = new TextButton("+", 0, 0, 64, 64);
        resetAiBtn = new TextButton("Reset AI", 0, 0, 300, 64);
        backBtn = new TextButton("Back", 0, 0, 200, 64);
    }

    @Override
    public void show() {
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int x, int y, int pointer, int button) {
                touch.set(x, y, 0);
                uiVp.unproject(touch);
                if (themeBtn.contains(touch.x, touch.y)) {
                    app.toggleTheme();
                } else if (volumeMinus.contains(touch.x, touch.y)) {
                    app.volume = Math.max(0f, app.volume - 0.1f);
                } else if (volumePlus.contains(touch.x, touch.y)) {
                    app.volume = Math.min(1f, app.volume + 0.1f);
                } else if (opponentBtn.contains(touch.x, touch.y)) {
                    app.cycleOpponentStyle();
                } else if (raysMinus.contains(touch.x, touch.y)) {
                    app.setVisionRays(app.visionRays - 4);
                } else if (raysPlus.contains(touch.x, touch.y)) {
                    app.setVisionRays(app.visionRays + 4);
                } else if (resetAiBtn.contains(touch.x, touch.y)) {
                    app.resetAi();
                } else if (backBtn.contains(touch.x, touch.y)) {
                    back();
                }
                return true;
            }

            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                    back();
                    return true;
                }
                return false;
            }
        });
    }

    private void back() {
        app.saveConfig();
        app.setScreen(new MenuScreen(app));
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(app.theme.background.r, app.theme.background.g, app.theme.background.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float w = uiVp.getWorldWidth(), h = uiVp.getWorldHeight();
        float unit = h / 640f;
        themeBtn.text = "Theme: " + (app.themeType == ThemeData.Type.Dark ? "Dark" : "Light");
        themeBtn.w = 300 * unit;
        themeBtn.h = 56 * unit;
        themeBtn.setCenter(w / 2f, h * 0.24f);
        volumeMinus.w = volumeMinus.h = 56 * unit;
        volumePlus.w = volumePlus.h = 56 * unit;
        volumeMinus.setCenter(w / 2f - 110 * unit, h * 0.37f);
        volumePlus.setCenter(w / 2f + 110 * unit, h * 0.37f);
        opponentBtn.text = "Opponent: " + app.opponentStyleLabel();
        opponentBtn.w = 300 * unit;
        opponentBtn.h = 56 * unit;
        opponentBtn.setCenter(w / 2f, h * 0.50f);
        raysMinus.w = raysMinus.h = 56 * unit;
        raysPlus.w = raysPlus.h = 56 * unit;
        raysMinus.setCenter(w / 2f - 110 * unit, h * 0.63f);
        raysPlus.setCenter(w / 2f + 110 * unit, h * 0.63f);
        resetAiBtn.w = 300 * unit;
        resetAiBtn.h = 56 * unit;
        resetAiBtn.setCenter(w / 2f, h * 0.76f);
        backBtn.w = 200 * unit;
        backBtn.h = 56 * unit;
        backBtn.setCenter(w / 2f, h * 0.90f);

        uiVp.apply();
        app.shapes.begin(uiVp.getCamera());
        themeBtn.draw(app.shapes, app.theme);
        volumeMinus.draw(app.shapes, app.theme);
        volumePlus.draw(app.shapes, app.theme);
        opponentBtn.draw(app.shapes, app.theme);
        raysMinus.draw(app.shapes, app.theme);
        raysPlus.draw(app.shapes, app.theme);
        resetAiBtn.draw(app.shapes, app.theme);
        backBtn.draw(app.shapes, app.theme);
        app.shapes.end();

        app.batch.begin();
        app.batch.setProjectionMatrix(uiVp.getCamera().combined);
        app.font.getData().setScale(3f * unit);
        layout.setText(app.font, "Settings");
        app.font.setColor(app.theme.text);
        app.font.draw(app.batch, "Settings", (w - layout.width) / 2f, h * 0.10f + layout.height);
        themeBtn.drawText(app.batch, app.font, app.theme, 1.5f * unit);
        volumeMinus.drawText(app.batch, app.font, app.theme, 2f * unit);
        volumePlus.drawText(app.batch, app.font, app.theme, 2f * unit);
        app.font.getData().setScale(1.5f * unit);
        layout.setText(app.font, "Volume: " + Math.round(app.volume * 100) + "%");
        app.font.draw(app.batch, "Volume: " + Math.round(app.volume * 100) + "%",
                (w - layout.width) / 2f, h * 0.37f + layout.height / 2f);
        opponentBtn.drawText(app.batch, app.font, app.theme, 1.5f * unit);
        raysMinus.drawText(app.batch, app.font, app.theme, 2f * unit);
        raysPlus.drawText(app.batch, app.font, app.theme, 2f * unit);
        app.font.getData().setScale(1.5f * unit);
        String raysLabel = "Vision Rays: " + app.visionRays;
        layout.setText(app.font, raysLabel);
        app.font.draw(app.batch, raysLabel, (w - layout.width) / 2f, h * 0.63f + layout.height / 2f);
        app.font.getData().setScale(1f * unit);
        String raysNote = "(changing resets AI training)";
        layout.setText(app.font, raysNote);
        app.font.setColor(app.theme.text.r, app.theme.text.g, app.theme.text.b, 0.55f);
        app.font.draw(app.batch, raysNote, (w - layout.width) / 2f, h * 0.63f + 40f * unit);
        app.font.setColor(app.theme.text);
        resetAiBtn.drawText(app.batch, app.font, app.theme, 1.5f * unit);
        backBtn.drawText(app.batch, app.font, app.theme, 1.5f * unit);
        app.font.getData().setScale(1f);
        app.batch.end();
    }

    @Override
    public void resize(int width, int height) {
        uiVp.update(width, height, true);
    }

    @Override
    public void dispose() {
    }
}
