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
import com.geometryduel.render.Shapes;
import com.geometryduel.screen.MenuScreen;

/**
 * 几何决斗（Geometry Duel）—— 对 pama1234.gdx.game.app.duel.pft01 的完整复刻。
 * 原作：FAL；安卓移植：Pama1234。
 */
public class GeometryDuelGame extends Game {
    /** 游戏内全部 UI 文本用到的非 ASCII 字符（新增界面文本时需同步补充）。 */
    private static final String FONT_CHARS =
            "中主了亮何作你使停再冲决准几击到制刻力动卓原向命" +
            "啊回型复大始学安完屏左已幕度开式态戏成或手招按摸攻教" +
            "斗新方时显普暂暗标植模次此游点版状用由界目瞄示移置自" +
            "致色蓄藏行要触设赢跳轻输过返进通重量键隐难需面音题！：";

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
        font = loadFont();

        sFire = Gdx.audio.newSound(Gdx.files.internal("audio/GUNMech_Mechanical_12.ogg"));
        lFire = Gdx.audio.newSound(Gdx.files.internal("audio/LASRGun_Plasma Rifle Fire_03.ogg"));
        longShotCharged = Gdx.audio.newSound(Gdx.files.internal("audio/MECHClik_Mine Deploy_02.ogg"));
        lFireHurt = Gdx.audio.newSound(Gdx.files.internal("audio/HIT_METAL_WRENCH_HEAVIEST_02.ogg"));

        loadConfig();
        setScreen(new MenuScreen(this));
    }

    /**
     * 字体加载：优先使用系统自带中文字体（安卓为 Noto CJK / Droid Sans Fallback，
     * 桌面为微软雅黑/苹方/Noto CJK），经 FreeType 生成位图字体；
     * 全部失败时回退到内置 unifont-15（仅含部分字符，中文会缺字）。
     */
    private BitmapFont loadFont() {
        String[] candidates = isAndroid ? new String[] {
                "/system/fonts/NotoSansSC-Regular.otf",
                "/system/fonts/NotoSansCJKsc-Regular.otf",
                "/system/fonts/NotoSansCJK-Regular.ttc",
                "/system/fonts/DroidSansFallbackFull.ttf",
                "/system/fonts/DroidSansFallback.ttf",
        } : new String[] {
                "C:/Windows/Fonts/msyh.ttc",
                "C:/Windows/Fonts/simhei.ttf",
                "C:/Windows/Fonts/simsun.ttc",
                "/System/Library/Fonts/PingFang.ttc",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
        };
        for (String path : candidates) {
            FileHandle fh = Gdx.files.absolute(path);
            if (!fh.exists()) continue;
            try {
                FreeTypeFontGenerator gen = new FreeTypeFontGenerator(fh);
                FreeTypeFontGenerator.FreeTypeFontParameter p =
                        new FreeTypeFontGenerator.FreeTypeFontParameter();
                p.size = 16; // 与原 unifont-15 字号一致，保证各处 setScale 排版不变
                p.characters = FreeTypeFontGenerator.DEFAULT_CHARS + FONT_CHARS;
                p.minFilter = Texture.TextureFilter.Linear;
                p.magFilter = Texture.TextureFilter.Linear;
                BitmapFont f = gen.generateFont(p);
                gen.dispose();
                f.getData().lineHeight = 17f; // 与原 unifont-15 行高一致，避免多行文本错位
                Gdx.app.log("Font", "loaded system font: " + path);
                return f;
            } catch (Throwable t) {
                Gdx.app.error("Font", "failed to load font: " + path, t);
            }
        }
        Gdx.app.error("Font", "no usable system CJK font, fallback to bundled unifont-15 (中文缺字)");
        return new BitmapFont(Gdx.files.internal("unifont/15/unifont-15.fnt"));
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
