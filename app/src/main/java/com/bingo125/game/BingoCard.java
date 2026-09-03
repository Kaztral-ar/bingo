package com.bingo125.game;

/**
 * A single player's 5x5 card: the number grid plus which cells are marked.
 * 0 in the grid means "not yet placed" during the filling phase.
 */
public class BingoCard {

    public static final int SIZE = 5;

    private final int[][] grid = new int[SIZE][SIZE];
    private final boolean[][] marked = new boolean[SIZE][SIZE];
    private int nextNumberToPlace = 1;

    public boolean placeNumber(int row, int col, int number) {
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) return false;
        if (grid[row][col] != 0) return false;           // cell already filled
        if (number != nextNumberToPlace) return false;    // must place in order 1..25
        grid[row][col] = number;
        nextNumberToPlace++;
        return true;
    }

    public int getNextNumberToPlace() {
        return nextNumberToPlace;
    }

    public boolean isComplete() {
        return nextNumberToPlace > 25;
    }

    /** Fills every remaining empty cell with the still-unused numbers, in ascending order. */
    public void autoFillRemaining() {
        boolean[] used = new boolean[26];
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++)
                if (grid[r][c] != 0) used[grid[r][c]] = true;

        int candidate = 1;
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (grid[r][c] == 0) {
                    while (candidate <= 25 && used[candidate]) candidate++;
                    if (candidate <= 25) {
                        grid[r][c] = candidate;
                        used[candidate] = true;
                    }
                }
            }
        }
        nextNumberToPlace = 26;
    }

    public int getValue(int row, int col) {
        return grid[row][col];
    }

    public boolean isMarked(int row, int col) {
        return marked[row][col];
    }

    /** Marks a called number on this card, if present. Returns true if it was found & marked. */
    public boolean markNumber(int number) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (grid[r][c] == number) {
                    marked[r][c] = true;
                    return true;
                }
            }
        }
        return false;
    }

    public boolean containsNumber(int number) {
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++)
                if (grid[r][c] == number) return true;
        return false;
    }

    public int markedCount() {
        int count = 0;
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++)
                if (marked[r][c]) count++;
        return count;
    }

    public int[][] getGrid() {
        return grid;
    }
}
