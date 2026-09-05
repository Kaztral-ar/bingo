package com.bingo125.game;

import java.util.List;

/** Controls one offline VS-Computer match. */
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
        if (state != GameState.FILLING) return;
        if (!human.getCard().isComplete()) human.getCard().autoFillRemaining();
        state = GameState.CALLING;
    }

    public Integer callNextNumber() {
        if (state != GameState.CALLING || callIndex >= callSequence.size()) return null;
        return callSelectedIndex(callIndex);
    }

    /** Calls a specific number selected by the player by tapping it. */
    public Integer callNumber(int number) {
        if (state != GameState.CALLING || number < 1 || number > 25) return null;
        for (int i = callIndex; i < callSequence.size(); i++) {
            if (callSequence.get(i) == number) {
                if (i != callIndex) {
                    Integer tmp = callSequence.get(callIndex);
                    callSequence.set(callIndex, callSequence.get(i));
                    callSequence.set(i, tmp);
                }
                return callSelectedIndex(callIndex);
            }
        }
        return null;
    }

    private Integer callSelectedIndex(int index) {
        if (index != callIndex || callIndex >= callSequence.size()) return null;
        int number = callSequence.get(callIndex++);
        computer.onNumberCalled(number);
        return number;
    }

    public List<Integer> calledSoFar() { return callSequence.subList(0, callIndex); }
    public BingoValidator.WinResult checkHumanWin() { return BingoValidator.check(human.getCard()); }
    public BingoValidator.WinResult checkComputerWin() { return BingoValidator.check(computer.getCard()); }
    public void finish() { state = GameState.FINISHED; }
}
