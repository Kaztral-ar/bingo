package com.bingo125;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
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
 * Offline VS Computer Bingo.
 * Both players fill their cards during the same 2-minute setup window.
 * After setup, the players choose who calls first. Calls alternate: the caller
 * calls one number, both cards automatically mark it, then the other player calls.
 */
public class ComputerGameActivity extends AppCompatActivity {

    private static final long FILL_MILLIS = 120_000;
    private static final long COMPUTER_CALL_DELAY = 1_200;

    private BingoGame game;
    private CountDownTimer fillTimer;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean setupFinished;
    private boolean callingStarted;
    private boolean humanTurn;
    private boolean resultShown;
    private boolean computerCallPending;

    private GridLayout grid;
    private final TextView[][] cellViews = new TextView[5][5];
    private TextView textStatusLabel, textBigNumber, textTimer, textFooterStatus;
    private LinearLayout calledNumbersRow;
    private HorizontalScrollView calledScroll;
    private Button btnCallNumber;

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
        btnCallNumber = findViewById(R.id.btnCallNumber);

        game = new BingoGame(new Player(prefs.getPlayerName()), new ComputerPlayer("Computer"));
        buildGrid();

        // Computer card is prepared immediately. Human and computer setup happen concurrently.
        textStatusLabel.setText("BOTH PLAYERS — FILL YOUR CARDS");
        textBigNumber.setText("1");
        textFooterStatus.setText("Fill your card while the computer prepares its card.");
        startHumanFillTimer();

