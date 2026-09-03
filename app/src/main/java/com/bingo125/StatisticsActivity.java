package com.bingo125;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bingo125.util.StatsManager;

public class StatisticsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        StatsManager stats = new StatsManager(this);

        setRow(R.id.rowGamesPlayed, "Games Played", String.valueOf(stats.gamesPlayed()));
        setRow(R.id.rowGamesWon, "Games Won", String.valueOf(stats.gamesWon()));
        setRow(R.id.rowGamesLost, "Games Lost", String.valueOf(stats.gamesLost()));
        setRow(R.id.rowComputerWins, "Computer Wins", String.valueOf(stats.computerWins()));
        setRow(R.id.rowOnlineWins, "Online Wins", String.valueOf(stats.onlineWins()));
        setRow(R.id.rowBingoCount, "Bingo Count", String.valueOf(stats.bingoCount()));

        int fastest = stats.fastestWin();
        setRow(R.id.rowFastestWin, "Fastest Win",
                fastest == Integer.MAX_VALUE ? "—" : (fastest + " numbers"));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void setRow(int id, String label, String value) {
        ((TextView) findViewById(id)).setText(label + "        " + value);
    }
}
