package ro.chess.server.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Raspuns trimis in caz de eroare.
 */
public class ErrorMsg extends Message {
    private String message;

    public ErrorMsg(String message) {
        super("ERROR");
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}