package com.bingo125.game;

public enum GameState {
    FILLING,   // players are arranging their card (2-minute timer running)
    CALLING,   // numbers are being called, players mark and check for bingo
    FINISHED   // a winner has been confirmed
}
