package com.bingo125;

import android.os.Bundle;
import android.widget.Switch;

import androidx.appcompat.app.AppCompatActivity;

import com.bingo125.util.PrefsManager;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        PrefsManager prefs = new PrefsManager(this);

        Switch sSound = findViewById(R.id.switchSound);
        Switch sVibration = findViewById(R.id.switchVibration);
        Switch sAutoMark = findViewById(R.id.switchAutoMark);
        Switch sCallSound = findViewById(R.id.switchCallSound);
        Switch sDarkTheme = findViewById(R.id.switchDarkTheme);

        sSound.setChecked(prefs.isSoundOn());
        sVibration.setChecked(prefs.isVibrationOn());
        sAutoMark.setChecked(prefs.isAutoMarkOn());
        sCallSound.setChecked(prefs.isCallSoundOn());
        sDarkTheme.setChecked(prefs.isDarkTheme());

        sSound.setOnCheckedChangeListener((b, checked) -> prefs.setSoundOn(checked));
        sVibration.setOnCheckedChangeListener((b, checked) -> prefs.setVibrationOn(checked));
        sAutoMark.setOnCheckedChangeListener((b, checked) -> prefs.setAutoMarkOn(checked));
        sCallSound.setOnCheckedChangeListener((b, checked) -> prefs.setCallSoundOn(checked));
        sDarkTheme.setOnCheckedChangeListener((b, checked) -> prefs.setDarkTheme(checked));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }
}
