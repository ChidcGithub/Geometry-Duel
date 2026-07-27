package com.geometryduel.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.geometryduel.GeometryDuelGame;
import com.geometryduel.game.gfx.GameBackground;
import com.geometryduel.ui.TextButton;

/**
 * 开始菜单（还原 StartMenu）：
 * 动态网格背景 + 标题"几何决斗" + 开始游戏/教学模式/设置 按钮。
 */
public class MenuScreen extends ScreenAdapter {
    private final GeometryDuelGame app;
    private final FitViewport worldVp;
    private final ScreenViewport uiVp;
    private GameBackground background;
    private final TextButton[] buttons;
    private final GlyphLayout layout = new GlyphLayout();
    private final Vector3 touch = new Vector3();

    public MenuScreen(GeometryDuelGame app) {
        this.app = app;
        OrthographicCamera worldCam = new OrthographicCamera();
        worldCam.setToOrtho(true, 640, 640);
        worldVp = new FitViewport(640, 640, worldCam);
        uiVp = new ScreenViewport();
        background = new GameBackground(app.theme.backgroundLine, 0.1f);
        buttons = new TextButton[]{
                new TextButton("开始游戏", 0, 0, 260, 64),
                new TextButton("教学模式", 0, 0, 260, 64),
                new TextButton("  设置  ", 0, 0, 260, 64),
        };
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int x, int y, int pointer, int button) {
                touch.set(x, y, 0);
                uiVp.unproject(touch);
                for (int i = 0; i < buttons.length; i++) {
                    if (buttons[i].contains(touch.x, touch.y)) {
                        onButton(i);
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void onButton(int i) {
        if (i == 0) app.setScreen(new GameScreen(app));
        else if (i == 1) app.setScreen(new TutorialScreen(app));
        else app.setScreen(new SettingsScreen(app));
    }

    @Override
    public void render(float delta) {
        background.update();

        Gdx.gl.glClearColor(app.theme.background.r, app.theme.background.g, app.theme.background.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        worldVp.apply();
        app.shapes.begin(worldVp.getCamera());
        background.display(app.shapes);
        app.shapes.end();

        layoutButtons();
        uiVp.apply();
        app.shapes.begin(uiVp.getCamera());
        for (TextButton b : buttons) b.draw(app.shapes, app.theme);
        app.shapes.end();

        app.batch.begin();
        app.batch.setProjectionMatrix(uiVp.getCamera().combined);
        float w = uiVp.getWorldWidth(), h = uiVp.getWorldHeight();
        float unit = h / 640f;
        app.font.getData().setScale(4f * unit);
        layout.setText(app.font, "几何决斗");
        app.font.setColor(app.theme.text);
        app.font.draw(app.batch, "几何决斗", (w - layout.width) / 2f, h * 0.071f + layout.height);
        for (TextButton b : buttons) b.drawText(app.batch, app.font, app.theme, 2f * unit);
        app.font.getData().setScale(1f);
        app.batch.end();

        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.Z)) {
            onButton(0);
        }
    }

    private void layoutButtons() {
        float w = uiVp.getWorldWidth(), h = uiVp.getWorldHeight();
        float bw = Math.min(260f * (h / 640f), w * 0.6f);
        float bh = 64f * (h / 640f);
        float gap = bh * 0.5f;
        float cy = h * 0.5f;
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].w = bw;
            buttons[i].h = bh;
            buttons[i].setCenter(w / 2f, cy + (bh + gap) * i);
        }
    }

    @Override
    public void resize(int width, int height) {
        worldVp.update(width, height, true);
        uiVp.update(width, height, true);
    }

    @Override
    public void dispose() {
    }
}
