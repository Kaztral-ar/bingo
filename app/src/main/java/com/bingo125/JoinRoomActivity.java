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

    private final RoomManager roomManager = new RoomManager();
    private String myUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_room);

        EditText editCode = findViewById(R.id.editRoomCode);
        TextView textError = findViewById(R.id.textJoinError);

        findViewById(R.id.btnJoin).setOnClickListener(v -> {
            String code = editCode.getText().toString().trim();
            if (code.length() != 6) {
                textError.setText("Enter a valid 6-digit code.");
                return;
            }
            textError.setText("");

            AuthHelper.ensureSignedIn(new AuthHelper.UidCallback() {
                @Override
                public void onReady(String uid) {
                    myUid = uid;
                    String name = new PrefsManager(JoinRoomActivity.this).getPlayerName();
                    roomManager.joinRoom(code, uid, name, new RoomManager.RoomListener() {
                        @Override
                        public void onRoomUpdated(RoomModel room) {
                            Intent intent = new Intent(JoinRoomActivity.this, LobbyActivity.class);
                            intent.putExtra("roomCode", room.roomCode);
                            intent.putExtra("uid", myUid);
                            startActivity(intent);
                            finish();
                        }

                        @Override
                        public void onError(String message) {
                            textError.setText(message);
                        }
                    });
                }

                @Override
                public void onError(String message) {
                    textError.setText(message);
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        roomManager.stopListening();
    }
}
