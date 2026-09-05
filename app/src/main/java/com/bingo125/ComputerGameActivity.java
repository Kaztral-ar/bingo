package com.bingo125;

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
import android.app.AlertDialog;

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

/** Offline VS Computer Bingo with a clear, turn-by-turn card setup phase. */
public class ComputerGameActivity extends AppCompatActivity {

    private static final long FILL_MILLIS = 120_000;
    private static final long COMPUTER_TURN_DELAY = 800;
    private static final long CALL_INTERVAL_MILLIS = 2_500;

    private BingoGame game;
    private CountDownTimer fillTimer;
    private final Handler callHandler = new Handler(Looper.getMainLooper());
    private Runnable callRunnable;

    private boolean humanStarts;
    private boolean humanTurn;
    private boolean humanCardDone;
    private boolean computerCardDone;
    private boolean callingStarted;
    private boolean resultShown;

    private GridLayout grid;
    private final TextView[][] cellViews = new TextView[5][5];
    private TextView textStatusLabel, textBigNumber, textTimer, textFooterStatus;
    private LinearLayout calledNumbersRow;
    private HorizontalScrollView calledScroll;
    private Button btnNextPlayer;

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
        btnNextPlayer = findViewById(R.id.btnNextPlayer);

        game = new BingoGame(new Player(prefs.getPlayerName()), new ComputerPlayer("Computer"));
        buildGrid();

