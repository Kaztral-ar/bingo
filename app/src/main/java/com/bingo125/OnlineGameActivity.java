package com.bingo125;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.bingo125.online.RoomManager;
import com.bingo125.online.RoomModel;
import com.bingo125.online.SupabaseManager;
import com.bingo125.util.AdManager;
import com.bingo125.util.SoundManager;
import com.bingo125.util.StatsManager;
import java.util.ArrayList;
import java.util.List;

/** Online Bingo using Supabase. Cards fill simultaneously; calling alternates and marks both cards automatically. */
public class OnlineGameActivity extends AppCompatActivity {
    private final RoomManager rooms=new RoomManager(); private String roomCode,myUid; private RoomModel latest; private boolean resultShown,promptShown; private CountDownTimer fillTimer; private int[][] myCard=new int[5][5]; private int nextNumber=1; private int lastCalls;
    private GridLayout grid; private final TextView[][] cells=new TextView[5][5]; private TextView status,big,timer,footer; private LinearLayout calledRow; private HorizontalScrollView calledScroll; private Button callButton; private SoundManager sound; private final AdManager ads=new AdManager();
    @Override protected void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_computer_game);rooms.init(this);roomCode=getIntent().getStringExtra("roomCode");myUid=getIntent().getStringExtra("uid");if(roomCode==null||myUid==null){finish();return;}sound=new SoundManager(this);ads.loadInterstitial(this);grid=findViewById(R.id.bingoGrid);status=findViewById(R.id.textStatusLabel);big=findViewById(R.id.textBigNumber);timer=findViewById(R.id.textTimer);footer=findViewById(R.id.textFooterStatus);calledRow=findViewById(R.id.calledNumbersRow);calledScroll=findViewById(R.id.calledScroll);callButton=findViewById(R.id.btnCallNumber);callButton.setVisibility(View.VISIBLE);buildGrid();rooms.listenToRoom(roomCode,new RoomManager.RoomListener(){public void onRoomUpdated(RoomModel r){render(r);}public void onError(String m){Toast.makeText(OnlineGameActivity.this,m,Toast.LENGTH_SHORT).show();}});}
    private void buildGrid(){grid.removeAllViews();for(int r=0;r<5;r++)for(int c=0;c<5;c++){TextView v=new TextView(this);GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=0;p.height=0;p.rowSpec=GridLayout.spec(r,1f);p.columnSpec=GridLayout.spec(c,1f);int m=(int)(getResources().getDisplayMetrics().density*3);p.setMargins(m,m,m,m);v.setLayoutParams(p);v.setGravity(Gravity.CENTER);v.setTextSize(18);v.setBackgroundColor(getColor(R.color.bg_cell));v.setTextColor(getColor(R.color.text_primary));grid.addView(v);cells[r][c]=v;}}
    private void render(RoomModel r){if(resultShown)return;latest=r;if("filling".equals(r.status)){status.setText("Fill Your Bingo Card");footer.setText("Tap cells to place 1 → 25");renderMyCard(r);if(r.fillDeadline!=null&&fillTimer==null){long rem=Math.max(0,r.fillDeadline-System.currentTimeMillis());fillTimer=new CountDownTimer(rem,1000){public void onTick(long x){timer.setText(String.format("Time Left: %d:%02d",x/60000,(x/1000)%60));}public void onFinish(){timer.setText("Time Left: 0:00");maybeStartCalling(r);}}.start();}if(allLocked(r))maybeStartCalling(r);}
        else if("calling".equals(r.status)){if(fillTimer!=null){fillTimer.cancel();fillTimer=null;}renderMyCard(r);calledScroll.setVisibility(View.VISIBLE);renderCalls(r.calledNumbers);RoomModel.PlayerModel me=r.players.get(myUid);int count=me==null?0:Math.min(5,me.bingoCount);status.setText("B-I-N-G-O  •  "+progress(count));String first=r.firstCaller==null?r.host:r.firstCaller;String other=otherPlayer(r,first);int calls=r.calledNumbers==null?0:r.calledNumbers.size();String expected=calls%2==0?first:other;boolean myTurn=myUid.equals(expected);callButton.setEnabled(myTurn);callButton.setText(myTurn?"CALL NUMBER":"WAIT FOR OPPONENT");footer.setText(myTurn?"Your turn — call one number. Both cards mark automatically.":"Opponent's turn — wait for the next call.");callButton.setOnClickListener(v->{callButton.setEnabled(false);rooms.callNumber(roomCode,new SupabaseManager.Callback(){public void success(org.json.JSONObject x){}public void error(String m){}});});}
        else if("finished".equals(r.status)&&r.winnerUid!=null)showResult(r);}
    private void renderMyCard(RoomModel r){RoomModel.PlayerModel me=r.players.get(myUid);if(me==null||me.card==null)return;myCard=me.card;for(int i=0;i<5;i++)for(int j=0;j<5;j++){cells[i][j].setText(String.valueOf(myCard[i][j]));boolean marked=r.calledNumbers!=null&&r.calledNumbers.contains(myCard[i][j]);cells[i][j].setBackgroundColor(marked?getColor(R.color.cell_marked):getColor(R.color.bg_cell));}}
    private void place(int row,int col){if(latest==null||!"filling".equals(latest.status)||latest.players.get(myUid)==null)return;if(myCard[row][col]!=0)return;myCard[row][col]=nextNumber++;cells[row][col].setText(String.valueOf(myCard[row][col]));sound.playPlace();rooms.saveCard(roomCode,myUid,myCard,nextNumber>25);if(nextNumber>25)footer.setText("Card complete — waiting for opponent…");}
    private void renderCalls(List<Integer> called){if(called==null)return;if(called.size()<lastCalls){lastCalls=0;calledRow.removeAllViews();}for(int i=lastCalls;i<called.size();i++){sound.playCall();appendChip(called.get(i));}lastCalls=called.size();highlightLines();}
    private void appendChip(int n){TextView t=new TextView(this);t.setText(String.valueOf(n));t.setTextColor(getColor(R.color.bg_deep));t.setBackgroundColor(getColor(R.color.accent_gold));t.setPadding(24,12,24,12);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMargins(6,0,6,0);calledRow.addView(t,p);calledScroll.post(()->calledScroll.fullScroll(View.FOCUS_RIGHT));}
    private void highlightLines(){if(latest==null||myCard==null)return;boolean[][] m=new boolean[5][5];List<Integer> c=latest.calledNumbers==null?new ArrayList<>():latest.calledNumbers;for(int r=0;r<5;r++)for(int j=0;j<5;j++)m[r][j]=c.contains(myCard[r][j]);for(int r=0;r<5;r++){boolean ok=true;for(int j=0;j<5;j++)ok&=m[r][j];if(ok)for(int j=0;j<5;j++)cells[r][j].setBackgroundColor(getColor(R.color.accent_gold));}for(int j=0;j<5;j++){boolean ok=true;for(int r=0;r<5;r++)ok&=m[r][j];if(ok)for(int r=0;r<5;r++)cells[r][j].setBackgroundColor(getColor(R.color.accent_gold));}boolean a=true,d=true;for(int i=0;i<5;i++){a&=m[i][i];d&=m[i][4-i];}if(a)for(int i=0;i<5;i++)cells[i][i].setBackgroundColor(getColor(R.color.accent_gold));if(d)for(int i=0;i<5;i++)cells[i][4-i].setBackgroundColor(getColor(R.color.accent_gold));}
    private String progress(int n){String s="";String x="BINGO";for(int i=0;i<5;i++){if(i>0)s+="  ";s+=i<n?x.charAt(i)+" ✓":String.valueOf(x.charAt(i));}return s;}
    private boolean allLocked(RoomModel r){if(r.players.size()<2)return false;for(RoomModel.PlayerModel p:r.players.values())if(!p.cardLocked)return false;return true;}
    private String otherPlayer(RoomModel r,String first){for(String id:r.players.keySet())if(!id.equals(first))return id;return null;}
    private void maybeStartCalling(RoomModel r){if(promptShown||resultShown||!allLocked(r)||!myUid.equals(r.host))return;promptShown=true;new AlertDialog.Builder(this).setTitle("Who calls first?").setMessage("Calling alternates after every number. Both cards mark automatically.").setNegativeButton("YOU / HOST",(d,w)->rooms.startCalling(roomCode,myUid)).setPositiveButton("OPPONENT",(d,w)->rooms.startCalling(roomCode,otherPlayer(r,myUid))).setCancelable(false).show();}
    private void showResult(RoomModel r){resultShown=true;rooms.stopListening();if(fillTimer!=null)fillTimer.cancel();boolean won=myUid.equals(r.winnerUid);if(won)sound.playWin();new StatsManager(this).recordOnlineGame(won,lastCalls);ads.showInterstitialIfReady(this);RoomModel.PlayerModel p=r.players.get(r.winnerUid);Intent i=new Intent(this,ResultActivity.class);i.putExtra("winnerName",p==null?"Opponent":p.name);i.putExtra("pattern",r.winningPattern==null?"B-I-N-G-O":r.winningPattern);i.putExtra("mode","online");startActivity(i);finish();}
    @Override public void onBackPressed(){rooms.stopListening();super.onBackPressed();}
    @Override protected void onDestroy(){super.onDestroy();if(fillTimer!=null)fillTimer.cancel();rooms.stopListening();if(sound!=null)sound.release();}
}
