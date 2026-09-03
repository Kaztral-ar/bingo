package com.bingo125;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class GameModeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_mode);

        findViewById(R.id.btnVsComputer).setOnClickListener(v ->
                startActivity(new Intent(this, ComputerGameActivity.class)));

        findViewById(R.id.btnOnline).setOnClickListener(v ->
                startActivity(new Intent(this, OnlineMenuActivity.class)));
    }
}
