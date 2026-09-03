package com.bingo125;

import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.bingo125.util.AdManager;

public class MainActivity extends AppCompatActivity {

    private final AdManager adManager = new AdManager();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btnNewGame).setOnClickListener(v ->
                startActivity(new Intent(this, GameModeActivity.class)));

        findViewById(R.id.btnHowToPlay).setOnClickListener(v ->
                startActivity(new Intent(this, HowToPlayActivity.class)));

        findViewById(R.id.btnStatistics).setOnClickListener(v ->
                startActivity(new Intent(this, StatisticsActivity.class)));

        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        adManager.attachBanner(this, findViewById(R.id.bannerContainer));
    }
}
