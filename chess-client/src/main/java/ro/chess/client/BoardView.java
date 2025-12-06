package ro.chess.client;

import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import ro.chess.client.util.BoardUtils;

import java.util.function.BiConsumer;

public class BoardView extends GridPane {
  private final StackPane[][] cells = new StackPane[8][8];
  // board[row][col] = piece like "wK", "bQ", null for empty
  private String[][] board = new String[8][8];
  private BiConsumer<String,String> onMoveAttempt;

  private String selected;
  private double cellSize = 80;

  public BoardView() {
    setHgap(0);
    setVgap(0);
    buildGrid();
    // Set initial position
    setPosition("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1");
  }

  public void setCellSize(double size) { this.cellSize = size; rebuildSizes(); redraw(); }
  public void setOnMoveAttempt(BiConsumer<String,String> handler) { this.onMoveAttempt = handler; }

  public void setPosition(String fen) {
    parseFen(fen);
    redraw();
  }

  private void parseFen(String fen) {
    // Clear board
    for (int r = 0; r < 8; r++) {
      for (int c = 0; c < 8; c++) {
        board[r][c] = null;
      }
    }

    String[] parts = fen.split(" ");
    String[] ranks = parts[0].split("/");

    for (int r = 0; r < 8 && r < ranks.length; r++) {
      int col = 0;
      for (char ch : ranks[r].toCharArray()) {
        if (Character.isDigit(ch)) {
          col += Character.getNumericValue(ch);
        } else {
          board[r][col] = fenCharToPiece(ch);
          col++;
        }
      }
    }
  }

  private String fenCharToPiece(char ch) {
    boolean isWhite = Character.isUpperCase(ch);
    String side = isWhite ? "w" : "b";
    char type = Character.toUpperCase(ch);
    return side + type;
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
          handleClick(sq, rr, cc);
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
    // Remove all piece images
    for (var row : cells) for (var cell : row)
      cell.getChildren().removeIf(n -> n instanceof ImageView);

    // Add piece images based on board state
    for (int r = 0; r < 8; r++) {
      for (int c = 0; c < 8; c++) {
        String piece = board[r][c];
        if (piece == null) continue;

        ImageView iv = new ImageView(Assets.piece(piece));
        iv.setPreserveRatio(true);
        iv.setFitWidth(cellSize * 0.8);
        iv.setFitHeight(cellSize * 0.8);
        iv.setMouseTransparent(true);

        cells[r][c].getChildren().add(iv);
      }
    }
    clearSelection();
  }

  private void handleClick(String sq, int row, int col) {
    String piece = board[row][col];
    if (selected == null) {
      if (piece != null) {
        selected = sq;
        highlight(sq, true);
        setCursor(Cursor.HAND);
      }
      return;
    }
    if (onMoveAttempt != null) onMoveAttempt.accept(selected, sq);
    clearSelection();
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
}
