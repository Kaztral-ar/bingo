package com.bingo125.game;

import java.util.List;

/**
 * Orchestrates a single VS-Computer match: both cards, the shared called-number
 * sequence, and win detection. (The online equivalent lives server-side; see
 * online/RoomManager and the Firebase rules — the client only renders that state.)
 */
public class BingoGame {

    private final Player human;
    private final ComputerPlayer computer;
    private final List<Integer> callSequence;
    private int callIndex = 0;
    private GameState state = GameState.FILLING;

    public BingoGame(Player human, ComputerPlayer computer) {
        this.human = human;
        this.computer = computer;
        this.callSequence = NumberGenerator.shuffledRange1to25();
    }

    public Player getHuman() { return human; }
    public ComputerPlayer getComputer() { return computer; }
    public GameState getState() { return state; }

    public void startCallingPhase() {
        if (!human.getCard().isComplete()) human.getCard().autoFillRemaining();
        state = GameState.CALLING;
    }

    /** Calls the next number in the shared sequence. Returns null once exhausted. */
    public Integer callNextNumber() {
        if (callIndex >= callSequence.size()) return null;
        int number = callSequence.get(callIndex++);
        computer.onNumberCalled(number); // computer auto-marks immediately
        return number;
    }

    public List<Integer> calledSoFar() {
        return callSequence.subList(0, callIndex);
    }

    /** Call this after the human marks a cell, and after every computer auto-mark. */
    public BingoValidator.WinResult checkHumanWin() {
        return BingoValidator.check(human.getCard());
    }

    public BingoValidator.WinResult checkComputerWin() {
        return BingoValidator.check(computer.getCard());
    }

    public void finish() {
        state = GameState.FINISHED;
    }
}
