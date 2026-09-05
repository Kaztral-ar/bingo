package com.bingo125.game;

import java.util.List;

/**
 * Controls one offline VS-Computer match: card setup, one shared call deck,
 * marking, and win-state transitions.
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

    /** Starts calling exactly once. */
    public void startCallingPhase() {
        if (state != GameState.FILLING) return;
        if (!human.getCard().isComplete()) human.getCard().autoFillRemaining();
        state = GameState.CALLING;
    }

    /** Calls the next unique number. Returns null when the deck is exhausted or the game is over. */
    public Integer callNextNumber() {
        if (state != GameState.CALLING || callIndex >= callSequence.size()) return null;
        int number = callSequence.get(callIndex++);
        computer.onNumberCalled(number);
        return number;
    }

    public List<Integer> calledSoFar() {
        return callSequence.subList(0, callIndex);
    }

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
