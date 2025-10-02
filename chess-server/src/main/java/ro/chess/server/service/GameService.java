package ro.chess.server.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import org.springframework.stereotype.Service;

@Service
public class GameService {

  // DEMO: o singură partidă globală (ulterior vei avea un Board per gameId)
  private final Board board = new Board();
  private final ObjectMapper om = new ObjectMapper();

  /** aplică mutarea, validează și întoarce JSON pentru client */
  public String applyMove(String from, String to, String promotion) throws Exception {
    Move move = new Move(
            Square.fromValue(from.toUpperCase()),
            Square.fromValue(to.toUpperCase())
    );

    // 1) mutare legală?
    if (!board.legalMoves().contains(move)) {
      return om.writeValueAsString(new ErrorMsg("illegal move"));
    }

    // 2) aplică mutarea
    board.doMove(move);

    // 3) după mutare e rândul adversarului
    Side sideToMove = board.getSideToMove();

    // 4) calculăm mutările legale pentru adversar
    var legal = board.legalMoves();

    // 5) șah/mat sau pat
    if (legal.isEmpty()) {
      boolean inCheck = board.isKingAttacked(); // verificăm regele părții la mutare
      if (inCheck) {
        // mat: pierde sideToMove, câștigă cealaltă parte
        String result = (sideToMove == Side.WHITE) ? "0-1" : "1-0";
        return om.writeValueAsString(new GameOverMsg("CHECKMATE", result, board.getFen()));
      } else {
        // pat (stalemate)
        return om.writeValueAsString(new GameOverMsg("STALEMATE", "1/2-1/2", board.getFen()));
      }
    }

    // 6) poziție normală: trimitem FEN + dacă e șah asupra părții la mutare
    boolean check = board.isKingAttacked();
    return om.writeValueAsString(new MoveAppliedMsg(board.getFen(), check));
  }

  /* ==== DTO-uri simple pentru răspuns ==== */
  public static class MoveAppliedMsg {
    public final String type = "MOVE_APPLIED";
    public final String fen;
    public final boolean check; // e șah pentru side-to-move
    public MoveAppliedMsg(String fen, boolean check) { this.fen = fen; this.check = check; }
  }

  public static class GameOverMsg {
    public final String type = "GAME_OVER";
    public final String reason;  // CHECKMATE | STALEMATE
    public final String result;  // "1-0" | "0-1" | "1/2-1/2"
    public final String fen;
    public GameOverMsg(String reason, String result, String fen) {
      this.reason = reason; this.result = result; this.fen = fen;
    }
  }

  public static class ErrorMsg {
    public final String type = "ERROR";
    public final String message;
    public ErrorMsg(String message) { this.message = message; }
  }
}