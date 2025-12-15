package ro.chess.client.util;

public final class BoardUtils {
    private static final char[] FILES = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'};

    /**
     * row: 0..7 (de sus in jos), col: 0..7 (stanga->dreapta)
     */
    public static String toSquare(int row, int col) {
        char file = FILES[col];     // a..h
        int rank = 8 - row;         // 8 sus -> 1 jos
        return "" + file + rank;
    }

    /**
     * Accepta 'a'..'h' sau 'A'..'H'
     */
    public static int fileIndex(char file) {
        char f = Character.toLowerCase(file);
        return f - 'a';             // 0..7
    }

    /**
     * Accepta '1'..'8' (sau orice char numeric)
     */
    public static int rankIndex(char rank) {
        int r = Character.getNumericValue(rank); // 1..8
        return 8 - r;                             // 7..0
    }

    private BoardUtils() {
    }
}