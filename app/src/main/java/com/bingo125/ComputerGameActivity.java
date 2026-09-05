package com.bingo125;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Paint;
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
import com.bingo125.game.ComputerPlayer;
import com.bingo125.game.GameState;
import com.bingo125.game.Player;
import com.bingo125.util.AdManager;
import com.bingo125.util.PrefsManager;
import com.bingo125.util.SoundManager;
import com.bingo125.util.StatsManager;

import java.util.ArrayList;
import java.util.List;

/** Offline Bingo versus the computer. */
public class ComputerGameActivity extends AppCompatActivity {
    private static final long FILL_MILLIS = 120_000;
    private static final long COMPUTER_CALL_DELAY = 1_200;
    private static final String BINGO = "BINGO";

    private BingoGame game;
    private CountDownTimer fillTimer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean setupFinished, callingStarted, humanTurn, resultShown, computerCallPending;
    private final boolean[] humanCompletedLines = new boolean[12];
    private final boolean[] computerCompletedLines = new boolean[12];
    private int humanBingoCount, computerBingoCount;

    private GridLayout grid;
    private final TextView[][] cellViews = new TextView[5][5];
    private TextView textStatusLabel, textBigNumber, textTimer, textFooterStatus;
    private LinearLayout calledNumbersRow;
    private HorizontalScrollView calledScroll;
    private SoundManager sound;
    private PrefsManager prefs;
    private final AdManager adManager = new AdManager();

    @Override protected void onCreate(Bundle savedInstanceState) {
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
        textStatusLabel.setText("BOTH PLAYERS — FILL YOUR CARDS");
        textBigNumber.setText("1");
        textFooterStatus.setText("Tap the squares to place 1 → 25.");
        startHumanFillTimer();
    }

    private void startHumanFillTimer() {
        fillTimer = new CountDownTimer(FILL_MILLIS, 1000) {
            @Override public void onTick(long remaining) {
                int seconds = (int)(remaining / 1000);
                textTimer.setText(String.format("Card Time: %d:%02d", seconds / 60, seconds % 60));
            }
            @Override public void onFinish() { finishCardSetup(); }
        }.start();
    }

    private void buildGrid() {
        grid.removeAllViews();
        for (int r = 0; r < 5; r++) for (int c = 0; c < 5; c++) {
            TextView cell = new TextView(this);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0; lp.height = 0;
            lp.rowSpec = GridLayout.spec(r, 1f);
            lp.columnSpec = GridLayout.spec(c, 1f);
            int m = (int)(getResources().getDisplayMetrics().density * 2);
            lp.setMargins(m, m, m, m);
            cell.setLayoutParams(lp);
            cell.setGravity(Gravity.CENTER);
            cell.setTextSize(17);
            cell.setBackgroundColor(getColor(R.color.bg_cell));
            cell.setTextColor(getColor(R.color.text_primary));
            final int row = r, col = c;
            cell.setOnClickListener(v -> {
                if (callingStarted) callTappedNumber(row, col); else placeHumanNumber(row, col);
            });
            grid.addView(cell);
            cellViews[r][c] = cell;
        }
    }

