package com.geometryduel.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.geometryduel.GeometryDuelGame;
import com.geometryduel.ThemeData;
import com.geometryduel.ui.Anim;
import com.geometryduel.ui.M3Button;
import com.geometryduel.ui.M3Slider;
import com.geometryduel.ui.M3Switch;

/**
 * 设置（M3 Expressive 重建）：
 * - 分区卡片：外观 / 对局 / AI 训练 / 关于，stagger 上浮入场
 * - 真控件：M3Switch（主题/动态取色/训练）、M3Slider（音量）、步进器（射线）、循环按钮（对手/速度）
 * - AI 训练卡片内含实时信息区（Gen/WR/G-S/物种/模拟局数/幽灵）
 * - 内容超出屏幕时垂直滚动：绘制用矩阵平移，命中检测用同一坐标换算，ScissorStack 裁剪
 */
public class SettingsScreen extends ScreenAdapter {
    private final GeometryDuelGame app;
    private final ScreenViewport uiVp;
    private final GlyphLayout layout = new GlyphLayout();
    private final Vector3 touch = new Vector3();
    private final Matrix4 identity = new Matrix4();

    // 顶栏
    private final M3Button backBtn = new M3Button("< Back", 0, 0);

    // 外观
    private final M3Switch darkSwitch = new M3Switch(false);
    private final M3Switch dynamicSwitch = new M3Switch(true);

    // 对局
    private final M3Slider volumeSlider = new M3Slider();
    private final M3Button opponentBtn = new M3Button("", 0, 0);
    private final M3Button speedBtn = new M3Button("", 0, 0);

    // AI 训练
    private final M3Switch trainingSwitch = new M3Switch(true);
    private final M3Button raysMinus = new M3Button("-", 0, 0);
    private final M3Button raysPlus = new M3Button("+", 0, 0);
    private final M3Button resetBtn = new M3Button("Reset AI", 0, 0);
    private float resetFlashT; // >0 时 Reset 按钮显示 Done!

    // 滚动
    private float scrollY, maxScroll;
    private float dragLastY = -1f;
    private float clock;

    // 布局结果（内容坐标，绘制与触摸检测共用）
    private float unit, cardX, cardW, cardPad, rowH, contentTop;
    private final float[] cardY = new float[4];
    private final float[] cardH = new float[4];
    private float rowYAppearance1, rowYAppearance2;
    private float rowYVolume, rowYOpponent, rowYSpeed;
    private float rowYTraining, rowYRays, rowYReset;
    private float infoStartY;

    public SettingsScreen(GeometryDuelGame app) {
        this.app = app;
        uiVp = new ScreenViewport();
        backBtn.style = M3Button.OUTLINE;
        opponentBtn.style = M3Button.OUTLINE;
        speedBtn.style = M3Button.OUTLINE;
        raysMinus.style = M3Button.TONAL;
        raysPlus.style = M3Button.TONAL;
        resetBtn.style = M3Button.DANGER;
    }

    @Override
    public void show() {
        clock = 0f;
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int x, int y, int pointer, int button) {
                touch.set(x, y, 0);
                uiVp.unproject(touch);
                float tx = touch.x, ty = touch.y;
                if (backBtn.contains(tx, ty)) { back(); return true; }
                // 屏幕 y → 内容 y
                float cy = ty + scrollY - contentTop;
                if (darkSwitch.contains(tx, cy)) { darkSwitch.toggle(); app.toggleTheme(); return true; }
                if (dynamicSwitch.enabled && dynamicSwitch.contains(tx, cy)) {
                    dynamicSwitch.toggle(); app.toggleDynamicColor(); return true;
                }
                if (volumeSlider.onTouchDown(tx, cy)) { app.volume = volumeSlider.value; return true; }
                if (hitButton(opponentBtn, tx, cy)) { opponentBtn.setPressed(true); app.cycleOpponentStyle(); return true; }
                if (hitButton(speedBtn, tx, cy)) { speedBtn.setPressed(true); app.aiSpeed = (app.aiSpeed + 1) % 4; return true; }
                if (trainingSwitch.contains(tx, cy)) { trainingSwitch.toggle(); app.toggleTraining(); return true; }
                if (hitButton(raysMinus, tx, cy)) { raysMinus.setPressed(true); app.setVisionRays(app.visionRays - 4); return true; }
                if (hitButton(raysPlus, tx, cy)) { raysPlus.setPressed(true); app.setVisionRays(app.visionRays + 4); return true; }
                if (hitButton(resetBtn, tx, cy)) { resetBtn.setPressed(true); app.resetAi(); resetFlashT = 1.2f; return true; }
                dragLastY = ty;
                return true;
            }

