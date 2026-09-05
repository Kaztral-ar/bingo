package com.bingo125;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.bingo125.online.RoomManager;
import com.bingo125.online.RoomModel;
import com.bingo125.util.PrefsManager;

public class JoinRoomActivity extends AppCompatActivity {
    private final RoomManager roomManager=new RoomManager(); private String myUid;
    @Override protected void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_join_room);roomManager.init(this);EditText code=findViewById(R.id.editRoomCode);TextView error=findViewById(R.id.textJoinError);
        findViewById(R.id.btnJoin).setOnClickListener(v->{String c=code.getText().toString().trim();if(c.length()!=6){error.setText("Enter a valid 6-digit code.");return;}error.setText("");AuthHelper.ensureSignedIn(this,new AuthHelper.UidCallback(){public void onReady(String uid){myUid=uid;roomManager.joinRoom(c,uid,new PrefsManager(JoinRoomActivity.this).getPlayerName(),new RoomManager.RoomListener(){public void onRoomUpdated(RoomModel r){Intent i=new Intent(JoinRoomActivity.this,LobbyActivity.class);i.putExtra("roomCode",r.roomCode);i.putExtra("uid",myUid);startActivity(i);finish();}public void onError(String m){error.setText(m);}});}public void onError(String m){error.setText(m);}});});}
    @Override protected void onDestroy(){super.onDestroy();roomManager.stopListening();}
}
