package ro.chess.server.dto;

/**
 * Raspuns trimis cand jocul s-a terminat.
 */
public class GameOverMsg {
    public final String type = "GAME_OVER";
    public final String reason;   // Motivul: CHECKMATE
    public final String result;   // Rezultat: "1-0" sau "0-1"
    public final String winner;   // Castigatorul: "WHITE" sau "BLACK"
    public final String fen;      // Pozitia finala

    public GameOverMsg(String reason, String result, String winner, String fen) {
        this.reason = reason;
        this.result = result;
        this.winner = winner;
        this.fen = fen;
    }
}