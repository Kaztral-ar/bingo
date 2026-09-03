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

    private final RoomManager roomManager = new RoomManager();
    private String roomCode, myUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby);

        roomCode = getIntent().getStringExtra("roomCode");
        myUid = getIntent().getStringExtra("uid");

        TextView textP1 = findViewById(R.id.textPlayer1);
        TextView textP2 = findViewById(R.id.textPlayer2);
        TextView textMessage = findViewById(R.id.textLobbyMessage);

        roomManager.listenToRoom(roomCode, new RoomManager.RoomListener() {
            @Override
            public void onRoomUpdated(RoomModel room) {
                List<String> names = new ArrayList<>();
                for (Map.Entry<String, RoomModel.PlayerModel> e : room.players.entrySet()) {
                    names.add(e.getValue().name + "  ✓ Ready");
                }
                textP1.setText(names.size() > 0 ? "👤 " + names.get(0) : "👤 Waiting…");
                textP2.setText(names.size() > 1 ? "👤 " + names.get(1) : "👤 Waiting for opponent…");
                textMessage.setText("Waiting for host to start...");

                if ("filling".equals(room.status)) {
                    Intent intent = new Intent(LobbyActivity.this, OnlineGameActivity.class);
                    intent.putExtra("roomCode", roomCode);
                    intent.putExtra("uid", myUid);
                    startActivity(intent);
                    finish();
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(LobbyActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        roomManager.stopListening();
    }
}
