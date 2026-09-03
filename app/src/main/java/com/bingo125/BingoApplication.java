package com.bingo125;

import android.app.Application;

import com.bingo125.util.AdManager;

public class BingoApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AdManager.init(this);
    }
}
