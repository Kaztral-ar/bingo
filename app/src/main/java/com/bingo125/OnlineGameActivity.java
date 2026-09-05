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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bingo125.online.RoomManager;
import com.bingo125.online.RoomModel;
import com.bingo125.util.AdManager;
import com.bingo125.util.SoundManager;
import com.bingo125.util.StatsManager;

import java.util.List;

/**
 * Real-time two-player online Bingo.
 * Cards are filled independently during the same setup phase. During calling,
 * every called number is automatically marked on both cards. Completed
 * rows/columns/diagonals count as B-I-N-G-O letters; the first player to reach
 * all five letters wins.
 */
public class OnlineGameActivity extends AppCompatActivity {

    private static final long CALL_INTERVAL_MILLIS = 2_500;
    private static final String[] BINGO = {"B", "I", "N", "G", "O"};

    private final RoomManager roomManager = new RoomManager();
    private final int[][] myCard = new int[5][5];
    private final boolean[][] myMarked = new boolean[5][5];
    private int nextNumberToPlace = 1;
    private boolean cardLocked = false;
    private int lastRenderedCalledCount = 0;
    private boolean resultShown = false;
    private boolean callingStartedLocally = false;
    private String currentStatus = "";
    private RoomModel latestRoom;

    private final Handler callHandler = new Handler(Looper.getMainLooper());
    private Runnable callRunnable;
    private String roomCode, myUid;
    private CountDownTimer fillTimer;

    private GridLayout grid;
    private final TextView[][] cellViews = new TextView[5][5];
    private TextView textStatusLabel, textBigNumber, textTimer, textFooterStatus;
    private LinearLayout calledNumbersRow;
    private HorizontalScrollView calledScroll;

