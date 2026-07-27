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
    private final GlyphLayout layout = new GlyphLayout();
    private final Vector3 touch = new Vector3();

    public SettingsScreen(GeometryDuelGame app) {
        this.app = app;
        uiVp = new ScreenViewport();
        themeBtn = new TextButton("", 0, 0, 300, 64);
        volumeMinus = new TextButton("-", 0, 0, 64, 64);
        volumePlus = new TextButton("+", 0, 0, 64, 64);
        backBtn = new TextButton("返回", 0, 0, 200, 64);
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
        themeBtn.text = "主题：" + (app.themeType == ThemeData.Type.Dark ? "暗色" : "亮色");
        themeBtn.w = 300 * unit;
        themeBtn.h = 64 * unit;
        themeBtn.setCenter(w / 2f, h * 0.35f);
        volumeMinus.w = volumeMinus.h = 64 * unit;
        volumePlus.w = volumePlus.h = 64 * unit;
        volumeMinus.setCenter(w / 2f - 110 * unit, h * 0.55f);
        volumePlus.setCenter(w / 2f + 110 * unit, h * 0.55f);
        backBtn.w = 200 * unit;
        backBtn.h = 64 * unit;
        backBtn.setCenter(w / 2f, h * 0.8f);

        uiVp.apply();
        app.shapes.begin(uiVp.getCamera());
        themeBtn.draw(app.shapes, app.theme);
        volumeMinus.draw(app.shapes, app.theme);
        volumePlus.draw(app.shapes, app.theme);
        backBtn.draw(app.shapes, app.theme);
        app.shapes.end();

        app.batch.begin();
        app.batch.setProjectionMatrix(uiVp.getCamera().combined);
        app.font.getData().setScale(3f * unit);
        layout.setText(app.font, "设置");
        app.font.setColor(app.theme.text);
        app.font.draw(app.batch, "设置", (w - layout.width) / 2f, h * 0.15f + layout.height);
        themeBtn.drawText(app.batch, app.font, app.theme, 1.6f * unit);
        volumeMinus.drawText(app.batch, app.font, app.theme, 2f * unit);
        volumePlus.drawText(app.batch, app.font, app.theme, 2f * unit);
        app.font.getData().setScale(1.6f * unit);
        layout.setText(app.font, "音量：" + Math.round(app.volume * 100) + "%");
        app.font.draw(app.batch, "音量：" + Math.round(app.volume * 100) + "%",
                (w - layout.width) / 2f, h * 0.55f + layout.height / 2f);
        backBtn.drawText(app.batch, app.font, app.theme, 1.6f * unit);
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
