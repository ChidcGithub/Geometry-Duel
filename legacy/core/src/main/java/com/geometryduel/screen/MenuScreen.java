package com.geometryduel.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.geometryduel.GeometryDuelGame;
import com.geometryduel.game.gfx.GameBackground;
import com.geometryduel.ui.Anim;
import com.geometryduel.ui.M3Button;

/**
 * 主菜单（M3 Expressive）：
 * - Hero 大标题：弹簧缩放入场
 * - AI 训练状态 chips（Gen / 胜率 / 代速）
 * - 三个大圆角按钮 stagger 上浮入场，按压缩放回弹
 * - 底部：硬件信息 + 版本号
 */
public class MenuScreen extends ScreenAdapter {
    private final GeometryDuelGame app;
    private final FitViewport worldVp;
    private final ScreenViewport uiVp;
    private final GameBackground background;
    private final M3Button startBtn, tutorialBtn, settingsBtn;
    private final GlyphLayout layout = new GlyphLayout();
    private final Vector3 touch = new Vector3();
    private float clock;

    public MenuScreen(GeometryDuelGame app) {
        this.app = app;
        OrthographicCamera worldCam = new OrthographicCamera();
        worldCam.setToOrtho(true, 640, 640);
        worldVp = new FitViewport(640, 640, worldCam);
        uiVp = new ScreenViewport();
        background = new GameBackground(app.theme.backgroundLine, 0.1f);
        startBtn = new M3Button("Start Game", 0, 0);
        startBtn.style = M3Button.FILLED;
        tutorialBtn = new M3Button("Tutorial", 0, 0);
        settingsBtn = new M3Button("Settings", 0, 0);
    }

    @Override
    public void show() {
        clock = 0f;
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int x, int y, int pointer, int button) {
                touch.set(x, y, 0);
                uiVp.unproject(touch);
                if (startBtn.contains(touch.x, touch.y)) { startBtn.setPressed(true); act(0); return true; }
                if (tutorialBtn.contains(touch.x, touch.y)) { tutorialBtn.setPressed(true); act(1); return true; }
                if (settingsBtn.contains(touch.x, touch.y)) { settingsBtn.setPressed(true); act(2); return true; }
                return false;
            }

