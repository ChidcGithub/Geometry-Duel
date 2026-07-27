package com.geometryduel.ui;

import android.app.Activity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.TextView;

import com.geometryduel.R;
import com.geometryduel.game.GameMode;
import com.geometryduel.game.GameView;

import java.io.PrintWriter;
import java.io.StringWriter;

public class MainActivity extends Activity {

    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            String crashLog = sw.toString();

            runOnUiThread(() -> {
                TextView tv = new TextView(MainActivity.this);
                tv.setText("CRASH\n\n" + crashLog);
                tv.setTextColor(0xFFFFFFFF);
                tv.setBackgroundColor(0xFF1A0000);
                tv.setTextSize(13f);
                tv.setPadding(32, 32, 32, 32);
                tv.setMovementMethod(new ScrollingMovementMethod());
                tv.setOnLongClickListener(v -> {
                    android.content.ClipboardManager clipboard =
                        (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    android.content.ClipData clip =
                        android.content.ClipData.newPlainText("crash", crashLog);
                    clipboard.setPrimaryClip(clip);
                    return true;
                });
                setContentView(tv);
            });
        });

        try {
            setContentView(R.layout.activity_main);

            String modeStr = getIntent().getStringExtra("game_mode");
            GameMode gameMode = GameMode.valueOf(modeStr != null ? modeStr : GameMode.PLAYER_VS_AI.name());

            gameView = findViewById(R.id.game_view);
            gameView.setGameMode(gameMode);
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String crashLog = sw.toString();

            TextView tv = new TextView(this);
            tv.setText("CRASH IN ONCREATE\n\n" + crashLog);
            tv.setTextColor(0xFFFFFFFF);
            tv.setBackgroundColor(0xFF1A0000);
            tv.setTextSize(13f);
            tv.setPadding(32, 32, 32, 32);
            tv.setMovementMethod(new ScrollingMovementMethod());
            String finalLog = crashLog;
            tv.setOnLongClickListener(v -> {
                android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                android.content.ClipData clip =
                    android.content.ClipData.newPlainText("crash", finalLog);
                clipboard.setPrimaryClip(clip);
                return true;
            });
            setContentView(tv);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gameView != null) {
            gameView.stop();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
    }
}
