package com.geometryduel.android;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.os.Build;
import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.geometryduel.GeometryDuelGame;

public class AndroidLauncher extends AndroidApplication {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        CrashHandler.install(this);
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useAccelerometer = false;
        config.useCompass = false;
        config.useGyroscope = false;
        initialize(new GeometryDuelGame(true, extractThemeSeed()), config);
    }

    /**
     * Material You 动态取色：提取壁纸种子色（ARGB）。
     * API 31+ 读系统动态色 system_accent1_500；API 27~30 回退 WallpaperColors；
     * 更低版本或提取失败返回 0（core 使用默认紫）。
     */
    private int extractThemeSeed() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                return getColor(android.R.color.system_accent1_500);
            }
            if (Build.VERSION.SDK_INT >= 27) {
                WallpaperManager wm = WallpaperManager.getInstance(this);
                WallpaperColors colors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                if (colors != null && colors.getPrimaryColor() != null) {
                    return colors.getPrimaryColor().toArgb();
                }
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }
}
