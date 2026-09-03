package com.bingo125.util;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Vibrator;

/**
 * Plays short SFX (place, call, mark, win) and drives the vibration feedback.
 * Actual .ogg/.wav assets go in res/raw/ (place_tick, number_call, bingo_win) —
 * add your own audio files there before shipping; this class just wires them up.
 */
public class SoundManager {

    private final SoundPool soundPool;
    private final PrefsManager prefs;
    private final Vibrator vibrator;
    private int soundPlace = -1, soundCall = -1, soundWin = -1;

    public SoundManager(Context context) {
        prefs = new PrefsManager(context);
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build();

        // Uncomment once audio assets exist under res/raw/:
        // soundPlace = soundPool.load(context, R.raw.place_tick, 1);
        // soundCall  = soundPool.load(context, R.raw.number_call, 1);
        // soundWin   = soundPool.load(context, R.raw.bingo_win, 1);
    }

    public void playPlace() { play(soundPlace); }
    public void playCall()  { play(soundCall); }
    public void playWin()   { play(soundWin); vibrate(300); }

    private void play(int soundId) {
        if (prefs.isSoundOn() && soundId != -1) soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
    }

    private void vibrate(long millis) {
        if (prefs.isVibrationOn() && vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(millis);
        }
    }

    public void release() {
        soundPool.release();
    }
}
