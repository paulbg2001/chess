package ro.chess.server.model;

public class GameState {
    private String[][] board;      // Copia tablei
    private boolean whiteTurn;     // Al cui rand era

    public GameState(String[][] b, boolean wt) {
        // Facem o copie profunda a tablei
        this.board = new String[8][8];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                this.board[r][c] = b[r][c];
            }
        }
        this.whiteTurn = wt;
    }

    public String[][] getBoard() {
        return board;
    }

    public void setBoard(String[][] board) {
        this.board = board;
    }

    public boolean isWhiteTurn() {
        return whiteTurn;
    }

    public void setWhiteTurn(boolean whiteTurn) {
        this.whiteTurn = whiteTurn;
    }
}