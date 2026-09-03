package com.bingo125.game;

/**
 * The computer opponent: fills its own card instantly with a random valid
 * arrangement, then auto-marks called numbers as they arrive.
 */
public class ComputerPlayer extends Player {

    public ComputerPlayer(String name) {
        super(name);
        fillCardInstantly();
    }

    private void fillCardInstantly() {
        // Place numbers 1..25 in a random valid layout, in order, into random empty cells.
        java.util.List<int[]> emptyCells = new java.util.ArrayList<>();
        for (int r = 0; r < BingoCard.SIZE; r++)
            for (int c = 0; c < BingoCard.SIZE; c++)
                emptyCells.add(new int[]{r, c});
        java.util.Collections.shuffle(emptyCells, new java.security.SecureRandom());

        for (int number = 1; number <= 25; number++) {
            int[] cell = emptyCells.get(number - 1);
            getCard().placeNumber(cell[0], cell[1], number);
        }
    }

    /** Mark the called number on this computer's card if it's present there. */
    public void onNumberCalled(int number) {
        getCard().markNumber(number);
    }
}
