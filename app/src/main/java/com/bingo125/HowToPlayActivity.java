package com.bingo125;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class HowToPlayActivity extends AppCompatActivity {

    private static final String RULES =
            "1. Bingo uses numbers 1–25.\n\n" +
            "2. The card contains 25 numbers arranged in a 5×5 grid.\n\n" +
            "3. Players have 2 minutes to arrange numbers 1–25.\n\n" +
            "4. Numbers must be entered in numerical order.\n\n" +
            "5. Empty boxes are automatically filled when the timer expires.\n\n" +
            "6. After the card is completed, numbers are called randomly.\n\n" +
            "7. A number can only be called once.\n\n" +
            "8. Mark numbers when they are called.\n\n" +
            "9. Complete a full row, column, or diagonal to get Bingo.\n\n" +
            "10. The first player with a valid winning pattern wins.\n\n" +
            "11. In online mode, the server verifies every claimed win.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_how_to_play);

        ((TextView) findViewById(R.id.textRules)).setText(RULES);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }
}
