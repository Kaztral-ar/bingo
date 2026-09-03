package com.bingo125.util;

import android.content.Context;
import android.content.SharedPreferences;

/** Local statistics tracking, per the Statistics screen spec. */
public class StatsManager {

    private static final String STATS = "bingo125_stats";
    private final SharedPreferences stats;

    public StatsManager(Context context) {
        stats = context.getApplicationContext().getSharedPreferences(STATS, Context.MODE_PRIVATE);
    }

    public int gamesPlayed()   { return stats.getInt("games_played", 0); }
    public int gamesWon()      { return stats.getInt("games_won", 0); }
    public int gamesLost()     { return stats.getInt("games_lost", 0); }
    public int computerWins()  { return stats.getInt("computer_wins", 0); }
    public int onlineWins()    { return stats.getInt("online_wins", 0); }
    public int bingoCount()    { return stats.getInt("bingo_count", 0); }
    /** Fewest numbers called before a win; Integer.MAX_VALUE means "no data yet". */
    public int fastestWin()    { return stats.getInt("fastest_win", Integer.MAX_VALUE); }

    public void recordComputerGame(boolean won, int numbersCalledWhenWon) {
        SharedPreferences.Editor e = stats.edit();
        e.putInt("games_played", gamesPlayed() + 1);
        if (won) {
            e.putInt("games_won", gamesWon() + 1);
            e.putInt("computer_wins", computerWins() + 1);
            e.putInt("bingo_count", bingoCount() + 1);
            if (numbersCalledWhenWon < fastestWin()) {
                e.putInt("fastest_win", numbersCalledWhenWon);
            }
        } else {
            e.putInt("games_lost", gamesLost() + 1);
        }
        e.apply();
    }

    public void recordOnlineGame(boolean won, int numbersCalledWhenWon) {
        SharedPreferences.Editor e = stats.edit();
        e.putInt("games_played", gamesPlayed() + 1);
        if (won) {
            e.putInt("games_won", gamesWon() + 1);
            e.putInt("online_wins", onlineWins() + 1);
            e.putInt("bingo_count", bingoCount() + 1);
            if (numbersCalledWhenWon < fastestWin()) {
                e.putInt("fastest_win", numbersCalledWhenWon);
            }
        } else {
            e.putInt("games_lost", gamesLost() + 1);
        }
        e.apply();
    }
}
