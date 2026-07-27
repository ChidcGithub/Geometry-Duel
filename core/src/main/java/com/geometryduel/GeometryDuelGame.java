package com.geometryduel;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
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

    public Sound sFire, lFire, longShotCharged, lFireHurt;

    public GeometryDuelGame(boolean isAndroid) {
        this.isAndroid = isAndroid;
    }

    @Override
    public void create() {
        shapes = new Shapes();
        batch = new SpriteBatch();
        font = new BitmapFont(Gdx.files.internal("unifont/15/unifont-15.fnt"));

        sFire = Gdx.audio.newSound(Gdx.files.internal("audio/GUNMech_Mechanical_12.ogg"));
        lFire = Gdx.audio.newSound(Gdx.files.internal("audio/LASRGun_Plasma Rifle Fire_03.ogg"));
        longShotCharged = Gdx.audio.newSound(Gdx.files.internal("audio/MECHClik_Mine Deploy_02.ogg"));
        lFireHurt = Gdx.audio.newSound(Gdx.files.internal("audio/HIT_METAL_WRENCH_HEAVIEST_02.ogg"));

        loadConfig();
        setScreen(new MenuScreen(this));
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
        applyTheme();
    }

    public void saveConfig() {
        Preferences p = Gdx.app.getPreferences("geometry-duel");
        p.putString("theme", themeType == ThemeData.Type.Dark ? "dark" : "light");
        p.putFloat("volume", volume);
        p.putBoolean("tutorialDone", tutorialDone);
        p.flush();
    }

    @Override
    public void pause() {
        super.pause();
        saveConfig();
    }

    @Override
    public void dispose() {
        saveConfig();
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
