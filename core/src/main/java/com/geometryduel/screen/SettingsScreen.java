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
 * 主题 / 动态取色 / 音量 / 对手风格 / AI 速度 / 视野射线 / 重置 AI / 训练开关 / 返回。
 * 布局：9 行控件等距排列（行距 64unit > 按钮高 52unit），文本与按钮互不重叠。
 */
public class SettingsScreen extends ScreenAdapter {
    private final GeometryDuelGame app;
    private final ScreenViewport uiVp;
    private final TextButton themeBtn, volumeMinus, volumePlus, backBtn;
    private final TextButton opponentBtn, raysMinus, raysPlus, resetAiBtn, trainingBtn, speedBtn;
    private final TextButton dynamicBtn;
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
        trainingBtn = new TextButton("", 0, 0, 300, 64);
        speedBtn = new TextButton("", 0, 0, 300, 64);
        dynamicBtn = new TextButton("", 0, 0, 300, 64);
        backBtn = new TextButton("Back", 0, 0, 200, 64);
        // Metro：容器色块按钮，返回键用主色强调
        TextButton[] containers = {themeBtn, volumeMinus, volumePlus, opponentBtn,
                raysMinus, raysPlus, resetAiBtn, trainingBtn, speedBtn, dynamicBtn};
        for (TextButton b : containers) b.style = TextButton.STYLE_CONTAINER;
        backBtn.style = TextButton.STYLE_PRIMARY;
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
                } else if (dynamicBtn.contains(touch.x, touch.y)) {
                    app.toggleDynamicColor();
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
                } else if (trainingBtn.contains(touch.x, touch.y)) {
                    app.toggleTraining();
                } else if (speedBtn.contains(touch.x, touch.y)) {
                    app.aiSpeed = (app.aiSpeed + 1) % 4;
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

    /** 行中心 y（行 0..7，从顶部向下等距）。 */
    private static float rowY(float y0, float step, int i) {
        return y0 - step * i;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(app.theme.background.r, app.theme.background.g, app.theme.background.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float w = uiVp.getWorldWidth(), h = uiVp.getWorldHeight();
        float unit = h / 640f;
        float bw = Math.min(300f * unit, w * 0.8f);   // 宽按钮（窄屏收缩）
        float bh = 52f * unit;                         // 按钮高
        float sw = 44f * unit;                         // 小方按钮
        float step = 64f * unit;                       // 行距 = 按钮高 + 12 间隙
        float y0 = h * 0.86f;                          // 首行中心（9 行整体上移防底部溢出）
        float off = Math.min(110f * unit, w * 0.5f - sw - 8f * unit); // +/- 偏移

        // ---- 布局（与触摸检测共用同一坐标系）----
        themeBtn.text = "Theme: " + (app.themeType == ThemeData.Type.Dark ? "Dark" : "Light");
        themeBtn.w = bw; themeBtn.h = bh;
        themeBtn.setCenter(w / 2f, rowY(y0, step, 0));

        volumeMinus.w = volumeMinus.h = sw;
        volumePlus.w = volumePlus.h = sw;
        volumeMinus.setCenter(w / 2f - off, rowY(y0, step, 1));
        volumePlus.setCenter(w / 2f + off, rowY(y0, step, 1));

        opponentBtn.text = "Opponent: " + app.opponentStyleLabel();
        opponentBtn.w = bw; opponentBtn.h = bh;
        opponentBtn.setCenter(w / 2f, rowY(y0, step, 2));

        String[] speedLabels = {"30Hz", "20Hz", "15Hz", "12Hz"};
        speedBtn.text = "AI Speed: " + speedLabels[app.aiSpeed & 3];
        speedBtn.w = bw; speedBtn.h = bh;
        speedBtn.setCenter(w / 2f, rowY(y0, step, 3));

        raysMinus.w = raysMinus.h = sw;
        raysPlus.w = raysPlus.h = sw;
        raysMinus.setCenter(w / 2f - off, rowY(y0, step, 4));
        raysPlus.setCenter(w / 2f + off, rowY(y0, step, 4));

        resetAiBtn.w = bw; resetAiBtn.h = bh;
        resetAiBtn.setCenter(w / 2f, rowY(y0, step, 5));

        trainingBtn.text = "Training: " + (app.trainingEnabled ? "On" : "Off");
        trainingBtn.w = bw; trainingBtn.h = bh;
        trainingBtn.setCenter(w / 2f, rowY(y0, step, 6));

        dynamicBtn.text = app.themeSeed != 0
                ? "Dynamic Color: " + (app.dynamicColor ? "On" : "Off")
                : "Dynamic Color: N/A";
        dynamicBtn.w = bw; dynamicBtn.h = bh;
        dynamicBtn.setCenter(w / 2f, rowY(y0, step, 7));

        backBtn.w = Math.min(200f * unit, w * 0.6f); backBtn.h = bh;
        backBtn.setCenter(w / 2f, rowY(y0, step, 8));

        // ---- 按钮边框 ----
        uiVp.apply();
        app.shapes.begin(uiVp.getCamera());
        themeBtn.draw(app.shapes, app.theme);
        volumeMinus.draw(app.shapes, app.theme);
        volumePlus.draw(app.shapes, app.theme);
        opponentBtn.draw(app.shapes, app.theme);
        raysMinus.draw(app.shapes, app.theme);
        raysPlus.draw(app.shapes, app.theme);
        resetAiBtn.draw(app.shapes, app.theme);
        trainingBtn.draw(app.shapes, app.theme);
        speedBtn.draw(app.shapes, app.theme);
        dynamicBtn.draw(app.shapes, app.theme);
        backBtn.draw(app.shapes, app.theme);
        app.shapes.end();

        // ---- 文本 ----
        app.batch.begin();
        app.batch.setProjectionMatrix(uiVp.getCamera().combined);

        // 标题
        app.font.getData().setScale(3f * unit);
        layout.setText(app.font, "Settings");
        app.font.setColor(app.theme.text);
        app.font.draw(app.batch, "Settings", (w - layout.width) / 2f, h * 0.95f);

        // 物种信息（标题下方独立小字行）
        app.font.getData().setScale(0.85f * unit);
        String speciesInfo = app.speciesInfoText();
        layout.setText(app.font, speciesInfo);
        app.font.setColor(app.theme.text.r, app.theme.text.g, app.theme.text.b, 0.55f);
        app.font.draw(app.batch, speciesInfo, (w - layout.width) / 2f, h * 0.905f);
        app.font.setColor(app.theme.text);

        // 宽按钮文本
        themeBtn.drawText(app.batch, app.font, app.theme, 1.5f * unit);
        opponentBtn.drawText(app.batch, app.font, app.theme, 1.5f * unit);
        speedBtn.drawText(app.batch, app.font, app.theme, 1.5f * unit);
        resetAiBtn.drawText(app.batch, app.font, app.theme, 1.5f * unit);
        trainingBtn.drawText(app.batch, app.font, app.theme, 1.5f * unit);
        dynamicBtn.drawText(app.batch, app.font, app.theme, 1.5f * unit);
        backBtn.drawText(app.batch, app.font, app.theme, 1.5f * unit);

        // Volume 行：标签居中于两个方按钮之间
        volumeMinus.drawText(app.batch, app.font, app.theme, 2f * unit);
        volumePlus.drawText(app.batch, app.font, app.theme, 2f * unit);
        app.font.getData().setScale(1.3f * unit);
        String volLabel = "Volume: " + Math.round(app.volume * 100) + "%";
        layout.setText(app.font, volLabel);
        app.font.draw(app.batch, volLabel, (w - layout.width) / 2f,
                rowY(y0, step, 1) + layout.height / 2f);

        // Vision Rays 行：标签偏上 + 注释小字偏下（均在行内）
        raysMinus.drawText(app.batch, app.font, app.theme, 2f * unit);
        raysPlus.drawText(app.batch, app.font, app.theme, 2f * unit);
        app.font.getData().setScale(1.15f * unit);
        String raysLabel = "Vision Rays: " + app.visionRays;
        layout.setText(app.font, raysLabel);
        app.font.draw(app.batch, raysLabel, (w - layout.width) / 2f,
                rowY(y0, step, 4) + 10f * unit + layout.height / 2f);
        app.font.getData().setScale(0.8f * unit);
        String raysNote = "(changing resets AI)";
        layout.setText(app.font, raysNote);
        app.font.setColor(app.theme.text.r, app.theme.text.g, app.theme.text.b, 0.55f);
        app.font.draw(app.batch, raysNote, (w - layout.width) / 2f,
                rowY(y0, step, 4) - 12f * unit + layout.height / 2f);
        app.font.setColor(app.theme.text);

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
