package com.geometryduel.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import com.geometryduel.R;
import com.geometryduel.game.GameMode;

public class MenuActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        Button btnPvE = findViewById(R.id.btn_pve);
        Button btnAIvAI = findViewById(R.id.btn_aivai);

        btnPvE.setOnClickListener(v -> startGame(GameMode.PLAYER_VS_AI));
        btnAIvAI.setOnClickListener(v -> startGame(GameMode.AI_VS_AI));
    }

    private void startGame(GameMode mode) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("game_mode", mode.name());
        startActivity(intent);
    }
}
