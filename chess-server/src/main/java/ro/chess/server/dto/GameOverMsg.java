package ro.chess.server.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Raspuns trimis cand jocul s-a terminat.
 */
public class GameOverMsg extends Message {
    private String reason;   // Motivul: CHECKMATE
    private String result;   // Rezultat: "1-0" sau "0-1"
    private String winner;   // Castigatorul: "WHITE" sau "BLACK"
    private String fen;      // Pozitia finala

    public GameOverMsg(String reason, String result, String winner, String fen) {
        super("GAME_OVER");
        this.reason = reason;
        this.result = result;
        this.winner = winner;
        this.fen = fen;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getWinner() {
        return winner;
    }

    public void setWinner(String winner) {
        this.winner = winner;
    }

    public String getFen() {
        return fen;
    }

    public void setFen(String fen) {
        this.fen = fen;
    }
}