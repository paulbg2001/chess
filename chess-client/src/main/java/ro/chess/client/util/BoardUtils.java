package ro.chess.client.util;

/**
 * Utilitare pentru coordonatele tablei de sah.
 * Ne ajuta sa transformam numere (0,0) in stil sah (a8).
 */
public class BoardUtils {
    private static final char[] COLOANE = { 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h' };

    /**
     * Transforma indecsii din matrice in notatie de sah.
     * Ex: rand 0, col 0 -> "a8"
     */
    public static String toSquare(int rand, int coloana) {
        char litera = COLOANE[coloana]; // a..h
        int cifra = 8 - rand; // 8 e sus (index 0), 1 e jos (index 7)
        return "" + litera + cifra;
    }

    /**
     * Transforma litera coloanei ('a'..'h') in numar (0..7).
     */
    public static int getIndexColoana(char litera) {
        char f = Character.toLowerCase(litera);
        return f - 'a'; // 'a' - 'a' = 0
    }

    /**
     * Transforma cifra randului ('1'..'8') in index (7..0).
     */
    public static int getIndexRand(char cifraChar) {
        int cifra = Character.getNumericValue(cifraChar); // '8' -> 8
        return 8 - cifra; // 8 - 8 = 0 (primul rand de sus)
    }
}
