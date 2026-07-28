package com.geometryduel;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.geometryduel.neat.NeatEvolver;
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
    /** 高分辨率五档字体（display/headline/title/body/label），随屏幕高度自动重建。 */
    public com.geometryduel.render.Fonts fonts;
    public static final String VERSION = "1.1.0";

    public ThemeData theme;
    public ThemeData.Type themeType = ThemeData.Type.Light;
    /** Material You 种子色（ARGB，0=无，由 Android 端从壁纸提取）。 */
    public final int themeSeed;
    /** 动态取色开关：开且有种子色时按壁纸色派生主题。 */
    public boolean dynamicColor = true;
    public float volume = 0.5f;
    public boolean tutorialDone;

    /** NEAT AI：后台训练器（持有冠军/种群/持久化）。 */
    public NeatTrainer trainer;
    public int visionRays = 36;
    /** 对手风格：-1=经典规则AI，0=总冠军，1..N=各物种冠军 */
    public int opponentStyle = 0;
    public boolean trainingEnabled = true;
    /** AI 决策速度：0=30Hz 1=20Hz 2=15Hz 3=12Hz (skipFrames = index+1)。训练线程也会读取。 */
    public volatile int aiSpeed = 1;  // default 20Hz

    public Sound sFire, lFire, longShotCharged, lFireHurt;
    public final HardwareInfo hardware = new HardwareInfo();

    public GeometryDuelGame(boolean isAndroid) {
        this(isAndroid, 0);
    }

    public GeometryDuelGame(boolean isAndroid, int themeSeed) {
        this.isAndroid = isAndroid;
        this.themeSeed = themeSeed;
    }

    @Override
    public void create() {
        shapes = new Shapes();
        batch = new SpriteBatch();
        fonts = new com.geometryduel.render.Fonts(isAndroid);
        fonts.ensureBuilt();

        sFire = Gdx.audio.newSound(Gdx.files.internal("audio/GUNMech_Mechanical_12.ogg"));
        lFire = Gdx.audio.newSound(Gdx.files.internal("audio/LASRGun_Plasma Rifle Fire_03.ogg"));
        longShotCharged = Gdx.audio.newSound(Gdx.files.internal("audio/MECHClik_Mine Deploy_02.ogg"));
        lFireHurt = Gdx.audio.newSound(Gdx.files.internal("audio/HIT_METAL_WRENCH_HEAVIEST_02.ogg"));

        loadConfig();
        trainer = new NeatTrainer(this, visionRays);
        trainer.start();
        trainer.setPaused(!trainingEnabled); // 尊重训练开关设置
        setScreen(new MenuScreen(this));
    }

    @Override
    public void render() {
        // 屏幕高度变化（旋转/分屏/窗口缩放）时按新物理像素重建字体档
        if (fonts != null) fonts.ensureBuilt();
        super.render();
    }

    public void applyTheme() {
        int seed = dynamicColor ? themeSeed : 0;
        theme = themeType == ThemeData.Type.Dark ? ThemeData.dark(seed) : ThemeData.light(seed);
    }

    public void toggleTheme() {
        themeType = themeType == ThemeData.Type.Dark ? ThemeData.Type.Light : ThemeData.Type.Dark;
        applyTheme();
    }

    /** 动态取色开关（无种子色的平台不可切换）。 */
    public void toggleDynamicColor() {
        if (themeSeed == 0) return;
        dynamicColor = !dynamicColor;
        applyTheme();
    }

    public void loadConfig() {
        Preferences p = Gdx.app.getPreferences("geometry-duel");
        themeType = "dark".equals(p.getString("theme", "light")) ? ThemeData.Type.Dark : ThemeData.Type.Light;
        volume = p.getFloat("volume", 0.5f);
        tutorialDone = p.getBoolean("tutorialDone", false);
        visionRays = p.getInteger("visionRays", 36);
        opponentStyle = p.getInteger("opponentStyle", 0);
        trainingEnabled = p.getBoolean("trainingEnabled", true);
        aiSpeed = Math.max(0, Math.min(3, p.getInteger("aiSpeed", 1)));
        dynamicColor = p.getBoolean("dynamicColor", true);
        applyTheme();
    }

    public void saveConfig() {
        Preferences p = Gdx.app.getPreferences("geometry-duel");
        p.putString("theme", themeType == ThemeData.Type.Dark ? "dark" : "light");
        p.putFloat("volume", volume);
        p.putBoolean("tutorialDone", tutorialDone);
        p.putInteger("visionRays", visionRays);
        p.putInteger("opponentStyle", opponentStyle);
        p.putBoolean("trainingEnabled", trainingEnabled);
        p.putInteger("aiSpeed", aiSpeed);
        p.putBoolean("dynamicColor", dynamicColor);
        p.flush();
    }

    /** 切换后台训练开关 */
    public void toggleTraining() {
        trainingEnabled = !trainingEnabled;
        if (trainer != null) trainer.setPaused(!trainingEnabled);
    }
    public void resetAi() {
        if (trainer != null) trainer.requestReset(visionRays);
    }

    /** 设置界面显示的物种信息 */
    public String speciesInfoText() {
        if (trainer == null) return "AI not started";
        NeatEvolver ev = trainer.evolver();
        if (ev == null || ev.currentSpecies.isEmpty())
            return "Gen " + trainer.generation() + " | Species: 0 (training...)";
        String[] labels = trainer.speciesStyleLabels();
        StringBuilder sb = new StringBuilder("Gen " + trainer.generation()
                + " | Species: " + labels.length);
        for (int i = 0; i < Math.min(labels.length, 3); i++)
            sb.append(" ").append(labels[i]);
        if (labels.length > 3) sb.append(" ...");
        return sb.toString();
    }
    public String opponentStyleLabel() {
        if (opponentStyle < 0) return "Classic";
        if (opponentStyle == 0) return "Champion";
        if (trainer != null) {
            String[] labels = trainer.speciesStyleLabels();
            int idx = opponentStyle - 1;
            if (idx >= 0 && idx < labels.length) return labels[idx];
        }
        return "Champion";
    }

    /** 循环切换对手风格 */
    public void cycleOpponentStyle() {
        int maxStyle = 0; // 0 = Champion
        if (trainer != null) maxStyle = trainer.speciesStyleLabels().length;
        opponentStyle++;
        if (opponentStyle > maxStyle) opponentStyle = -1; // wrap to Classic
    }

    /** 调整视野射线数（16~64）。输入维度变化 → 重置 AI 训练。 */
    public void setVisionRays(int rays) {
        rays = Math.max(16, Math.min(64, rays));
        if (rays == visionRays) return;
        visionRays = rays;
        saveConfig();
        if (trainer != null) trainer.requestReset(rays);
    }

    @Override
    public void pause() {
        super.pause();
        saveConfig();
        if (trainer != null) trainer.setPaused(true); // 息屏暂停训练，避免耗电
    }

    @Override
    public void resume() {
        super.resume();
        // 恢复训练：训练开关开且当前不在玩家对战中（对战的暂停由 GameScreen 管理）
        if (trainer != null && trainingEnabled) {
            boolean inBattle = getScreen() instanceof com.geometryduel.screen.GameScreen
                    && ((com.geometryduel.screen.GameScreen) getScreen()).isBattleActive();
            trainer.setPaused(inBattle);
        }
    }

    @Override
    public void dispose() {
        saveConfig();
        if (trainer != null) trainer.shutdown();
        if (getScreen() != null) getScreen().dispose();
        shapes.dispose();
        batch.dispose();
        if (fonts != null) fonts.dispose();
        sFire.dispose();
        lFire.dispose();
        longShotCharged.dispose();
        lFireHurt.dispose();
    }
}