            @Override
            public boolean touchUp(int x, int y, int pointer, int button) {
                startBtn.setPressed(false);
                tutorialBtn.setPressed(false);
                settingsBtn.setPressed(false);
                return true;
            }
        });
    }

    private void act(int i) {
        if (i == 0) app.setScreen(new GameScreen(app));
        else if (i == 1) app.setScreen(new TutorialScreen(app));
        else app.setScreen(new SettingsScreen(app));
    }

    @Override
    public void render(float delta) {
        clock += delta;
        startBtn.update(delta);
        tutorialBtn.update(delta);
        settingsBtn.update(delta);
        background.update();

        float w = uiVp.getWorldWidth(), h = uiVp.getWorldHeight();
        float unit = h / 640f;
        layoutButtons(w, h, unit);

        Gdx.gl.glClearColor(app.theme.background.r, app.theme.background.g, app.theme.background.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // 动态网格背景
        worldVp.apply();
        app.shapes.begin(worldVp.getCamera());
        background.display(app.shapes);
        app.shapes.end();

        // ---- 形状层：按钮 + chips ----
        uiVp.apply();
        app.shapes.begin(uiVp.getCamera());
        startBtn.draw(app.shapes, app.theme);
        tutorialBtn.draw(app.shapes, app.theme);
        settingsBtn.draw(app.shapes, app.theme);
        drawChips(app.shapes, w, h, unit);
        app.shapes.end();

        // ---- 文本层 ----
        app.batch.begin();
        app.batch.setProjectionMatrix(uiVp.getCamera().combined);

        drawHeroTitle(w, h, unit);
        startBtn.drawText(app.batch, app.fonts, app.theme);
        tutorialBtn.drawText(app.batch, app.fonts, app.theme);
        settingsBtn.drawText(app.batch, app.fonts, app.theme);
        drawChipsText(w, h, unit);
        drawFooter(w, h, unit);

        app.batch.end();

        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.Z)) {
            act(0);
        }
    }

    /** Hero 标题：弹簧缩放 + 淡入。 */
    private void drawHeroTitle(float w, float h, float unit) {
        float t = Anim.clamp01(clock / 0.55f);
        float scale = Anim.lerp(0.86f, 1f, Anim.spring(t));
        float alpha = Anim.decelerate(Anim.clamp01(clock / 0.35f));
        BitmapFont f = app.fonts.display();
        f.getData().setScale(scale);
        String s = "GEOMETRY DUEL";
        layout.setText(f, s);
        // 窄屏收缩防溢出
        float fit = Math.min(1f, w * 0.92f / layout.width);
        if (fit < 1f) {
            f.getData().setScale(scale * fit);
            layout.setText(f, s);
        }
        f.setColor(app.theme.primary.r, app.theme.primary.g, app.theme.primary.b, alpha);
        f.draw(app.batch, s, (w - layout.width) / 2f, h * 0.185f + layout.height / 2f);
        f.getData().setScale(1f);

        // 副标题
        BitmapFont lf = app.fonts.label();
        String sub = "N E A T   A I   E D I T I O N";
        layout.setText(lf, sub);
        lf.setColor(app.theme.text.r, app.theme.text.g, app.theme.text.b, 0.5f * alpha);
        lf.draw(app.batch, sub, (w - layout.width) / 2f, h * 0.245f + layout.height / 2f);
    }

    // ---- AI 状态 chips ----
    private final String[] chipText = new String[3];

    private void buildChips() {
        if (app.trainer != null) {
            float wr = app.trainer.championWinRate();
            chipText[0] = "GEN " + app.trainer.generation();
            chipText[1] = "WR " + (wr < 0 ? "--" : Math.round(wr * 100) + "%");
            chipText[2] = String.format("%.1f G/S", app.trainer.genRate());
        } else {
            chipText[0] = "AI";
            chipText[1] = "WARMING";
            chipText[2] = "UP";
        }
    }

    private float chipAlpha() {
        return Anim.decelerate(Anim.stagger(clock, 2, 0.09f, 0.45f));
    }

    private void drawChips(com.geometryduel.render.Shapes s, float w, float h, float unit) {
        buildChips();
        float a = chipAlpha();
        if (a <= 0f) return;
        BitmapFont f = app.fonts.label();
        float pad = 14f * unit, gap = 10f * unit;
        float ch = 30f * unit;
        float total = -gap;
        float[] ws = new float[3];
        for (int i = 0; i < 3; i++) {
            layout.setText(f, chipText[i]);
            ws[i] = layout.width + pad * 2f;
            total += ws[i] + gap;
        }
        float cx = (w - total) / 2f;
        float cy = h * 0.30f;
        s.doFill();
        s.noStroke();
        for (int i = 0; i < 3; i++) {
            s.fill(app.theme.primaryContainer.r, app.theme.primaryContainer.g, app.theme.primaryContainer.b, a);
            s.roundRect(cx, cy - ch / 2f, ws[i], ch, ch / 2f);
            cx += ws[i] + gap;
        }
    }

    private void drawChipsText(float w, float h, float unit) {
        float a = chipAlpha();
        if (a <= 0f) return;
        BitmapFont f = app.fonts.label();
        f.setColor(app.theme.onPrimaryContainer.r, app.theme.onPrimaryContainer.g, app.theme.onPrimaryContainer.b, a);
        float pad = 14f * unit, gap = 10f * unit;
        float ch = 30f * unit;
        float total = -gap;
        float[] ws = new float[3];
        for (int i = 0; i < 3; i++) {
            layout.setText(f, chipText[i]);
            ws[i] = layout.width + pad * 2f;
            total += ws[i] + gap;
        }
        float cx = (w - total) / 2f;
        float cy = h * 0.30f;
        for (int i = 0; i < 3; i++) {
            layout.setText(f, chipText[i]);
            f.draw(app.batch, chipText[i], cx + pad, cy + layout.height / 2f);
            cx += ws[i] + gap;
        }
    }

    private void drawFooter(float w, float h, float unit) {
        app.hardware.detect();
        BitmapFont f = app.fonts.label();
        f.getData().setScale(0.82f);
        f.setColor(app.theme.text.r, app.theme.text.g, app.theme.text.b, 0.45f);
        f.draw(app.batch, "GPU: " + app.hardware.gpuRenderer, 12f * unit, h - 40f * unit);
        f.draw(app.batch, "NPU: " + app.hardware.npuInfo, 12f * unit, h - 20f * unit);
        String ver = "v" + GeometryDuelGame.VERSION;
        layout.setText(f, ver);
        f.draw(app.batch, ver, w - layout.width - 12f * unit, h - 20f * unit);
        f.getData().setScale(1f);
    }

    private void layoutButtons(float w, float h, float unit) {
        float bw = Math.min(320f * unit, w * 0.78f);
        float bh = 58f * unit;
        float gap = 18f * unit;
        float cy = h * 0.46f;
        M3Button[] bs = {startBtn, tutorialBtn, settingsBtn};
        for (int i = 0; i < bs.length; i++) {
            bs[i].w = bw;
            bs[i].h = bh;
            bs[i].setCenter(w / 2f, cy + (bh + gap) * i);
            bs[i].entrance = Anim.stagger(clock - 0.1f, i, 0.09f, 0.45f);
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
