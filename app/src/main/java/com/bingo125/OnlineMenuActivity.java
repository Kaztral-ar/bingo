package com.bingo125;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class OnlineMenuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_online_menu);

        findViewById(R.id.btnCreateRoom).setOnClickListener(v ->
                startActivity(new Intent(this, CreateRoomActivity.class)));

        findViewById(R.id.btnJoinRoom).setOnClickListener(v ->
                startActivity(new Intent(this, JoinRoomActivity.class)));
    }
}
