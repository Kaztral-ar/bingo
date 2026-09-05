package com.bingo125;

import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bingo125.util.AdManager;

public class ResultActivity extends AppCompatActivity {
    private final AdManager adManager = new AdManager();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        String winnerName = getIntent().getStringExtra("winnerName");
        String pattern = getIntent().getStringExtra("pattern");
        String mode = getIntent().getStringExtra("mode");
        boolean tie = "TIE".equalsIgnoreCase(pattern);
        boolean draw = "DRAW".equalsIgnoreCase(pattern);

        TextView headline = findViewById(R.id.textResultHeadline);
        TextView winner = findViewById(R.id.textWinner);
        TextView winningPattern = findViewById(R.id.textPattern);

        if (tie) {
            headline.setText("BINGO — TIE!");
            winner.setText("Both players win");
            winningPattern.setText("Both players completed B-I-N-G-O");
        } else if (draw) {
            headline.setText("GAME DRAW");
            winner.setText("No winner");
            winningPattern.setText("All 25 numbers were called");
        } else {
            boolean youWon = "You".equalsIgnoreCase(winnerName);
            headline.setText(youWon ? "YOU WIN!" : "YOU LOSE");
            winner.setText(youWon ? "BINGO! 🎉" : "Winner: " + (winnerName == null ? "Opponent" : winnerName));
            winningPattern.setText("Winning Pattern: " + (pattern == null ? "B-I-N-G-O" : pattern));
        }

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
