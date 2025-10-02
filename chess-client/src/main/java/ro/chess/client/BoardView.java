package ro.chess.client;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import ro.chess.client.util.BoardUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class BoardView extends GridPane {
  private final StackPane[][] cells = new StackPane[8][8];
  private final Map<Square, ImageView> pieceViews = new HashMap<>();
  private final Board board = new Board();
  private BiConsumer<String,String> onMoveAttempt;

  private String selected;
  private double cellSize = 80; // schimbă cu setCellSize()

  public BoardView() {
    setHgap(0);
    setVgap(0);
    buildGrid();
    setPosition(board.getFen());
  }

  public void setCellSize(double size) { this.cellSize = size; rebuildSizes(); redraw(); }
  public void setOnMoveAttempt(BiConsumer<String,String> handler) { this.onMoveAttempt = handler; }

  public void setPosition(String fen) {
    board.loadFromFen(fen);
    redraw();
  }

  private void buildGrid() {
    for (int r=0; r<8; r++) {
      for (int c=0; c<8; c++) {
        StackPane cell = new StackPane();
        cell.setPrefSize(cellSize, cellSize);
        var light = (r + c) % 2 == 0;
        var rect = new Rectangle(cellSize, cellSize);
        rect.setFill(light ? Color.web("#EEEED2") : Color.web("#769656"));
        cell.getChildren().add(rect);
        cell.setAlignment(Pos.CENTER);

        final int rr = r, cc = c;
        cell.setOnMouseClicked(e -> {
          if (e.getButton() != MouseButton.PRIMARY) return;
          var sq = BoardUtils.toSquare(rr, cc);
          handleClick(sq);
        });

        cells[r][c] = cell;
        add(cell, c, r);
      }
    }
  }

  private void rebuildSizes() {
    for (int r=0; r<8; r++) for (int c=0; c<8; c++) {
      StackPane cell = cells[r][c];
      cell.setPrefSize(cellSize, cellSize);
      Rectangle rect = (Rectangle) cell.getChildren().get(0);
      rect.setWidth(cellSize); rect.setHeight(cellSize);
    }
  }

  private void redraw() {
    pieceViews.clear();
    for (var row : cells) for (var cell : row)
      cell.getChildren().removeIf(n -> n instanceof ImageView);

    for (Square sq : Square.values()) {
      if (sq == Square.NONE) continue;
      Piece piece = board.getPiece(sq);
      if (piece == Piece.NONE) continue;

      ImageView iv = new ImageView(Assets.piece(pieceKey(piece)));
      iv.setPreserveRatio(true);
      iv.setFitWidth(cellSize * 0.8);
      iv.setFitHeight(cellSize * 0.8);
      iv.setMouseTransparent(true);

      int col = BoardUtils.fileIndex(sq.getFile().getNotation().charAt(0));
      int row = BoardUtils.rankIndex(sq.getRank().getNotation().charAt(0));
      if (row < 0 || row > 7 || col < 0 || col > 7) continue;

      cells[row][col].getChildren().add(iv);
      pieceViews.put(sq, iv);
    }
    clearSelection();
  }

  private void handleClick(String sq) {
    Square s = Square.fromValue(sq.toUpperCase());
    Piece p = board.getPiece(s);
    if (selected == null) {
      if (p != Piece.NONE) {
        selected = sq;
        highlight(sq, true);
        setCursor(Cursor.HAND);
      }
      return;
    }
    if (onMoveAttempt != null) onMoveAttempt.accept(selected, sq);
    clearSelection();
  }

  public void applyMoveLocal(String from, String to, String promo) {
    var move = new com.github.bhlangonijr.chesslib.move.Move(
            Square.fromValue(from.toUpperCase()), Square.fromValue(to.toUpperCase()));
    board.doMove(move);
    redraw();
  }

  private void highlight(String sq, boolean on) {
    int c = BoardUtils.fileIndex(sq.charAt(0));
    int r = BoardUtils.rankIndex(sq.charAt(1));
    var rect = (Rectangle) cells[r][c].getChildren().get(0);
    rect.setStroke(on ? Color.GOLD : null);
    rect.setStrokeWidth(on ? 3 : 0);
  }

  private void clearSelection() {
    if (selected != null) highlight(selected, false);
    selected = null;
    setCursor(Cursor.DEFAULT);
  }

  private String pieceKey(Piece piece) {
    String side = (piece.getPieceSide() == Side.WHITE) ? "w" : "b";
    String type = switch (piece.getPieceType()) {
      case KING   -> "K";
      case QUEEN  -> "Q";
      case ROOK   -> "R";
      case BISHOP -> "B";
      case KNIGHT -> "N";
      case PAWN   -> "P";
      default     -> "";
    };
    return side + type;
  }
}