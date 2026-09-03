package com.bingo125;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OnlineGameActivity extends AppCompatActivity {

    private final RoomManager roomManager = new RoomManager();
    private final int[][] myCard = new int[5][5];
    private final boolean[][] myMarked = new boolean[5][5];
    private int nextNumberToPlace = 1;
    private boolean cardLocked = false;
    private int lastRenderedCalledCount = 0;
    private boolean resultShown = false;
    private String currentStatus = "";

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
        if ("filling".equals(currentStatus) && !cardLocked) placeDuringFilling(row, col);
        else if ("calling".equals(currentStatus) && cardLocked) markDuringCalling(row, col);
    }

    private void placeDuringFilling(int row, int col) {
        if (myCard[row][col] != 0 || nextNumberToPlace > 25) return;
        myCard[row][col] = nextNumberToPlace;
        cellViews[row][col].setText(String.valueOf(nextNumberToPlace));
        sound.playPlace();
        nextNumberToPlace++;
        textBigNumber.setText(nextNumberToPlace <= 25 ? String.valueOf(nextNumberToPlace) : "✓");

        roomManager.placeNumber(roomCode, myUid, row, col, nextNumberToPlace - 1, deepCopy(myCard));
        if (nextNumberToPlace > 25) {
            cardLocked = true;
            roomManager.lockCard(roomCode, myUid);
            textFooterStatus.setText("Card complete — waiting for opponent / timer…");
        }
    }

    private void markDuringCalling(int row, int col) {
        int value = myCard[row][col];
        if (value == 0 || myMarked[row][col]) return;

        List<Integer> called = getCurrentCalledNumbers;
        if (called == null || !called.contains(value)) {
            Toast.makeText(this, "Wait for number " + value + " to be called.", Toast.LENGTH_SHORT).show();
            return;
        }

        myMarked[row][col] = true;
        cellViews[row][col].setBackgroundColor(getColor(R.color.cell_marked));
        roomManager.markNumber(roomCode, myUid, deepCopy(myMarked));
        if (hasLocalBingo()) roomManager.claimBingo(roomCode, myUid);
    }

    // Kept as a field so taps always use the latest server state.
    private List<Integer> getCurrentCalledNumbers;

    private boolean hasLocalBingo() {
        for (int r = 0; r < 5; r++) {
            boolean ok = true;
            for (int c = 0; c < 5; c++) ok &= myMarked[r][c];
            if (ok) return true;
        }
        for (int c = 0; c < 5; c++) {
            boolean ok = true;
            for (int r = 0; r < 5; r++) ok &= myMarked[r][c];
            if (ok) return true;
        }
        boolean a = true, b = true;
        for (int i = 0; i < 5; i++) { a &= myMarked[i][i]; b &= myMarked[i][4 - i]; }
        return a || b;
    }

    private void render(RoomModel room) {
        if (resultShown) return;
        currentStatus = room.status == null ? "" : room.status;

        if ("filling".equals(currentStatus) && room.fillDeadline != null) {
            textStatusLabel.setText("Fill Your Bingo Card");
            if (fillTimer == null) {
                long remaining = Math.max(0, room.fillDeadline - System.currentTimeMillis());
                fillTimer = new CountDownTimer(remaining, 1000) {
                    @Override public void onTick(long ms) {
                        int s = (int) (ms / 1000);
                        textTimer.setText(String.format("Time Left: %d:%02d", s / 60, s % 60));
                    }
                    @Override public void onFinish() { textTimer.setText("Time Left: 0:00"); }
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
                    }
                }
            }
            if (!"calling".equals(currentStatus)) return;
            calledScroll.setVisibility(View.VISIBLE);
            textStatusLabel.setText("Bingo Round — Online");
            textTimer.setText("");

            List<Integer> called = room.calledNumbers;
            getCurrentCalledNumbers = called;
            if (called != null && called.size() < lastRenderedCalledCount) {
                lastRenderedCalledCount = 0;
                calledNumbersRow.removeAllViews();
                resetMarks();
            }
            if (called != null && called.size() > lastRenderedCalledCount) {
                for (int i = lastRenderedCalledCount; i < called.size(); i++) {
                    int number = called.get(i);
                    sound.playCall();
                    appendCalledChip(number);
                    highlightIfPresent(number);
                }
                textBigNumber.setText(String.valueOf(called.get(called.size() - 1)));
                lastRenderedCalledCount = called.size();
            }
            textFooterStatus.setText("Tap a called number on your card to mark it");
        }

        if ("finished".equals(currentStatus) && room.winnerUid != null) {
            resultShown = true;
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
            intent.putExtra("pattern", room.winningPattern != null ? room.winningPattern : "—");
            intent.putExtra("mode", "online");
            startActivity(intent);
            finish();
        }
    }

    private void resetMarks() {
        for (int r = 0; r < 5; r++) for (int c = 0; c < 5; c++) {
            myMarked[r][c] = false;
            cellViews[r][c].setBackgroundColor(getColor(R.color.bg_cell));
        }
    }

    private void highlightIfPresent(int number) {
        for (int r = 0; r < 5; r++) for (int c = 0; c < 5; c++) {
            if (myCard[r][c] == number && !myMarked[r][c])
                cellViews[r][c].setBackgroundColor(getColor(R.color.cell_called));
        }
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

    private boolean[][] deepCopy(boolean[][] src) {
        boolean[][] copy = new boolean[5][5];
        for (int r = 0; r < 5; r++) copy[r] = src[r].clone();
        return copy;
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (fillTimer != null) fillTimer.cancel();
        roomManager.stopListening();
        if (sound != null) sound.release();
    }
}
