package com.bingo125;

import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bingo125.util.AdManager;

public class ResultActivity extends AppCompatActivity {

    private final AdManager adManager = new AdManager();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        String winnerName = getIntent().getStringExtra("winnerName");
        String pattern = getIntent().getStringExtra("pattern");
        String mode = getIntent().getStringExtra("mode"); // "computer" or "online"

        ((TextView) findViewById(R.id.textWinner)).setText("Winner: " + winnerName);
        ((TextView) findViewById(R.id.textPattern)).setText("Winning Pattern: " + pattern);

        findViewById(R.id.btnPlayAgain).setOnClickListener(v -> {
            Class<?> target = "online".equals(mode) ? OnlineMenuActivity.class : ComputerGameActivity.class;
            startActivity(new Intent(this, target));
            finish();
        });

        findViewById(R.id.btnHome).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        adManager.attachBanner(this, (FrameLayout) findViewById(R.id.bannerContainer));
    }
}
