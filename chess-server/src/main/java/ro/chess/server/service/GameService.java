package ro.chess.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;

@Service
public class GameService {

  // Board: 8x8, null = empty, otherwise piece like "wK", "bQ", "wP", etc.
  private String[][] board = new String[8][8];
  // true = white's turn, false = black's turn
  private boolean whiteTurn = true;
  private final ObjectMapper om = new ObjectMapper();
  
  // History for undo - stores snapshots of (board state, whiteTurn)
  private final Deque<GameState> history = new ArrayDeque<>();
  
  private static class GameState {
    final String[][] board;
    final boolean whiteTurn;
    GameState(String[][] b, boolean wt) {
      this.board = new String[8][8];
      for (int r = 0; r < 8; r++) {
        for (int c = 0; c < 8; c++) {
          this.board[r][c] = b[r][c];
        }
      }
      this.whiteTurn = wt;
    }
  }

  public GameService() {
    resetBoard();
  }

  /** Resetează tabla și returnează FEN-ul inițial */
  public String resetGame() throws Exception {
    resetBoard();
    history.clear();
    return om.writeValueAsString(new MoveAppliedMsg(generateFen(), false));
  }
  
  /** Undo ultima mutare */
  public String undoMove() throws Exception {
    if (history.isEmpty()) {
      return om.writeValueAsString(new ErrorMsg("no moves to undo"));
    }
    GameState prev = history.pop();
    // Restore board state
    for (int r = 0; r < 8; r++) {
      for (int c = 0; c < 8; c++) {
        board[r][c] = prev.board[r][c];
      }
    }
    whiteTurn = prev.whiteTurn;
    return om.writeValueAsString(new MoveAppliedMsg(generateFen(), false));
  }

  private void resetBoard() {
    // Clear board
    for (int r = 0; r < 8; r++) {
      for (int c = 0; c < 8; c++) {
        board[r][c] = null;
      }
    }
    // Setup initial position
    // Row 0 = rank 8 (black pieces)
    board[0][0] = "bR"; board[0][1] = "bN"; board[0][2] = "bB"; board[0][3] = "bQ";
    board[0][4] = "bK"; board[0][5] = "bB"; board[0][6] = "bN"; board[0][7] = "bR";
    for (int c = 0; c < 8; c++) board[1][c] = "bP";
    
    // Row 7 = rank 1 (white pieces)
    board[7][0] = "wR"; board[7][1] = "wN"; board[7][2] = "wB"; board[7][3] = "wQ";
    board[7][4] = "wK"; board[7][5] = "wB"; board[7][6] = "wN"; board[7][7] = "wR";
    for (int c = 0; c < 8; c++) board[6][c] = "wP";
    
    whiteTurn = true;
  }

  /** aplică mutarea și întoarce JSON pentru client */
  public String applyMove(String from, String to, String promotion) throws Exception {
    int fromCol = from.charAt(0) - 'a';
    int fromRow = 8 - Character.getNumericValue(from.charAt(1));
    int toCol = to.charAt(0) - 'a';
    int toRow = 8 - Character.getNumericValue(to.charAt(1));

    // Validate bounds
    if (fromRow < 0 || fromRow > 7 || fromCol < 0 || fromCol > 7 ||
        toRow < 0 || toRow > 7 || toCol < 0 || toCol > 7) {
      return om.writeValueAsString(new ErrorMsg("invalid square"));
    }

    String piece = board[fromRow][fromCol];
    if (piece == null) {
      return om.writeValueAsString(new ErrorMsg("no piece at " + from));
    }

    // Check if it's the correct player's turn
    boolean isWhitePiece = piece.startsWith("w");
    if (isWhitePiece != whiteTurn) {
      return om.writeValueAsString(new ErrorMsg("not your turn"));
    }

    // Check what's at destination
    String captured = board[toRow][toCol];
    
    // Can't capture own piece
    if (captured != null && captured.startsWith(piece.substring(0, 1))) {
      return om.writeValueAsString(new ErrorMsg("cannot capture own piece"));
    }

    // Save state before making the move (for undo)
    history.push(new GameState(board, whiteTurn));

    // Make the move
    board[toRow][toCol] = piece;
    board[fromRow][fromCol] = null;
    
    // Check if king was captured
    if (captured != null && captured.endsWith("K")) {
      String winner = isWhitePiece ? "WHITE" : "BLACK";
      String result = isWhitePiece ? "1-0" : "0-1";
      return om.writeValueAsString(new GameOverMsg("CHECKMATE", result, winner, generateFen()));
    }

    // Switch turn
    whiteTurn = !whiteTurn;

    return om.writeValueAsString(new MoveAppliedMsg(generateFen(), false));
  }

  private String generateFen() {
    StringBuilder fen = new StringBuilder();
    for (int r = 0; r < 8; r++) {
      int empty = 0;
      for (int c = 0; c < 8; c++) {
        String piece = board[r][c];
        if (piece == null) {
          empty++;
        } else {
          if (empty > 0) {
            fen.append(empty);
            empty = 0;
          }
          fen.append(pieceToFenChar(piece));
        }
      }
      if (empty > 0) fen.append(empty);
      if (r < 7) fen.append("/");
    }
    fen.append(" ").append(whiteTurn ? "w" : "b").append(" - - 0 1");
    return fen.toString();
  }

  private char pieceToFenChar(String piece) {
    boolean isWhite = piece.startsWith("w");
    char type = piece.charAt(1);
    return isWhite ? Character.toUpperCase(type) : Character.toLowerCase(type);
  }

  /* ==== DTO-uri simple pentru răspuns ==== */
  public static class MoveAppliedMsg {
    public final String type = "MOVE_APPLIED";
    public final String fen;
    public final boolean check;
    public MoveAppliedMsg(String fen, boolean check) { this.fen = fen; this.check = check; }
  }

  public static class GameOverMsg {
    public final String type = "GAME_OVER";
    public final String reason;
    public final String result;
    public final String winner;
    public final String fen;
    public GameOverMsg(String reason, String result, String winner, String fen) {
      this.reason = reason; this.result = result; this.winner = winner; this.fen = fen;
    }
  }

  public static class ErrorMsg {
    public final String type = "ERROR";
    public final String message;
    public ErrorMsg(String message) { this.message = message; }
  }
}