        btnNextPlayer.setOnClickListener(v -> advanceToNextTurn());
        showWhoStartsDialog();
    }

    /** Always ask first. The chosen starter is shown before any card input is allowed. */
    private void showWhoStartsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Who starts first?")
                .setMessage("Choose who gets the first card-filling turn.")
                .setCancelable(false)
                .setNegativeButton("YOU START", (d, w) -> beginTurn(true))
                .setPositiveButton("COMPUTER STARTS", (d, w) -> beginTurn(false))
                .show();
    }

    private void beginTurn(boolean human) {
        humanTurn = human;
        btnNextPlayer.setVisibility(View.GONE);
        clearVisibleGrid();

        if (human) {
            textStatusLabel.setText("YOUR TURN — Fill Your Bingo Card");
            textFooterStatus.setText("Place numbers 1 → 25. You have 2 minutes.");
            textBigNumber.setText("1");
            startHumanTimer();
        } else {
            textStatusLabel.setText("COMPUTER'S TURN");
            textBigNumber.setText("—");
            textTimer.setText("");
            textFooterStatus.setText("Computer is filling its card…");
            grid.setVisibility(View.INVISIBLE);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (resultShown || humanTurn) return;
                game.getComputer().getCard().autoFillRemaining();
                computerCardDone = true;
                textStatusLabel.setText("COMPUTER FINISHED");
                textFooterStatus.setText("Computer has finished. Press Next Player when ready.");
                btnNextPlayer.setText("NEXT: YOU");
                btnNextPlayer.setVisibility(View.VISIBLE);
            }, COMPUTER_TURN_DELAY);
        }
    }

    private void startHumanTimer() {
        if (fillTimer != null) fillTimer.cancel();
        grid.setVisibility(View.VISIBLE);
        fillTimer = new CountDownTimer(FILL_MILLIS, 1000) {
            @Override public void onTick(long remaining) {
                int seconds = (int) (remaining / 1000);
                textTimer.setText(String.format("Time Left: %d:%02d", seconds / 60, seconds % 60));
            }

            @Override public void onFinish() {
                textTimer.setText("Time Left: 0:00");
                finishHumanTurn();
            }
        }.start();
    }

    private void finishHumanTurn() {
        if (!humanTurn || humanCardDone) return;
        if (fillTimer != null) { fillTimer.cancel(); fillTimer = null; }
        BingoCard card = game.getHuman().getCard();
        if (!card.isComplete()) card.autoFillRemaining();
        humanCardDone = true;
        refreshGridFromCard(card);
        textStatusLabel.setText("YOU FINISHED");
        textBigNumber.setText("✓");
        textFooterStatus.setText("Your card is locked. Press Next Player for the computer's turn.");
        btnNextPlayer.setText("NEXT: COMPUTER");
        btnNextPlayer.setVisibility(View.VISIBLE);
    }

    private void advanceToNextTurn() {
        if (resultShown || callingStarted) return;

        if (humanTurn && humanCardDone) {
            beginTurn(false);
            return;
        }

        if (!humanTurn && computerCardDone) {
            if (humanCardDone) {
                startCallingPhase();
            } else {
                beginTurn(true);
            }
        }
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
                cell.setOnClickListener(v -> onCellTapped(row, col));
                grid.addView(cell);
                cellViews[r][c] = cell;
            }
        }
    }

    private void clearVisibleGrid() {
        for (int r = 0; r < 5; r++) for (int c = 0; c < 5; c++) {
            cellViews[r][c].setText("");
            cellViews[r][c].setBackgroundColor(getColor(R.color.bg_cell));
        }
    }

    private void onCellTapped(int row, int col) {
        if (resultShown || callingStarted || !humanTurn) return;
        BingoCard card = game.getHuman().getCard();
        if (card.isComplete()) return;
        int next = card.getNextNumberToPlace();
        if (card.placeNumber(row, col, next)) {
            sound.playPlace();
            cellViews[row][col].setText(String.valueOf(next));
            textBigNumber.setText(next < 25 ? String.valueOf(next + 1) : "✓");
            if (card.isComplete()) finishHumanTurn();
        }
    }

    private void refreshGridFromCard(BingoCard card) {
        grid.setVisibility(View.VISIBLE);
        for (int r = 0; r < 5; r++) for (int c = 0; c < 5; c++) {
            cellViews[r][c].setText(String.valueOf(card.getValue(r, c)));
            cellViews[r][c].setBackgroundColor(getColor(R.color.bg_cell));
        }
    }

    private void startCallingPhase() {
        if (callingStarted || resultShown || !humanCardDone || !computerCardDone) return;
        callingStarted = true;
        if (fillTimer != null) { fillTimer.cancel(); fillTimer = null; }
        game.startCallingPhase();
        btnNextPlayer.setVisibility(View.GONE);
        textStatusLabel.setText("BINGO ROUND — YOU vs COMPUTER");
        textTimer.setText("");
        textBigNumber.setText("—");
        calledNumbersRow.removeAllViews();
        calledScroll.setVisibility(View.VISIBLE);
        textFooterStatus.setText("Numbers will be called automatically.");

        callRunnable = new Runnable() {
            @Override public void run() {
                if (resultShown || game.getState() != GameState.CALLING) return;
                Integer number = game.callNextNumber();
                if (number == null) { finishDraw(); return; }
                onNumberCalled(number);
                if (!resultShown) callHandler.postDelayed(this, CALL_INTERVAL_MILLIS);
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
        if (autoMark) {
            BingoCard card = game.getHuman().getCard();
            if (card.markNumber(number)) recolorCell(number);
        }

        BingoValidator.WinResult humanWin = game.checkHumanWin();
        BingoValidator.WinResult computerWin = game.checkComputerWin();
        if (humanWin.isWin() || computerWin.isWin()) {
            boolean humanWon = humanWin.isWin();
            finishRound(humanWon, humanWon ? humanWin : computerWin);
            return;
        }
        textFooterStatus.setText(autoMark ? "Marked automatically" : "Tap a called number on your card to mark it");
    }

    private void highlightIfPresent(int number) {
        BingoCard card = game.getHuman().getCard();
        for (int r = 0; r < 5; r++) for (int c = 0; c < 5; c++)
            if (card.getValue(r, c) == number && !card.isMarked(r, c))
                cellViews[r][c].setBackgroundColor(getColor(R.color.cell_called));
    }

    private void recolorCell(int number) {
        BingoCard card = game.getHuman().getCard();
        for (int r = 0; r < 5; r++) for (int c = 0; c < 5; c++)
            if (card.getValue(r, c) == number)
                cellViews[r][c].setBackgroundColor(getColor(R.color.cell_marked));
    }

    private void onCellTappedDuringCalling(int row, int col) {
        BingoCard card = game.getHuman().getCard();
        int value = card.getValue(row, col);
        if (value == 0 || card.isMarked(row, col) || !game.calledSoFar().contains(value)) return;
        if (card.markNumber(value)) {
            recolorCell(value);
            BingoValidator.WinResult humanWin = game.checkHumanWin();
            if (humanWin.isWin()) finishRound(true, humanWin);
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
    }

    private void finishRound(boolean humanWon, BingoValidator.WinResult result) {
        if (resultShown) return;
        resultShown = true;
        game.finish();
        callHandler.removeCallbacksAndMessages(null);
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

    private void finishDraw() {
        if (resultShown) return;
        resultShown = true;
        game.finish();
        callHandler.removeCallbacksAndMessages(null);
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
        callHandler.removeCallbacksAndMessages(null);
        if (sound != null) sound.release();
    }
}
