package ro.chess.server.dto;

/**
 * Raspuns trimis in caz de eroare.
 */
public class ErrorMsg {
    public final String type = "ERROR";
    public final String message;  // Mesajul de eroare

    public ErrorMsg(String message) {
        this.message = message;
    }
}