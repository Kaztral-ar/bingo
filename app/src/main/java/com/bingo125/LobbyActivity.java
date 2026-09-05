package com.bingo125;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.bingo125.online.RoomManager;
import com.bingo125.online.RoomModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LobbyActivity extends AppCompatActivity {
    private final RoomManager roomManager=new RoomManager(); private String roomCode,myUid;
    @Override protected void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_lobby);roomManager.init(this);roomCode=getIntent().getStringExtra("roomCode");myUid=getIntent().getStringExtra("uid");TextView p1=findViewById(R.id.textPlayer1),p2=findViewById(R.id.textPlayer2),msg=findViewById(R.id.textLobbyMessage);
        roomManager.listenToRoom(roomCode,new RoomManager.RoomListener(){public void onRoomUpdated(RoomModel r){List<String> n=new ArrayList<>();for(Map.Entry<String,RoomModel.PlayerModel> e:r.players.entrySet())n.add(e.getValue().name+"  ✓ Ready");p1.setText(n.size()>0?"👤 "+n.get(0):"👤 Waiting…");p2.setText(n.size()>1?"👤 "+n.get(1):"👤 Waiting for opponent…");msg.setText("Waiting for host to start...");if("filling".equals(r.status)){Intent i=new Intent(LobbyActivity.this,OnlineGameActivity.class);i.putExtra("roomCode",roomCode);i.putExtra("uid",myUid);startActivity(i);finish();}}public void onError(String m){Toast.makeText(LobbyActivity.this,m,Toast.LENGTH_LONG).show();}});}
    @Override protected void onDestroy(){super.onDestroy();roomManager.stopListening();}
}
