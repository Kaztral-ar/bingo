package com.bingo125.game;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Produces shuffled 1..25 sequences using a cryptographically strong RNG,
 * for both card layouts and the official number-calling order.
 */
public class NumberGenerator {

    private static final SecureRandom RNG = new SecureRandom();

    /** Returns a shuffled list containing each of 1..25 exactly once. */
    public static List<Integer> shuffledRange1to25() {
        List<Integer> numbers = new ArrayList<>(25);
        for (int i = 1; i <= 25; i++) numbers.add(i);
        Collections.shuffle(numbers, RNG);
        return numbers;
    }

    /** Convenience: a fully random, valid 5x5 card (used for the computer opponent). */
    public static int[][] randomCard() {
        List<Integer> shuffled = shuffledRange1to25();
        int[][] card = new int[5][5];
        int k = 0;
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                card[r][c] = shuffled.get(k++);
            }
        }
        return card;
    }
}
