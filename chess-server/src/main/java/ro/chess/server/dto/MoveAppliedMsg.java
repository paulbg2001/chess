package ro.chess.server.dto;

/**
 * Raspuns trimis dupa o mutare reusita.
 */
public class MoveAppliedMsg extends Message {
    private String fen;
    private boolean check;

    public MoveAppliedMsg(String fen, boolean check) {
        super("MOVE_APPLIED");
        this.fen = fen;
        this.check = check;
    }

    public String getFen() {
        return fen;
    }

    public void setFen(String fen) {
        this.fen = fen;
    }

    public boolean isCheck() {
        return check;
    }

    public void setCheck(boolean check) {
        this.check = check;
    }
}