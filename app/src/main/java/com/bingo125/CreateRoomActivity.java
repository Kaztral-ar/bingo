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

    private final RoomManager roomManager = new RoomManager();
    private String roomCode;
    private String myUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_room);

        TextView textCode = findViewById(R.id.textRoomCode);
        TextView textStatus = findViewById(R.id.textLobbyStatus);
        Button btnStart = findViewById(R.id.btnStartGame);

        AuthHelper.ensureSignedIn(new AuthHelper.UidCallback() {
            @Override
            public void onReady(String uid) {
                myUid = uid;
                String name = new PrefsManager(CreateRoomActivity.this).getPlayerName();
                roomManager.createRoom(uid, name, new RoomManager.RoomListener() {
                    @Override
                    public void onRoomUpdated(RoomModel room) {
                        roomCode = room.roomCode;
                        textCode.setText(room.roomCode);

                        int playerCount = room.players.size();
                        boolean ready = playerCount >= 2;
                        textStatus.setText(ready ? "Player 2 has joined!" : "Waiting for Player 2…");
                        btnStart.setEnabled(ready);

                        if ("filling".equals(room.status)) {
                            Intent intent = new Intent(CreateRoomActivity.this, OnlineGameActivity.class);
                            intent.putExtra("roomCode", room.roomCode);
                            intent.putExtra("uid", myUid);
                            startActivity(intent);
                            finish();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(CreateRoomActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(String message) {
                Toast.makeText(CreateRoomActivity.this, message, Toast.LENGTH_LONG).show();
                finish();
            }
        });

        btnStart.setOnClickListener(v -> {
            if (roomCode != null) roomManager.startGame(roomCode);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        roomManager.stopListening();
    }
}