    private void placeHumanNumber(int row, int col) {
        if (setupFinished || resultShown) return;
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

    private void finishCardSetup() {
        if (setupFinished || resultShown) return;
        setupFinished = true;
        if (fillTimer != null) { fillTimer.cancel(); fillTimer = null; }
        BingoCard human = game.getHuman().getCard();
        if (!human.isComplete()) human.autoFillRemaining();
        game.getComputer().getCard().autoFillRemaining();
        refreshGrid(human);
        textTimer.setText("Setup complete");
        textBigNumber.setText("✓");
        textStatusLabel.setText("CARDS READY — CHOOSE WHO CALLS FIRST");
        textFooterStatus.setText("Choose who calls first.");
        new AlertDialog.Builder(this).setTitle("Who calls first?")
                .setMessage("The caller taps any number on the card. Calls alternate after every number.")
                .setCancelable(false)
                .setNegativeButton("YOU CALL FIRST", (d,w) -> startCalling(true))
                .setPositiveButton("COMPUTER CALLS FIRST", (d,w) -> startCalling(false)).show();
    }

    private void startCalling(boolean humanStarts) {
        if (callingStarted || resultShown) return;
        callingStarted = true; humanTurn = humanStarts; game.startCallingPhase();
        calledNumbersRow.removeAllViews(); calledScroll.setVisibility(View.VISIBLE);
        textTimer.setText(""); textBigNumber.setText("—");
        if (humanTurn) setHumanTurn(); else setComputerTurn();
    }

    private void setHumanTurn() {
        if (resultShown) return;
        humanTurn = true; computerCallPending = false;
        textStatusLabel.setText("YOUR TURN — TAP A NUMBER");
        textFooterStatus.setText("Tap any number on your card to call it. Both players mark it.");
    }

    private void setComputerTurn() {
        if (resultShown) return;
        humanTurn = false;
        textStatusLabel.setText("COMPUTER'S TURN — CALLING");
        textFooterStatus.setText("Computer is choosing a number…");
        computerCallPending = true;
        handler.postDelayed(() -> {
            computerCallPending = false;
            if (!resultShown && callingStarted && !humanTurn) callAndProcess(null, false);
        }, COMPUTER_CALL_DELAY);
    }

    private void callTappedNumber(int row, int col) {
        if (resultShown || !callingStarted || !humanTurn || computerCallPending) return;
        int number = game.getHuman().getCard().getValue(row, col);
        if (number < 1 || number > 25) return;
        callAndProcess(number, true);
    }

    /** null means use the next random number; otherwise use the number tapped by the player. */
    private void callAndProcess(Integer selected, boolean calledByHuman) {
        if (resultShown || game.getState() != GameState.CALLING) return;
        Integer number = selected == null ? game.callNextNumber() : game.callNumber(selected);
        if (number == null) {
            if (selected != null) textFooterStatus.setText("That number was already called. Tap another number.");
            else finishDraw();
            return;
        }
        sound.playCall();
        textBigNumber.setText(String.valueOf(number));
        appendCalledChip(number);
        BingoCard human = game.getHuman().getCard();
        if (human.markNumber(number)) recolorCell(number);

        List<String> humanLines = recordNewLines(human, humanCompletedLines, true);
        List<String> computerLines = recordNewLines(game.getComputer().getCard(), computerCompletedLines, false);
        StringBuilder progress = new StringBuilder();
        if (!humanLines.isEmpty()) progress.append("YOU: ").append(join(humanLines));
        if (!computerLines.isEmpty()) {
            if (progress.length() > 0) progress.append("   ");
            progress.append("COMPUTER: ").append(join(computerLines));
        }
        if (humanBingoCount >= 5 && computerBingoCount >= 5) { finishResult("Tie — both got B-I-N-G-O", "Both players completed five lines", false); return; }
        if (humanBingoCount >= 5) { finishResult(game.getHuman().getName(), "B-I-N-G-O complete", true); return; }
        if (computerBingoCount >= 5) { finishResult(game.getComputer().getName(), "Computer B-I-N-G-O complete", false); return; }
        if (progress.length() > 0) textFooterStatus.setText(progress + " — keep playing until B-I-N-G-O.");
        if (calledByHuman) setComputerTurn(); else setHumanTurn();
    }

    private List<String> recordNewLines(BingoCard card, boolean[] done, boolean human) {
        List<String> out = new ArrayList<>();
        for (int r=0;r<5;r++) if (!done[r] && rowComplete(card,r)) { done[r]=true; out.add(award(human,"Row "+(r+1))); if(human) crossRow(r); }
        for (int c=0;c<5;c++) if (!done[5+c] && colComplete(card,c)) { done[5+c]=true; out.add(award(human,"Column "+(c+1))); if(human) crossCol(c); }
        if (!done[10] && diagComplete(card,true)) { done[10]=true; out.add(award(human,"Diagonal ↘")); if(human) crossDiag(true); }
        if (!done[11] && diagComplete(card,false)) { done[11]=true; out.add(award(human,"Diagonal ↙")); if(human) crossDiag(false); }
        return out;
    }

    private String award(boolean human, String desc) {
        int count = human ? humanBingoCount : computerBingoCount;
        if (human) humanBingoCount++; else computerBingoCount++;
        return BINGO.charAt(count) + " (" + desc + ")";
    }
    private boolean rowComplete(BingoCard c,int r){for(int x=0;x<5;x++)if(!c.isMarked(r,x))return false;return true;}
    private boolean colComplete(BingoCard c,int x){for(int r=0;r<5;r++)if(!c.isMarked(r,x))return false;return true;}
    private boolean diagComplete(BingoCard c,boolean main){for(int i=0;i<5;i++)if(!c.isMarked(i,main?i:4-i))return false;return true;}
    private void crossRow(int r){for(int c=0;c<5;c++)crossCell(r,c);}
    private void crossCol(int c){for(int r=0;r<5;r++)crossCell(r,c);}
    private void crossDiag(boolean main){for(int i=0;i<5;i++)crossCell(i,main?i:4-i);}
    private void crossCell(int r,int c){TextView v=cellViews[r][c];v.setPaintFlags(v.getPaintFlags()|Paint.STRIKE_THRU_TEXT_FLAG);v.setBackgroundColor(getColor(R.color.cell_marked));}
    private String join(List<String> a){StringBuilder s=new StringBuilder();for(int i=0;i<a.size();i++){if(i>0)s.append(", ");s.append(a.get(i));}return s.toString();}

    private void recolorCell(int number){BingoCard c=game.getHuman().getCard();for(int r=0;r<5;r++)for(int x=0;x<5;x++)if(c.getValue(r,x)==number)cellViews[r][x].setBackgroundColor(getColor(R.color.cell_marked));}
    private void refreshGrid(BingoCard c){for(int r=0;r<5;r++)for(int x=0;x<5;x++){TextView v=cellViews[r][x];v.setText(String.valueOf(c.getValue(r,x)));v.setBackgroundColor(getColor(R.color.bg_cell));v.setPaintFlags(v.getPaintFlags()&~Paint.STRIKE_THRU_TEXT_FLAG);}}
    private void appendCalledChip(int n){TextView v=new TextView(this);v.setText(String.valueOf(n));v.setTextColor(getColor(R.color.bg_deep));v.setBackgroundColor(getColor(R.color.accent_gold));v.setPadding(16,8,16,8);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMargins(3,0,3,0);calledNumbersRow.addView(v,p);calledScroll.post(()->calledScroll.fullScroll(View.FOCUS_RIGHT));}

    private void finishResult(String winner,String pattern,boolean won){
        if(resultShown)return;resultShown=true;game.finish();handler.removeCallbacksAndMessages(null);if(fillTimer!=null)fillTimer.cancel();if(won)sound.playWin();new StatsManager(this).recordComputerGame(won,game.calledSoFar().size());adManager.showInterstitialIfReady(this);
        Intent i=new Intent(this,ResultActivity.class);i.putExtra("winnerName",winner);i.putExtra("pattern",pattern);i.putExtra("mode","computer");startActivity(i);finish();
    }
    private void finishDraw(){finishResult("No winner","No B-I-N-G-O — all 25 numbers called",false);}
    @Override protected void onDestroy(){super.onDestroy();if(fillTimer!=null)fillTimer.cancel();handler.removeCallbacksAndMessages(null);if(sound!=null)sound.release();}
}
