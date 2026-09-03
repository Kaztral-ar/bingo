package com.bingo125.util;

import android.content.Context;
import android.content.SharedPreferences;

/** Thin wrapper around SharedPreferences for Settings + small persisted values. */
public class PrefsManager {

    private static final String PREFS = "bingo125_prefs";
    private final SharedPreferences prefs;

    public PrefsManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isSoundOn()        { return prefs.getBoolean("sound_on", true); }
    public void setSoundOn(boolean v) { prefs.edit().putBoolean("sound_on", v).apply(); }

    public boolean isVibrationOn()        { return prefs.getBoolean("vibration_on", true); }
    public void setVibrationOn(boolean v) { prefs.edit().putBoolean("vibration_on", v).apply(); }

    public boolean isAutoMarkOn()        { return prefs.getBoolean("auto_mark_on", false); }
    public void setAutoMarkOn(boolean v) { prefs.edit().putBoolean("auto_mark_on", v).apply(); }

    public boolean isCallSoundOn()        { return prefs.getBoolean("call_sound_on", true); }
    public void setCallSoundOn(boolean v) { prefs.edit().putBoolean("call_sound_on", v).apply(); }

    public boolean isDarkTheme()        { return prefs.getBoolean("dark_theme", true); }
    public void setDarkTheme(boolean v) { prefs.edit().putBoolean("dark_theme", v).apply(); }

    public String getPlayerName()          { return prefs.getString("player_name", "Player"); }
    public void setPlayerName(String name) { prefs.edit().putString("player_name", name).apply(); }
}