    private SoundManager sound;
    private final AdManager adManager = new AdManager();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_computer_game);

        roomCode = getIntent().getStringExtra("roomCode");
        myUid = getIntent().getStringExtra("uid");
        if (roomCode == null || myUid == null) {
            Toast.makeText(this, "Invalid game session.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        sound = new SoundManager(this);
        adManager.loadInterstitial(this);
        grid = findViewById(R.id.bingoGrid);
        textStatusLabel = findViewById(R.id.textStatusLabel);
        textBigNumber = findViewById(R.id.textBigNumber);
        textTimer = findViewById(R.id.textTimer);
        textFooterStatus = findViewById(R.id.textFooterStatus);
        calledNumbersRow = findViewById(R.id.calledNumbersRow);
        calledScroll = findViewById(R.id.calledScroll);

        buildGrid();
        textBigNumber.setText("1");

        roomManager.listenToRoom(roomCode, new RoomManager.RoomListener() {
            @Override public void onRoomUpdated(RoomModel room) { render(room); }
            @Override public void onError(String message) {
                Toast.makeText(OnlineGameActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
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

    private void onCellTapped(int row, int col) {
        if (resultShown) return;
        // Filling is manual. During calling, marks come automatically from the server.
        if ("filling".equals(currentStatus) && !cardLocked) placeDuringFilling(row, col);
    }

    private void placeDuringFilling(int row, int col) {
        if (myCard[row][col] != 0 || nextNumberToPlace > 25) return;
        int number = nextNumberToPlace;
        myCard[row][col] = number;
        cellViews[row][col].setText(String.valueOf(number));
        sound.playPlace();
        nextNumberToPlace++;
        textBigNumber.setText(nextNumberToPlace <= 25 ? String.valueOf(nextNumberToPlace) : "✓");

        roomManager.placeNumber(roomCode, myUid, row, col, number, deepCopy(myCard));
        if (nextNumberToPlace > 25) {
            cardLocked = true;
            roomManager.lockCard(roomCode, myUid);
            textFooterStatus.setText("Card complete — waiting for opponent…");
        }
    }

    private void render(RoomModel room) {
        if (resultShown) return;
        latestRoom = room;
        currentStatus = room.status == null ? "" : room.status;

        if ("filling".equals(currentStatus)) {
            textStatusLabel.setText("Fill Your Bingo Card");
            textFooterStatus.setText(cardLocked
                    ? "Card complete — waiting for opponent…"
                    : "Tap cells to place 1 → 25");
            if (room.fillDeadline != null && fillTimer == null) {
                long remaining = Math.max(0, room.fillDeadline - System.currentTimeMillis());
                fillTimer = new CountDownTimer(remaining, 1000) {
                    @Override public void onTick(long ms) {
                        int s = (int) (ms / 1000);
                        textTimer.setText(String.format("Time Left: %d:%02d", s / 60, s % 60));
                    }
                    @Override public void onFinish() {
                        textTimer.setText("Time Left: 0:00");
                        if (myUid.equals(room.host)) roomManager.requestCalling(roomCode, myUid);
                    }
                }.start();
            }
        }

        if ("calling".equals(currentStatus)) {
            if (fillTimer != null) { fillTimer.cancel(); fillTimer = null; }
            RoomModel.PlayerModel me = room.players.get(myUid);
            if (me != null && me.card != null) {
                cardLocked = me.cardLocked;
                for (int r = 0; r < 5; r++) {
                    for (int c = 0; c < 5; c++) {
                        myCard[r][c] = me.card[r][c];
                        cellViews[r][c].setText(String.valueOf(myCard[r][c]));
                        myMarked[r][c] = me.marked != null && me.marked[r][c];
                        cellViews[r][c].setBackgroundColor(myMarked[r][c]
                                ? getColor(R.color.cell_marked) : getColor(R.color.bg_cell));
                    }
                }
            }

            calledScroll.setVisibility(View.VISIBLE);
            textStatusLabel.setText("Bingo Round — Online");
            textTimer.setText("");

            List<Integer> called = room.calledNumbers;
            if (called != null && called.size() < lastRenderedCalledCount) {
                lastRenderedCalledCount = 0;
                calledNumbersRow.removeAllViews();
            }
            if (called != null && called.size() > lastRenderedCalledCount) {
                for (int i = lastRenderedCalledCount; i < called.size(); i++) {
                    int number = called.get(i);
                    sound.playCall();
                    appendCalledChip(number);
                }
                textBigNumber.setText(String.valueOf(called.get(called.size() - 1)));
                lastRenderedCalledCount = called.size();
            }

            updateBingoProgress(me);

            // Only the host requests the authoritative shared call sequence.
            if (myUid.equals(room.host) && !callingStartedLocally) {
                callingStartedLocally = true;
                startHostCallingLoop();
            }
        }

        if ("finished".equals(currentStatus) && room.winnerUid != null) {
            resultShown = true;
            stopCallingLoop();
            if (fillTimer != null) { fillTimer.cancel(); fillTimer = null; }
            roomManager.stopListening();
            boolean iWon = myUid.equals(room.winnerUid);
            String winnerName = "Opponent";
            RoomModel.PlayerModel winnerModel = room.players.get(room.winnerUid);
            if (winnerModel != null && winnerModel.name != null) winnerName = winnerModel.name;
            if (iWon) sound.playWin();
            new StatsManager(this).recordOnlineGame(iWon, lastRenderedCalledCount);
            adManager.showInterstitialIfReady(this);

            Intent intent = new Intent(this, ResultActivity.class);
            intent.putExtra("winnerName", winnerName);
            intent.putExtra("pattern", room.winningPattern != null ? room.winningPattern : "BINGO");
            intent.putExtra("mode", "online");
            startActivity(intent);
            finish();
        }
    }

    private void updateBingoProgress(RoomModel.PlayerModel me) {
        int count = me == null ? 0 : Math.max(0, Math.min(5, me.bingoCount));
        StringBuilder progress = new StringBuilder("BINGO: ");
        for (int i = 0; i < BINGO.length; i++) {
            if (i > 0) progress.append("  ");
            progress.append(i < count ? BINGO[i] + " ✓" : BINGO[i]);
        }
        textFooterStatus.setText(progress.toString());
    }

    private void startHostCallingLoop() {
        stopCallingLoop();
        callRunnable = new Runnable() {
            @Override public void run() {
                if (resultShown || !"calling".equals(currentStatus)) return;
                roomManager.requestNextNumber(roomCode, myUid);
                callHandler.postDelayed(this, CALL_INTERVAL_MILLIS);
            }
        };
        callHandler.post(callRunnable);
    }

    private void stopCallingLoop() {
        callHandler.removeCallbacksAndMessages(null);
        callRunnable = null;
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

    private int[][] deepCopy(int[][] src) {
        int[][] copy = new int[5][5];
        for (int r = 0; r < 5; r++) copy[r] = src[r].clone();
        return copy;
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (fillTimer != null) fillTimer.cancel();
        stopCallingLoop();
        roomManager.stopListening();
        if (sound != null) sound.release();
    }
}
