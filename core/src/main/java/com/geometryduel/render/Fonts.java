package com.geometryduel.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;

/**
 * 高分辨率字体管理（修复旧实现 16px 位图放大数倍导致的模糊）：
 * 按屏幕物理像素为五档字号各生成一个独立 BitmapFont，绘制时一律 scale=1。
 * 屏幕高度变化（旋转/分屏/窗口缩放）时自动重建。
 *
 * 档位（以 640 设计高度为基准的 dp）：
 *   DISPLAY  88   主菜单超大标题
 *   HEADLINE 46   屏标题 / 结算大字
 *   TITLE    30   卡片标题 / 倒计时
 *   BODY     23   正文 / 按钮
 *   LABEL    17   辅助说明小字
 */
public class Fonts {
    private static final int SZ_DISPLAY = 88;
    private static final int SZ_HEADLINE = 46;
    private static final int SZ_TITLE = 30;
    private static final int SZ_BODY = 23;
    private static final int SZ_LABEL = 17;

    private final boolean isAndroid;
    private BitmapFont display, headline, title, body, label;
    private int builtForHeight = -1;

    public Fonts(boolean isAndroid) {
        this.isAndroid = isAndroid;
    }

    /** 每帧调用：仅当屏幕高度变化时重建（int 比较，开销可忽略）。 */
    public void ensureBuilt() {
        int h = Gdx.graphics.getHeight();
        if (h > 0 && h != builtForHeight) build(h);
    }

    private void build(int screenH) {
        disposeFonts();
        float unitPx = screenH / 640f;
        FileHandle fh = resolveFontFile();
        if (fh != null) {
            FreeTypeFontGenerator gen = null;
            try {
                gen = new FreeTypeFontGenerator(fh);
                display = generate(gen, SZ_DISPLAY, unitPx);
                headline = generate(gen, SZ_HEADLINE, unitPx);
                title = generate(gen, SZ_TITLE, unitPx);
                body = generate(gen, SZ_BODY, unitPx);
                label = generate(gen, SZ_LABEL, unitPx);
            } catch (Throwable t) {
                Gdx.app.error("Fonts", "freetype build failed, fallback to builtin", t);
                disposeFonts();
            } finally {
                if (gen != null) gen.dispose();
            }
        }
        if (body == null) {
            display = headline = title = body = label = new BitmapFont();
        }
        builtForHeight = screenH;
        Gdx.app.log("Fonts", "built for height " + screenH + " (unitPx=" + unitPx + ")");
    }

    private static BitmapFont generate(FreeTypeFontGenerator gen, int sizeDp, float unitPx) {
        FreeTypeFontGenerator.FreeTypeFontParameter p =
                new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = Math.max(8, Math.round(sizeDp * unitPx));
        p.minFilter = Texture.TextureFilter.Linear;
        p.magFilter = Texture.TextureFilter.Linear;
        return gen.generateFont(p);
    }

    private FileHandle resolveFontFile() {
        String[] candidates = isAndroid ? new String[]{
                "/system/fonts/Roboto-Regular.ttf",
                "/system/fonts/DroidSans.ttf",
        } : new String[]{
                "C:/Windows/Fonts/arial.ttf",
                "/System/Library/Fonts/Helvetica.ttc",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
        };
        for (String path : candidates) {
            FileHandle fh = Gdx.files.absolute(path);
            if (fh.exists()) return fh;
        }
        Gdx.app.error("Fonts", "no usable system font");
        return null;
    }

    public BitmapFont display() { return display; }
    public BitmapFont headline() { return headline; }
    public BitmapFont title() { return title; }
    public BitmapFont body() { return body; }
    public BitmapFont label() { return label; }

    private void disposeFonts() {
        // builtin 回退时五档为同一实例，只 dispose 一次；freetype 档各自独立
        if (body != null && body == display) {
            body.dispose();
        } else {
            if (display != null) display.dispose();
            if (headline != null) headline.dispose();
            if (title != null) title.dispose();
            if (body != null) body.dispose();
            if (label != null) label.dispose();
        }
        display = headline = title = body = label = null;
    }

    public void dispose() {
        disposeFonts();
        builtForHeight = -1;
    }
}
