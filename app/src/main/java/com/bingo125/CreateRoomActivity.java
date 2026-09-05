package com.bingo125;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.bingo125.online.RoomManager;
import com.bingo125.online.RoomModel;
import com.bingo125.util.PrefsManager;

public class CreateRoomActivity extends AppCompatActivity {
    private final RoomManager roomManager=new RoomManager(); private String roomCode,myUid;
    @Override protected void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_create_room);roomManager.init(this);TextView code=findViewById(R.id.textRoomCode),status=findViewById(R.id.textLobbyStatus);Button start=findViewById(R.id.btnStartGame);
        AuthHelper.ensureSignedIn(this,new AuthHelper.UidCallback(){public void onReady(String uid){myUid=uid;roomManager.createRoom(uid,new PrefsManager(CreateRoomActivity.this).getPlayerName(),new RoomManager.RoomListener(){public void onRoomUpdated(RoomModel r){roomCode=r.roomCode;code.setText(r.roomCode);boolean ready=r.players.size()>=2;status.setText(ready?"Player 2 has joined!":"Waiting for Player 2…");start.setEnabled(ready);if("filling".equals(r.status)){startGameScreen(r.roomCode);}}public void onError(String m){Toast.makeText(CreateRoomActivity.this,m,Toast.LENGTH_LONG).show();}});}public void onError(String m){Toast.makeText(CreateRoomActivity.this,m,Toast.LENGTH_LONG).show();finish();}});
        start.setOnClickListener(v->{if(roomCode!=null)roomManager.startGame(roomCode);});}
    private void startGameScreen(String code){Intent i=new Intent(this,OnlineGameActivity.class);i.putExtra("roomCode",code);i.putExtra("uid",myUid);startActivity(i);finish();}
    @Override protected void onDestroy(){super.onDestroy();roomManager.stopListening();}
}
