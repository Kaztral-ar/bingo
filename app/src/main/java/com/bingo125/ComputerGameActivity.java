package com.bingo125;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bingo125.game.BingoCard;
import com.bingo125.game.BingoGame;
import com.bingo125.game.BingoValidator;
import com.bingo125.game.ComputerPlayer;
import com.bingo125.game.GameState;
import com.bingo125.game.Player;
import com.bingo125.util.AdManager;
import com.bingo125.util.PrefsManager;
import com.bingo125.util.SoundManager;
import com.bingo125.util.StatsManager;

/**
 * Full VS-Computer flow: 2-minute card filling (spec sections 3–8), then
 * automatic number calling with marking + live win detection (sections 9–13).
 */
public class ComputerGameActivity extends AppCompatActivity {

    private static final long FILL_MILLIS = 120_000;
    private static final long CALL_INTERVAL_MILLIS = 2_500;

    private BingoGame game;
    private CountDownTimer fillTimer;
    private final Handler callHandler = new Handler(Looper.getMainLooper());
    private Runnable callRunnable;

    private GridLayout grid;
    private final TextView[][] cellViews = new TextView[5][5];

    private TextView textStatusLabel, textBigNumber, textTimer, textFooterStatus;
    private LinearLayout calledNumbersRow;
    private HorizontalScrollView calledScroll;