        btnCallNumber.setOnClickListener(v -> humanCallNumber());
        btnCallNumber.setVisibility(View.GONE);
    }

    private void startHumanFillTimer() {
        fillTimer = new CountDownTimer(FILL_MILLIS, 1000) {
            @Override public void onTick(long remaining) {
                int seconds = (int) (remaining / 1000);
                textTimer.setText(String.format("Card Time: %d:%02d", seconds / 60, seconds % 60));
            }

            @Override public void onFinish() {
                finishCardSetup();
            }
        }.start();
    }

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
                final int row = r, col = c;
                cell.setOnClickListener(v -> placeHumanNumber(row, col));
                grid.addView(cell);
                cellViews[r][c] = cell;
            }
        }
    }

    private void placeHumanNumber(int row, int col) {
        if (setupFinished || callingStarted || resultShown) return;
        BingoCard card = game.getHuman().getCard();
        if (card.isComplete()) return;

        int next = card.getNextNumberToPlace();
        if (card.placeNumber(row, col, next)) {
            sound.playPlace();
            cellViews[row][col].setText(String.valueOf(next));
            textBigNumber.setText(next < 25 ? String.valueOf(next + 1) : "✓");
            if (card.isComplete()) finishCardSetup();
        }
    }

    /** Both cards are considered filled together; only the human card needs manual input. */
    private void finishCardSetup() {
        if (setupFinished || resultShown) return;
        setupFinished = true;
        if (fillTimer != null) { fillTimer.cancel(); fillTimer = null; }

        BingoCard humanCard = game.getHuman().getCard();
        if (!humanCard.isComplete()) humanCard.autoFillRemaining();
        game.getComputer().getCard().autoFillRemaining();
        refreshGridFromCard(humanCard);

        textTimer.setText("Setup complete");
        textBigNumber.setText("✓");
        textStatusLabel.setText("CARDS READY — CHOOSE WHO CALLS FIRST");
        textFooterStatus.setText("Both cards are locked. Choose the first caller.");
        showCallerSelection();
    }

    private void showCallerSelection() {
        new AlertDialog.Builder(this)
                .setTitle("Who calls first?")
                .setMessage("Choose the player who will call the first Bingo number.")
                .setCancelable(false)
                .setNegativeButton("YOU CALL FIRST", (dialog, which) -> startCalling(true))
                .setPositiveButton("COMPUTER CALLS FIRST", (dialog, which) -> startCalling(false))
                .show();
    }

    private void startCalling(boolean humanStarts) {
        if (callingStarted || resultShown) return;
        callingStarted = true;
        humanTurn = humanStarts;
        game.startCallingPhase();

        calledNumbersRow.removeAllViews();
        calledScroll.setVisibility(View.VISIBLE);
        btnCallNumber.setVisibility(View.VISIBLE);
        textTimer.setText("");
        textBigNumber.setText("—");

        if (humanTurn) {
            setHumanCallTurn();
        } else {
            setComputerCallTurn();
        }
    }

    private void setHumanCallTurn() {
        if (resultShown) return;
        humanTurn = true;
        computerCallPending = false;
        btnCallNumber.setEnabled(true);
        btnCallNumber.setText("CALL NUMBER");
        textStatusLabel.setText("YOUR TURN — CALL ONE NUMBER");
        textFooterStatus.setText("Call one number. Both YOU and COMPUTER will mark it.");
    }

    private void setComputerCallTurn() {
        if (resultShown) return;
        humanTurn = false;
        btnCallNumber.setEnabled(false);
        btnCallNumber.setText("COMPUTER CALLING…");
        textStatusLabel.setText("COMPUTER'S TURN — CALLING");
        textFooterStatus.setText("Computer is choosing one number. Both players will mark it.");

        computerCallPending = true;
        handler.postDelayed(() -> {
            computerCallPending = false;
            if (!resultShown && callingStarted && !humanTurn) {
                callNextNumber(false);
            }
        }, COMPUTER_CALL_DELAY);
    }

    private void humanCallNumber() {
        if (resultShown || !callingStarted || !humanTurn || computerCallPending) return;
        btnCallNumber.setEnabled(false);
        callNextNumber(true);
    }

    /** One shared called number is automatically marked on BOTH cards. */
    private void callNextNumber(boolean calledByHuman) {
        if (resultShown || game.getState() != GameState.CALLING) return;

        Integer number = game.callNextNumber();
        if (number == null) {
            finishDraw();
            return;
        }

        sound.playCall();
        textBigNumber.setText(String.valueOf(number));
        appendCalledChip(number);

        // Every called number is automatically marked on the human card.
        // BingoGame already auto-marks the same number on the computer card.
        BingoCard humanCard = game.getHuman().getCard();
        if (humanCard.markNumber(number)) recolorCell(number);

        BingoValidator.WinResult humanWin = game.checkHumanWin();
        BingoValidator.WinResult computerWin = game.checkComputerWin();

        if (humanWin.isWin() || computerWin.isWin()) {
            if (humanWin.isWin() && computerWin.isWin()) {
                finishTie(humanWin);
            } else if (humanWin.isWin()) {
                finishRound(true, humanWin);
            } else {
                finishRound(false, computerWin);
            }
            return;
        }

        // Alternate the caller after every single number.
        if (calledByHuman) {
            setComputerCallTurn();
        } else {
            setHumanCallTurn();
        }
    }

    private void highlightIfPresent(int number) {
        BingoCard card = game.getHuman().getCard();
        for (int r = 0; r < 5; r++) for (int c = 0; c < 5; c++) {
            if (card.getValue(r, c) == number && !card.isMarked(r, c))
                cellViews[r][c].setBackgroundColor(getColor(R.color.cell_called));
        }
    }

    private void recolorCell(int number) {
        BingoCard card = game.getHuman().getCard();
        for (int r = 0; r < 5; r++) for (int c = 0; c < 5; c++)
            if (card.getValue(r, c) == number)
                cellViews[r][c].setBackgroundColor(getColor(R.color.cell_marked));
    }

    private void refreshGridFromCard(BingoCard card) {
        for (int r = 0; r < 5; r++) for (int c = 0; c < 5; c++) {
            cellViews[r][c].setText(String.valueOf(card.getValue(r, c)));
            cellViews[r][c].setBackgroundColor(getColor(R.color.bg_cell));
        }
    }

    private void appendCalledChip(int number) {
        TextView chip = new TextView(this);
        chip.setText(String.valueOf(number));
        chip.setTextColor(getColor(R.color.bg_deep));
        chip.setBackgroundColor(getColor(R.color.accent_gold));
        chip.setPadding(24, 12, 24, 12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(6, 0, 6, 0);
        chip.setLayoutParams(lp);
        calledNumbersRow.addView(chip);
        calledScroll.post(() -> calledScroll.fullScroll(View.FOCUS_RIGHT));
        highlightIfPresent(number);
    }

    private void finishRound(boolean humanWon, BingoValidator.WinResult result) {
        if (resultShown) return;
        resultShown = true;
        game.finish();
        handler.removeCallbacksAndMessages(null);
        if (fillTimer != null) fillTimer.cancel();
        sound.playWin();
        new StatsManager(this).recordComputerGame(humanWon, game.calledSoFar().size());
        adManager.showInterstitialIfReady(this);

        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra("winnerName", humanWon ? game.getHuman().getName() : game.getComputer().getName());
        intent.putExtra("pattern", result.describe());
        intent.putExtra("mode", "computer");
        startActivity(intent);
        finish();
    }

    private void finishTie(BingoValidator.WinResult result) {
        if (resultShown) return;
        resultShown = true;
        game.finish();
        handler.removeCallbacksAndMessages(null);
        if (fillTimer != null) fillTimer.cancel();
        new StatsManager(this).recordComputerGame(false, game.calledSoFar().size());

        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra("winnerName", "Tie — both got Bingo");
        intent.putExtra("pattern", result.describe());
        intent.putExtra("mode", "computer");
        startActivity(intent);
        finish();
    }

    private void finishDraw() {
        if (resultShown) return;
        resultShown = true;
        game.finish();
        handler.removeCallbacksAndMessages(null);
        if (fillTimer != null) fillTimer.cancel();
        new StatsManager(this).recordComputerGame(false, game.calledSoFar().size());

        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra("winnerName", "No winner");
        intent.putExtra("pattern", "No Bingo — all 25 numbers called");
        intent.putExtra("mode", "computer");
        startActivity(intent);
        finish();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (fillTimer != null) fillTimer.cancel();
        handler.removeCallbacksAndMessages(null);
        if (sound != null) sound.release();
    }
}
