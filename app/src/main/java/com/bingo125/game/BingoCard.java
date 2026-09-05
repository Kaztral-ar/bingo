package com.bingo125.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A single player's 5x5 card containing numbers 1..25 and marked cells. */
public class BingoCard {
    public static final int SIZE = 5;
    public static final int MAX_NUMBER = SIZE * SIZE;

    private final int[][] grid = new int[SIZE][SIZE];
    private final boolean[][] marked = new boolean[SIZE][SIZE];
    private int nextNumberToPlace = 1;

    public boolean placeNumber(int row, int col, int number) {
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) return false;
        if (number < 1 || number > MAX_NUMBER) return false;
        if (grid[row][col] != 0) return false;
        if (number != nextNumberToPlace) return false;
        grid[row][col] = number;
        nextNumberToPlace++;
        return true;
    }

    public int getNextNumberToPlace() { return nextNumberToPlace; }
    public boolean isComplete() { return nextNumberToPlace > MAX_NUMBER; }

    /** Fills remaining cells with unused numbers in ascending order. */
    public void autoFillRemaining() { fillRemaining(false); }

    /** Fills remaining cells with unused numbers in a random order. */
    public void randomFillRemaining() { fillRemaining(true); }

    private void fillRemaining(boolean random) {
        boolean[] used = new boolean[MAX_NUMBER + 1];
        for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++) {
            int value = grid[r][c];
            if (value >= 1 && value <= MAX_NUMBER) used[value] = true;
        }
        List<Integer> unused = new ArrayList<>();
        for (int n = 1; n <= MAX_NUMBER; n++) if (!used[n]) unused.add(n);
        if (random) Collections.shuffle(unused);
        int index = 0;
        for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++) {
            if (grid[r][c] == 0 && index < unused.size()) grid[r][c] = unused.get(index++);
        }
        nextNumberToPlace = MAX_NUMBER + 1;
    }

    public int getValue(int row, int col) { return grid[row][col]; }
    public boolean isMarked(int row, int col) { return marked[row][col]; }

    public boolean markNumber(int number) {
        if (number < 1 || number > MAX_NUMBER) return false;
        for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++) {
            if (grid[r][c] == number) {
                if (marked[r][c]) return false;
                marked[r][c] = true;
                return true;
            }
        }
        return false;
    }

    public boolean containsNumber(int number) {
        if (number < 1 || number > MAX_NUMBER) return false;
        for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++) if (grid[r][c] == number) return true;
        return false;
    }

    public int markedCount() {
        int count = 0;
        for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++) if (marked[r][c]) count++;
        return count;
    }

    public int[][] getGrid() { return grid; }
}
