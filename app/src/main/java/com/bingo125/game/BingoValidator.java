package com.bingo125.game;

/**
 * Checks a card's marked cells for a completed row, column, or diagonal.
 * Used identically for VS Computer and (server-side) for online games.
 */
public class BingoValidator {

    public enum PatternType { NONE, ROW, COLUMN, DIAGONAL }

    public static class WinResult {
        public final PatternType type;
        public final int index; // row/col index, or 0 = main diagonal, 1 = anti-diagonal
        public WinResult(PatternType type, int index) {
            this.type = type;
            this.index = index;
        }
        public boolean isWin() { return type != PatternType.NONE; }

        public String describe() {
            switch (type) {
                case ROW: return "Row " + (index + 1);
                case COLUMN: return "Column " + (index + 1);
                case DIAGONAL: return index == 0 ? "Diagonal (top-left to bottom-right)"
                                                  : "Diagonal (top-right to bottom-left)";
                default: return "None";
            }
        }
    }

    public static WinResult check(BingoCard card) {
        int size = BingoCard.SIZE;

        for (int r = 0; r < size; r++) {
            boolean full = true;
            for (int c = 0; c < size; c++) if (!card.isMarked(r, c)) { full = false; break; }
            if (full) return new WinResult(PatternType.ROW, r);
        }

        for (int c = 0; c < size; c++) {
            boolean full = true;
            for (int r = 0; r < size; r++) if (!card.isMarked(r, c)) { full = false; break; }
            if (full) return new WinResult(PatternType.COLUMN, c);
        }

        boolean mainDiag = true;
        for (int i = 0; i < size; i++) if (!card.isMarked(i, i)) { mainDiag = false; break; }
        if (mainDiag) return new WinResult(PatternType.DIAGONAL, 0);

        boolean antiDiag = true;
        for (int i = 0; i < size; i++) if (!card.isMarked(i, size - 1 - i)) { antiDiag = false; break; }
        if (antiDiag) return new WinResult(PatternType.DIAGONAL, 1);

        return new WinResult(PatternType.NONE, -1);
    }
}
