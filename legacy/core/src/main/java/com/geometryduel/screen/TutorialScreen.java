package com.geometryduel.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.geometryduel.GeometryDuelGame;
import com.geometryduel.render.Shapes;
import com.geometryduel.ui.M3Button;

/**
 * 教学模式（M3 Expressive）：
 * - 初始难度 0.02，每从演示进入实战一次 +0.2，超过 1.0 即完成
 * - 顶部信息卡：标题 / 难度进度条 / 剩余轮次；右上角 Skip 按钮
 */
public class TutorialScreen extends GameScreen {
    private static final float LEVEL_STEP = 0.2f;

    private float level = 0.02f;
    private final M3Button skipBtn = new M3Button("Skip", 0, 0);
    private final GlyphLayout layout = new GlyphLayout();

    {
        skipBtn.style = M3Button.OUTLINE;
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
        skipBtn.update(delta);
        float w = uiVp.getWorldWidth(), h = uiVp.getWorldHeight();
        float unit = h / 640f;
        skipBtn.w = 108f * unit;
        skipBtn.h = 44f * unit;
        skipBtn.setCenter(w - skipBtn.w / 2f - 10f, h - skipBtn.h / 2f - 64f * unit);
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
        float w = uiVp.getWorldWidth(), h = uiVp.getWorldHeight();
        float unit = h / 640f;
        // 难度进度条（M3 linear progress）
        float px = 12f * unit, py = h - 152f * unit;
        float pw = 190f * unit, ph = 6f * unit;
        float progress = Math.min(1f, level / 1.0f);
        s.doFill();
        s.noStroke();
        s.fill(app.theme.surfaceVariant.r, app.theme.surfaceVariant.g, app.theme.surfaceVariant.b, 0.9f);
        s.roundRect(px, py, pw, ph, ph / 2f);
        if (progress > 0.001f) {
            s.fill(app.theme.primary);
            s.roundRect(px, py, pw * progress, ph, ph / 2f);
        }
        skipBtn.draw(s, app.theme);
    }

    @Override
    protected void drawExtraHud(float w, float h, float unit) {
        BitmapFont title = app.fonts.title();
        BitmapFont label = app.fonts.label();
        float x = unit * 12f;
        float y = h - unit * 96f;

        title.setColor(app.theme.text);
        title.draw(app.batch, "Tutorial", x, y);

        label.setColor(app.theme.onSurfaceVariant);
        label.draw(app.batch, "Level " + String.format(java.util.Locale.US, "%.1f", level)
                + (app.tutorialDone ? "  ·  Done" : ""), x, y + 22f * unit);
        int remain = (int) Math.ceil((1.0f - level) / LEVEL_STEP);
        label.draw(app.batch, Math.max(0, remain) + " rounds to go", x, y + 64f * unit);

        skipBtn.drawText(app.batch, app.fonts, app.theme);
    }
}