    private SoundManager sound;
    private PrefsManager prefs;
    private final AdManager adManager = new AdManager();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_computer_game);

        sound = new SoundManager(this);
        prefs = new PrefsManager(this);
        adManager.loadInterstitial(this);

        grid = findViewById(R.id.bingoGrid);
        textStatusLabel = findViewById(R.id.textStatusLabel);
        textBigNumber = findViewById(R.id.textBigNumber);
        textTimer = findViewById(R.id.textTimer);
        textFooterStatus = findViewById(R.id.textFooterStatus);
        calledNumbersRow = findViewById(R.id.calledNumbersRow);
        calledScroll = findViewById(R.id.calledScroll);

        game = new BingoGame(new Player(prefs.getPlayerName()), new ComputerPlayer("Computer"));

        buildGrid();
        startFillingPhase();
    }

    // ---------- Grid construction ----------

    private void buildGrid() {
        grid.removeAllViews();
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                TextView cell = new TextView(this);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = 0;
                lp.height = 0;
                lp.rowSpec = GridLayout.spec(r, 1f);
                lp.columnSpec = GridLayout.spec(c, 1f);
                int marginPx = (int) (getResources().getDisplayMetrics().density * 3);
                lp.setMargins(marginPx, marginPx, marginPx, marginPx);
                cell.setLayoutParams(lp);
                cell.setGravity(Gravity.CENTER);
                cell.setTextSize(18);
                cell.setBackgroundColor(getColor(R.color.bg_cell));
                cell.setTextColor(getColor(R.color.text_primary));
                cell.setText("");

                final int row = r, col = c;
                cell.setOnClickListener(v -> onCellTapped(row, col));
                grid.addView(cell);
                cellViews[r][c] = cell;
            }
        }
    }

    // ---------- Filling phase ----------

    private void startFillingPhase() {
        textStatusLabel.setText("Fill Your Bingo Card");
        updateNextNumberDisplay();

        fillTimer = new CountDownTimer(FILL_MILLIS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                textTimer.setText(String.format("Time Left: %d:%02d", seconds / 60, seconds % 60));
            }

            @Override
            public void onFinish() {
                if (!game.getHuman().getCard().isComplete()) {
                    game.getHuman().getCard().autoFillRemaining();
                    refreshGridFromCard(game.getHuman().getCard());
                }
                beginCallingPhase();
            }
        }.start();
    }

    private void onCellTapped(int row, int col) {
        if (game.getState() == GameState.CALLING) {
            onCellTappedDuringCalling(row, col);
            return;
        }
        if (game.getState() != GameState.FILLING) return;
        BingoCard card = game.getHuman().getCard();
        if (card.isComplete()) return;

        int next = card.getNextNumberToPlace();
        boolean placed = card.placeNumber(row, col, next);
        if (placed) {
            sound.playPlace();
            cellViews[row][col].setText(String.valueOf(next));
            updateNextNumberDisplay();

            if (card.isComplete()) {
                textFooterStatus.setText("Card Complete!");
                if (fillTimer != null) fillTimer.cancel();
                beginCallingPhase();
            }
        }
    }

    private void updateNextNumberDisplay() {
        int next = game.getHuman().getCard().getNextNumberToPlace();
        textBigNumber.setText(next <= 25 ? String.valueOf(next) : "✓");
    }

    private void refreshGridFromCard(BingoCard card) {
        for (int r = 0; r < 5; r++)
            for (int c = 0; c < 5; c++)
                cellViews[r][c].setText(String.valueOf(card.getValue(r, c)));
    }

    // ---------- Calling phase ----------

    private void beginCallingPhase() {
        game.startCallingPhase();
        textStatusLabel.setText("Bingo Round — YOU vs COMPUTER");
        textTimer.setText("");
        textBigNumber.setText("—");
        calledScroll.setVisibility(View.VISIBLE);
        textFooterStatus.setText("Waiting for next number...");

        callRunnable = new Runnable() {
            @Override
            public void run() {
                Integer number = game.callNextNumber();
                if (number == null) {
                    // Deck exhausted with nobody completing a line — draw / no winner.
                    textFooterStatus.setText("No more numbers — no winner this round.");
                    return;
                }
                onNumberCalled(number);
                callHandler.postDelayed(this, CALL_INTERVAL_MILLIS);
            }
        };
        callHandler.postDelayed(callRunnable, CALL_INTERVAL_MILLIS);
    }

    private void onNumberCalled(int number) {
        sound.playCall();
        textBigNumber.setText(String.valueOf(number));
        appendCalledChip(number);

        highlightIfPresent(number);

        boolean autoMark = prefs.isAutoMarkOn();
        if (autoMark && game.getHuman().getCard().containsNumber(number)) {
            game.getHuman().getCard().markNumber(number);
            recolorCell(number);
        }

        BingoValidator.WinResult computerWin = game.checkComputerWin();
        BingoValidator.WinResult humanWin = game.checkHumanWin();

        textFooterStatus.setText(autoMark ? "Marked automatically" : "Tap the number on your card to mark it");

        if (computerWin.isWin() || humanWin.isWin()) {
            finishRound(humanWin.isWin(), humanWin.isWin() ? humanWin : computerWin);
        }
    }

    private void highlightIfPresent(int number) {
        BingoCard card = game.getHuman().getCard();
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                if (card.getValue(r, c) == number) {
                    cellViews[r][c].setBackgroundColor(getColor(R.color.cell_called));
                }
            }
        }
    }

    private void recolorCell(int number) {
        BingoCard card = game.getHuman().getCard();
        for (int r = 0; r < 5; r++)
            for (int c = 0; c < 5; c++)
                if (card.getValue(r, c) == number)
                    cellViews[r][c].setBackgroundColor(getColor(R.color.cell_marked));
    }

    /** The player taps a called number on their own card to mark it (manual-mark mode). */
    private void onCellTappedDuringCalling(int row, int col) {
        BingoCard card = game.getHuman().getCard();
        int value = card.getValue(row, col);
        if (value == 0 || card.isMarked(row, col)) return;
        if (!game.calledSoFar().contains(value)) return; // cannot mark an uncalled number

        card.markNumber(value);
        recolorCell(value);

        BingoValidator.WinResult humanWin = game.checkHumanWin();
        if (humanWin.isWin()) finishRound(true, humanWin);
    }

    private void appendCalledChip(int number) {
        TextView chip = new TextView(this);
        chip.setText(String.valueOf(number));
        chip.setTextColor(getColor(R.color.bg_deep));
        chip.setBackgroundColor(getColor(R.color.accent_gold));
        chip.setPadding(24, 12, 24, 12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(6, 0, 6, 0);
        chip.setLayoutParams(lp);
        calledNumbersRow.addView(chip);
        calledScroll.post(() -> calledScroll.fullScroll(View.FOCUS_RIGHT));
    }

    private void finishRound(boolean humanWon, BingoValidator.WinResult result) {
        game.finish();
        callHandler.removeCallbacksAndMessages(null);
        sound.playWin();

        for (TextView[] row : cellViewsSnapshot()) {
            // Highlight winning line on the human card when the human won.
        }

        int numbersCalled = game.calledSoFar().size();
        new StatsManager(this).recordComputerGame(humanWon, numbersCalled);

        adManager.showInterstitialIfReady(this);

        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra("winnerName", humanWon ? game.getHuman().getName() : game.getComputer().getName());
        intent.putExtra("pattern", result.describe());
        intent.putExtra("mode", "computer");
        startActivity(intent);
        finish();
    }

    private TextView[][] cellViewsSnapshot() {
        return cellViews;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fillTimer != null) fillTimer.cancel();
        callHandler.removeCallbacksAndMessages(null);
        sound.release();
    }
}
