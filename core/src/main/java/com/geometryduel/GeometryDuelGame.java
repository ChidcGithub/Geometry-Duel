package com.geometryduel;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.geometryduel.neat.NeatTrainer;
import com.geometryduel.render.Shapes;
import com.geometryduel.screen.MenuScreen;

/**
 * 几何决斗（Geometry Duel）—— 对 pama1234.gdx.game.app.duel.pft01 的完整复刻。
 * 原作：FAL；安卓移植：Pama1234。
 */
public class GeometryDuelGame extends Game {
    public final boolean isAndroid;

    public Shapes shapes;
    public SpriteBatch batch;
    public BitmapFont font;

    public ThemeData theme;
    public ThemeData.Type themeType = ThemeData.Type.Light;
    public float volume = 0.5f;
    public boolean tutorialDone;

    /** NEAT AI：后台训练器（持有冠军/种群/持久化）。 */
    public NeatTrainer trainer;
    public int visionRays = 36;
    public boolean opponentNeat = true;

    public Sound sFire, lFire, longShotCharged, lFireHurt;
    public final HardwareInfo hardware = new HardwareInfo();

    public GeometryDuelGame(boolean isAndroid) {
        this.isAndroid = isAndroid;
    }

    @Override
    public void create() {
        shapes = new Shapes();
        batch = new SpriteBatch();
        font = loadFont();

        sFire = Gdx.audio.newSound(Gdx.files.internal("audio/GUNMech_Mechanical_12.ogg"));
        lFire = Gdx.audio.newSound(Gdx.files.internal("audio/LASRGun_Plasma Rifle Fire_03.ogg"));
        longShotCharged = Gdx.audio.newSound(Gdx.files.internal("audio/MECHClik_Mine Deploy_02.ogg"));
        lFireHurt = Gdx.audio.newSound(Gdx.files.internal("audio/HIT_METAL_WRENCH_HEAVIEST_02.ogg"));

        loadConfig();
        trainer = new NeatTrainer(this, visionRays);
        trainer.start();
        setScreen(new MenuScreen(this));
    }

    /**
     * 字体加载：优先使用系统自带无衬线字体（安卓 Roboto，桌面 Arial/Helvetica/DejaVu），
     * 经 FreeType 生成 16px 位图字体；全部失败时回退到 libGDX 内置 Arial 15。
     */
    private BitmapFont loadFont() {
        String[] candidates = isAndroid ? new String[] {
                "/system/fonts/Roboto-Regular.ttf",
                "/system/fonts/DroidSans.ttf",
        } : new String[] {
                "C:/Windows/Fonts/arial.ttf",
                "/System/Library/Fonts/Helvetica.ttc",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
        };
        for (String path : candidates) {
            FileHandle fh = Gdx.files.absolute(path);
            if (!fh.exists()) continue;
            try {
                FreeTypeFontGenerator gen = new FreeTypeFontGenerator(fh);
                FreeTypeFontGenerator.FreeTypeFontParameter p =
                        new FreeTypeFontGenerator.FreeTypeFontParameter();
                p.size = 16; // 与原 unifont-15 字号一致，保证各处 setScale 排版不变
                p.minFilter = Texture.TextureFilter.Linear;
                p.magFilter = Texture.TextureFilter.Linear;
                BitmapFont f = gen.generateFont(p);
                gen.dispose();
                Gdx.app.log("Font", "loaded system font: " + path);
                return f;
            } catch (Throwable t) {
                Gdx.app.error("Font", "failed to load font: " + path, t);
            }
        }
        Gdx.app.error("Font", "no usable system font, fallback to built-in Arial 15");
        return new BitmapFont();
    }

    public void applyTheme() {
        theme = themeType == ThemeData.Type.Dark ? ThemeData.dark() : ThemeData.light();
    }

    public void toggleTheme() {
        themeType = themeType == ThemeData.Type.Dark ? ThemeData.Type.Light : ThemeData.Type.Dark;
        applyTheme();
    }

    public void loadConfig() {
        Preferences p = Gdx.app.getPreferences("geometry-duel");
        themeType = "dark".equals(p.getString("theme", "light")) ? ThemeData.Type.Dark : ThemeData.Type.Light;
        volume = p.getFloat("volume", 0.5f);
        tutorialDone = p.getBoolean("tutorialDone", false);
        visionRays = p.getInteger("visionRays", 36);
        opponentNeat = p.getBoolean("opponentNeat", true);
        applyTheme();
    }

    public void saveConfig() {
        Preferences p = Gdx.app.getPreferences("geometry-duel");
        p.putString("theme", themeType == ThemeData.Type.Dark ? "dark" : "light");
        p.putFloat("volume", volume);
        p.putBoolean("tutorialDone", tutorialDone);
        p.putInteger("visionRays", visionRays);
        p.putBoolean("opponentNeat", opponentNeat);
        p.flush();
    }

    /** 清空 AI 训练成果并从零进化。 */
    public void resetAi() {
        if (trainer != null) trainer.reset(visionRays);
    }

    /** 调整视野射线数（16~64）。输入维度变化 → 重置 AI 训练。 */
    public void setVisionRays(int rays) {
        rays = Math.max(16, Math.min(64, rays));
        if (rays == visionRays) return;
        visionRays = rays;
        saveConfig();
        if (trainer != null) trainer.reset(rays);
    }

    @Override
    public void pause() {
        super.pause();
        saveConfig();
    }

    @Override
    public void dispose() {
        saveConfig();
        if (trainer != null) trainer.shutdown();
        if (getScreen() != null) getScreen().dispose();
        shapes.dispose();
        batch.dispose();
        font.dispose();
        sFire.dispose();
        lFire.dispose();
        longShotCharged.dispose();
        lFireHurt.dispose();
    }
}
