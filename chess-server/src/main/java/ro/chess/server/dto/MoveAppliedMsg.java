package ro.chess.server.dto;

/**
 * Raspuns trimis dupa o mutare reusita.
 */
public class MoveAppliedMsg {
    public final String type = "MOVE_APPLIED";
    public final String fen;      // Pozitia actuala
    public final boolean check;   // Daca e sah (nefolosit in versiunea simplificata)

    public MoveAppliedMsg(String fen, boolean check) {
        this.fen = fen;
        this.check = check;
    }
}