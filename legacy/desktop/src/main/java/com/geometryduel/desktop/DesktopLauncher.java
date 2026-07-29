package com.geometryduel.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.geometryduel.GeometryDuelGame;

public class DesktopLauncher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Geometry Duel");
        config.setWindowedMode(960, 640);
        config.useVsync(true);
        config.setForegroundFPS(60);
        new Lwjgl3Application(new GeometryDuelGame(false), config);
    }
}