            @Override
            public boolean touchDragged(int x, int y, int pointer) {
                touch.set(x, y, 0);
                uiVp.unproject(touch);
                if (volumeSlider.isDragging()) {
                    volumeSlider.onDrag(touch.x);
                    app.volume = volumeSlider.value;
                    return true;
                }
                if (dragLastY >= 0f) {
                    scrollY -= (touch.y - dragLastY);
                    scrollY = Math.max(0f, Math.min(maxScroll, scrollY));
                    dragLastY = touch.y;
                }
                return true;
            }

            @Override
            public boolean touchUp(int x, int y, int pointer, int button) {
                volumeSlider.onUp();
                opponentBtn.setPressed(false);
                speedBtn.setPressed(false);
                raysMinus.setPressed(false);
                raysPlus.setPressed(false);
                resetBtn.setPressed(false);
                dragLastY = -1f;
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

    private boolean hitButton(M3Button b, float tx, float cy) {
        return b.visible && b.enabled && tx >= b.x && tx <= b.x + b.w && cy >= b.y && cy <= b.y + b.h;
    }

    private void back() {
        app.saveConfig();
        app.setScreen(new MenuScreen(app));
    }

    @Override
    public void render(float delta) {
        clock += delta;
        if (resetFlashT > 0f) resetFlashT -= delta;

        float w = uiVp.getWorldWidth(), h = uiVp.getWorldHeight();
        unit = h / 640f;
        layoutContent(w, h);

        // 控件状态同步
        darkSwitch.value = app.themeType == ThemeData.Type.Dark;
        dynamicSwitch.value = app.dynamicColor;
        dynamicSwitch.enabled = app.themeSeed != 0;
        trainingSwitch.value = app.trainingEnabled;
        if (!volumeSlider.isDragging()) volumeSlider.value = app.volume;

        darkSwitch.update(delta);
        dynamicSwitch.update(delta);
        trainingSwitch.update(delta);
        volumeSlider.update(delta);
        opponentBtn.update(delta);
        speedBtn.update(delta);
        raysMinus.update(delta);
        raysPlus.update(delta);
        resetBtn.update(delta);
        backBtn.update(delta);

        Gdx.gl.glClearColor(app.theme.background.r, app.theme.background.g, app.theme.background.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // ---- 滚动内容（矩阵平移到屏幕坐标，裁剪到顶栏以下）----
        float dy = contentTop - scrollY;
        Rectangle area = new Rectangle(0, 0, w, h - contentTop);
        Rectangle scissor = new Rectangle();
        ScissorStack.calculateScissors(uiVp.getCamera(), identity, area, scissor);
        boolean pushed = ScissorStack.pushScissors(scissor);

        app.shapes.begin(uiVp.getCamera());
        app.shapes.push();
        app.shapes.translate(0f, dy);
        drawCardsShape();
        drawControlsShape();
        app.shapes.pop();
        app.shapes.end();

        app.batch.begin();
        app.batch.setProjectionMatrix(uiVp.getCamera().combined);
        app.batch.getTransformMatrix().translate(0f, dy, 0f);
        drawCardsText();
        drawControlsText();
        app.batch.setTransformMatrix(identity);
        app.batch.end();

        if (pushed) ScissorStack.popScissors();

        // ---- 固定顶栏 ----
        app.shapes.begin(uiVp.getCamera());
        backBtn.draw(app.shapes, app.theme);
        app.shapes.end();
        app.batch.begin();
        app.batch.setProjectionMatrix(uiVp.getCamera().combined);
        BitmapFont hf = app.fonts.headline();
        hf.setColor(app.theme.text);
        layout.setText(hf, "Settings");
        hf.draw(app.batch, "Settings", (w - layout.width) / 2f, contentTop * 0.62f + layout.height / 2f);
        backBtn.drawText(app.batch, app.fonts, app.theme);
        app.batch.end();
    }

    // ------------------------------------------------------------ 布局（内容坐标）

    private void layoutContent(float w, float h) {
        float margin = 20f * unit;
        rowH = 52f * unit;
        cardPad = 14f * unit;
        float rowGap = 6f * unit;
        float sectionTitleH = 30f * unit;
        float sectionGap = 20f * unit;
        cardW = Math.min(w - margin * 2f, 560f * unit);
        cardX = (w - cardW) / 2f;
        float infoLineH = 24f * unit;

        contentTop = 64f * unit;
        backBtn.w = 110f * unit;
        backBtn.h = 42f * unit;
        backBtn.setCenter(margin + backBtn.w / 2f, contentTop * 0.62f);
        backBtn.entrance = Anim.stagger(clock, 0, 0f, 0.3f);

        // 动态文本（测量前更新）
        opponentBtn.text = app.opponentStyleLabel();
        String[] speedLabels = {"30Hz", "20Hz", "15Hz", "12Hz"};
        speedBtn.text = speedLabels[app.aiSpeed & 3];
        resetBtn.text = resetFlashT > 0f ? "Done!" : "Reset AI";

        float[] rises = new float[4];
        for (int i = 0; i < 4; i++) {
            float e = Anim.decelerate(Anim.stagger(clock - 0.05f, i, 0.08f, 0.4f));
            rises[i] = (1f - e) * 20f * unit;
        }

        float y = 12f * unit;

        // 外观：2 行
        y += sectionTitleH;
        cardY[0] = y;
        rowYAppearance1 = y + cardPad + rises[0];
        rowYAppearance2 = rowYAppearance1 + rowH + rowGap;
        cardH[0] = cardPad * 2f + rowH * 2f + rowGap;
        y += cardH[0] + sectionGap;

        // 对局：3 行
        y += sectionTitleH;
        cardY[1] = y;
        rowYVolume = y + cardPad + rises[1];
        rowYOpponent = rowYVolume + rowH + rowGap;
        rowYSpeed = rowYOpponent + rowH + rowGap;
        cardH[1] = cardPad * 2f + rowH * 3f + rowGap * 2f;
        y += cardH[1] + sectionGap;

        // AI 训练：3 控件行 + 3 信息行
        y += sectionTitleH;
        cardY[2] = y;
        rowYTraining = y + cardPad + rises[2];
        rowYRays = rowYTraining + rowH + rowGap;
        rowYReset = rowYRays + rowH + rowGap;
        infoStartY = rowYReset + rowH + 10f * unit;
        cardH[2] = cardPad * 2f + rowH * 3f + rowGap * 2f + infoLineH * 3f + 10f * unit;
        y += cardH[2] + sectionGap;

        // 关于：4 信息行
        y += sectionTitleH;
        cardY[3] = y;
        cardH[3] = cardPad * 2f + infoLineH * 4f;
        y += cardH[3] + 24f * unit;

        // 控件几何
        float ctrlRight = cardX + cardW - cardPad;

        darkSwitch.w = 52f * unit;
        darkSwitch.h = 32f * unit;
        darkSwitch.x = ctrlRight - darkSwitch.w;
        darkSwitch.y = rowYAppearance1 + (rowH - darkSwitch.h) / 2f;
        dynamicSwitch.w = darkSwitch.w;
        dynamicSwitch.h = darkSwitch.h;
        dynamicSwitch.x = darkSwitch.x;
        dynamicSwitch.y = rowYAppearance2 + (rowH - darkSwitch.h) / 2f;

        volumeSlider.w = Math.min(190f * unit, cardW * 0.45f);
        volumeSlider.x = ctrlRight - volumeSlider.w;
        volumeSlider.y = rowYVolume + rowH / 2f;
        volumeSlider.trackH = 6f * unit;
        volumeSlider.thumbR = 11f * unit;
        volumeSlider.touchH = rowH;

        layoutPill(opponentBtn, ctrlRight, rowYOpponent);
        layoutPill(speedBtn, ctrlRight, rowYSpeed);

        trainingSwitch.w = darkSwitch.w;
        trainingSwitch.h = darkSwitch.h;
        trainingSwitch.x = darkSwitch.x;
        trainingSwitch.y = rowYTraining + (rowH - darkSwitch.h) / 2f;

        float sw = 44f * unit;
        raysMinus.w = raysPlus.w = sw;
        raysMinus.h = raysPlus.h = sw;
        raysMinus.x = ctrlRight - sw * 2f - 64f * unit;
        raysMinus.y = rowYRays + (rowH - sw) / 2f;
        raysPlus.x = ctrlRight - sw;
        raysPlus.y = raysMinus.y;

        layoutPill(resetBtn, ctrlRight, rowYReset);

        maxScroll = Math.max(0f, y - (h - contentTop));
        scrollY = Math.max(0f, Math.min(maxScroll, scrollY));
    }

    private void layoutPill(M3Button b, float rightX, float rowY) {
        BitmapFont f = app.fonts.body();
        layout.setText(f, b.text);
        b.w = Math.max(96f * unit, layout.width + 40f * unit);
        b.h = 42f * unit;
        b.x = rightX - b.w;
        b.y = rowY + (rowH - b.h) / 2f;
        b.entrance = 1f;
    }

    // ------------------------------------------------------------ 绘制（内容坐标，由矩阵平移）

    private void drawCardsShape() {
        float radius = 20f * unit;
        for (int i = 0; i < 4; i++) {
            float e = Anim.decelerate(Anim.stagger(clock - 0.05f, i, 0.08f, 0.4f));
            if (e <= 0f) continue;
            app.shapes.doFill();
            app.shapes.noStroke();
            app.shapes.fill(app.theme.surfaceVariant.r, app.theme.surfaceVariant.g,
                    app.theme.surfaceVariant.b, 0.45f * e);
            app.shapes.roundRect(cardX, cardY[i], cardW, cardH[i], radius);
        }
    }

    private void drawControlsShape() {
        darkSwitch.draw(app.shapes, app.theme);
        dynamicSwitch.draw(app.shapes, app.theme);
        trainingSwitch.draw(app.shapes, app.theme);
        volumeSlider.draw(app.shapes, app.theme);
        opponentBtn.draw(app.shapes, app.theme);
        speedBtn.draw(app.shapes, app.theme);
        raysMinus.draw(app.shapes, app.theme);
        raysPlus.draw(app.shapes, app.theme);
        resetBtn.draw(app.shapes, app.theme);
    }

    private void drawCardsText() {
        BitmapFont title = app.fonts.title();
        BitmapFont body = app.fonts.body();
        BitmapFont label = app.fonts.label();
        float lx = cardX + cardPad + 6f * unit;

        title.getData().setScale(0.72f);
        title.setColor(app.theme.primary);
        for (int i = 0; i < 4; i++) {
            String s = i == 0 ? "APPEARANCE" : i == 1 ? "GAMEPLAY" : i == 2 ? "AI TRAINING" : "ABOUT";
            title.draw(app.batch, s, cardX + 4f * unit, cardY[i] - 8f * unit);
        }
        title.getData().setScale(1f);

        body.setColor(app.theme.text);
        rowLabel(body, "Dark Theme", lx, rowYAppearance1 + rowH / 2f);
        rowLabel(body, "Dynamic Color" + (app.themeSeed == 0 ? " (N/A)" : ""), lx, rowYAppearance2 + rowH / 2f);
        rowLabel(body, "Volume", lx, rowYVolume + rowH / 2f);
        rowLabel(body, "Opponent", lx, rowYOpponent + rowH / 2f);
        rowLabel(body, "AI Speed", lx, rowYSpeed + rowH / 2f);
        rowLabel(body, "Background Training", lx, rowYTraining + rowH / 2f);
        rowLabel(body, "Vision Rays", lx, rowYRays + rowH / 2f);
        rowLabel(body, "Danger Zone", lx, rowYReset + rowH / 2f);

        // 行内值文本
        body.setColor(app.theme.text.r, app.theme.text.g, app.theme.text.b, 0.75f);
        String vol = Math.round(app.volume * 100) + "%";
        layout.setText(body, vol);
        body.draw(app.batch, vol, volumeSlider.x - layout.width - 14f * unit,
                rowYVolume + rowH / 2f + layout.height / 2f);
        String rays = String.valueOf(app.visionRays);
        layout.setText(body, rays);
        body.draw(app.batch, rays, raysMinus.x + raysMinus.w + 18f * unit,
                rowYRays + rowH / 2f + layout.height / 2f);
    }

    private void drawControlsText() {
        opponentBtn.drawText(app.batch, app.fonts, app.theme);
        speedBtn.drawText(app.batch, app.fonts, app.theme);
        raysMinus.drawText(app.batch, app.fonts, app.theme);
        raysPlus.drawText(app.batch, app.fonts, app.theme);
        resetBtn.drawText(app.batch, app.fonts, app.theme);

        // AI 信息区
        BitmapFont label = app.fonts.label();
        label.setColor(app.theme.onSurfaceVariant);
        float lx = cardX + cardPad + 6f * unit;
        if (app.trainer != null) {
            float wr = app.trainer.championWinRate();
            label.draw(app.batch, "Gen " + app.trainer.generation()
                    + "  ·  WR " + (wr < 0 ? "--" : Math.round(wr * 100) + "%")
                    + "  ·  " + String.format("%.1f g/s", app.trainer.genRate()), lx, infoStartY + 24f * unit);
            label.draw(app.batch, app.speciesInfoText(), lx, infoStartY + 48f * unit);
            label.draw(app.batch, "Sims " + app.trainer.simMatches() + "  ·  Ghosts " + app.trainer.ghostCount()
                    + (app.trainer.isConverged() ? "  ·  Converged" : ""), lx, infoStartY + 72f * unit);
        } else {
            label.draw(app.batch, "Trainer not started", lx, infoStartY + 24f * unit);
        }

        // 关于
        app.hardware.detect();
        float ay = cardY[3] + cardPad + 8f * unit;
        label.draw(app.batch, "GPU: " + app.hardware.gpuRenderer, lx, ay);
        label.draw(app.batch, "NPU: " + app.hardware.npuInfo, lx, ay + 24f * unit);
        label.draw(app.batch, "Version " + GeometryDuelGame.VERSION, lx, ay + 48f * unit);
        label.draw(app.batch, "Made by FAL · Android port by Pama1234 · Unofficial remake", lx, ay + 72f * unit);
    }

    private void rowLabel(BitmapFont f, String s, float x, float centerY) {
        layout.setText(f, s);
        f.draw(app.batch, s, x, centerY + layout.height / 2f);
    }

    @Override
    public void resize(int width, int height) {
        uiVp.update(width, height, true);
    }

    @Override
    public void dispose() {
    }
}
