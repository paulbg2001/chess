package ro.chess.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Serviciu care gestioneaza logica jocului de sah.
 * 
 * NOTA: Aceasta versiune simplificata NU valideaza mutarile legale de sah!
 * Orice piesa poate fi mutata oriunde (cu exceptia capturarii propriilor piese).
 * Jocul se termina cand un rege este capturat.
 */
@Slf4j
@Service
public class GameService {

  // Tabla de joc: 8x8, null = patrat gol, altfel piesa (ex: "wK", "bQ", "wP")
  // Formatul piesei: prima litera = culoare (w/b), a doua = tip (K/Q/R/B/N/P)
  private String[][] board = new String[8][8];
  
  // true = randul albului, false = randul negrului
  private boolean whiteTurn = true;
  
  private final ObjectMapper om = new ObjectMapper();
  
  // Istoric pentru undo - stocheaza snapshot-uri ale starii jocului
  private final Deque<GameState> history = new ArrayDeque<>();
  
  /**
   * Clasa interna care retine starea jocului la un moment dat.
   * Folosita pentru functionalitatea de undo.
   */
  private static class GameState {
    final String[][] board;      // Copia tablei
    final boolean whiteTurn;     // Al cui rand era
    
    GameState(String[][] b, boolean wt) {
      // Facem o copie profunda a tablei
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
    log.info("[GAME] Serviciul de joc initializat");
  }

  /**
   * Returneaza FEN-ul curent (reprezentarea pozitiei).
   */
  public String getCurrentFen() {
    return generateFen();
  }
  
  /**
   * Returneaza true daca e randul albului.
   */
  public boolean isWhiteTurn() {
    return whiteTurn;
  }

  /**
   * Reseteaza tabla la pozitia initiala si sterge istoricul.
   */
  public String resetGame() throws Exception {
    resetBoard();
    history.clear();
    log.info("[GAME] Joc resetat la pozitia initiala");
    return om.writeValueAsString(new MoveAppliedMsg(generateFen(), false));
  }
  
  /**
   * Anuleaza ultima mutare (undo).
   * Restaureaza starea anterioara din istoric.
   */
  public String undoMove() throws Exception {
    if (history.isEmpty()) {
      log.warn("[UNDO] Nu exista mutari de anulat");
      return om.writeValueAsString(new ErrorMsg("no moves to undo"));
    }
    
    // Scoatem ultima stare din istoric
    GameState prev = history.pop();
    
    // Restauram tabla si randul
    for (int r = 0; r < 8; r++) {
      for (int c = 0; c < 8; c++) {
        board[r][c] = prev.board[r][c];
      }
    }
    whiteTurn = prev.whiteTurn;
    
    log.info("[UNDO] Mutare anulata. Mutari ramase in istoric: {}", history.size());
    return om.writeValueAsString(new MoveAppliedMsg(generateFen(), false));
  }

  /**
   * Reseteaza tabla la pozitia standard de inceput.
   * Rand 0 = rang 8 (piesele negre)
   * Rand 7 = rang 1 (piesele albe)
   */
  private void resetBoard() {
    // Golim tabla
    for (int r = 0; r < 8; r++) {
      for (int c = 0; c < 8; c++) {
        board[r][c] = null;
      }
    }
    
    // Piesele negre (randul 0 = rang 8)
    // Ordinea: Tura, Cal, Nebun, Regina, Rege, Nebun, Cal, Tura
    board[0][0] = "bR"; board[0][1] = "bN"; board[0][2] = "bB"; board[0][3] = "bQ";
    board[0][4] = "bK"; board[0][5] = "bB"; board[0][6] = "bN"; board[0][7] = "bR";
    
    // Pionii negri (randul 1 = rang 7)
    for (int c = 0; c < 8; c++) {
      board[1][c] = "bP";
    }
    
    // Piesele albe (randul 7 = rang 1)
    board[7][0] = "wR"; board[7][1] = "wN"; board[7][2] = "wB"; board[7][3] = "wQ";
    board[7][4] = "wK"; board[7][5] = "wB"; board[7][6] = "wN"; board[7][7] = "wR";
    
    // Pionii albi (randul 6 = rang 2)
    for (int c = 0; c < 8; c++) {
      board[6][c] = "wP";
    }
    
    // Albul incepe
    whiteTurn = true;
  }

  /**
   * Aplica o mutare si returneaza JSON pentru client.
   * 
   * @param from Patratul sursa (ex: "e2")
   * @param to Patratul destinatie (ex: "e4")
   * @param promotion Nu e folosit in aceasta versiune simplificata
   */
  public String applyMove(String from, String to, String promotion) throws Exception {
    // Convertim notatia algebrica in indici de array
    // Coloana: 'a'=0, 'b'=1, ..., 'h'=7
    // Randul: '8'=0, '7'=1, ..., '1'=7 (inversat pentru array)
    int fromCol = from.charAt(0) - 'a';
    int fromRow = 8 - Character.getNumericValue(from.charAt(1));
    int toCol = to.charAt(0) - 'a';
    int toRow = 8 - Character.getNumericValue(to.charAt(1));

    // Validam ca indicii sunt in limite
    if (fromRow < 0 || fromRow > 7 || fromCol < 0 || fromCol > 7 ||
        toRow < 0 || toRow > 7 || toCol < 0 || toCol > 7) {
      log.warn("[MOVE] Patrat invalid: {} -> {}", from, to);
      return om.writeValueAsString(new ErrorMsg("invalid square"));
    }

    // Luam piesa de pe patratul sursa
    String piece = board[fromRow][fromCol];
    if (piece == null) {
      log.warn("[MOVE] Nu exista piesa pe {}", from);
      return om.writeValueAsString(new ErrorMsg("no piece at " + from));
    }

    // Verificam daca e randul corect
    boolean isWhitePiece = piece.startsWith("w");
    if (isWhitePiece != whiteTurn) {
      log.warn("[MOVE] Nu e randul jucatorului. Piesa: {}, Rand alb: {}", piece, whiteTurn);
      return om.writeValueAsString(new ErrorMsg("not your turn"));
    }

    // Verificam ce e pe patratul destinatie
    String captured = board[toRow][toCol];
    
    // Nu poti captura propria piesa
    if (captured != null && captured.startsWith(piece.substring(0, 1))) {
      log.warn("[MOVE] Incercare de capturare a propriei piese: {} pe {}", captured, to);
      return om.writeValueAsString(new ErrorMsg("cannot capture own piece"));
    }

    // Salvam starea inainte de mutare (pentru undo)
    history.push(new GameState(board, whiteTurn));

    // Executam mutarea
    board[toRow][toCol] = piece;
    board[fromRow][fromCol] = null;
    
    log.info("[MOVE] {} muta {} de pe {} pe {}", 
            isWhitePiece ? "WHITE" : "BLACK", piece, from, to);
    
    if (captured != null) {
      log.info("[CAPTURE] Piesa capturata: {}", captured);
    }
    
    // Verificam daca regele a fost capturat -> joc terminat
    if (captured != null && captured.endsWith("K")) {
      String winner = isWhitePiece ? "WHITE" : "BLACK";
      String result = isWhitePiece ? "1-0" : "0-1";
      log.info("[GAME OVER] {} a castigat prin capturarea regelui!", winner);
      return om.writeValueAsString(new GameOverMsg("CHECKMATE", result, winner, generateFen()));
    }

    // Schimbam randul
    whiteTurn = !whiteTurn;
    log.debug("[TURN] Acum e randul: {}", whiteTurn ? "WHITE" : "BLACK");

    return om.writeValueAsString(new MoveAppliedMsg(generateFen(), false));
  }

  /**
   * Genereaza FEN-ul pozitiei curente.
   * FEN = Forsyth-Edwards Notation - format standard pentru pozitii de sah.
   * 
   * Exemplu: "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1"
   */
  private String generateFen() {
    StringBuilder fen = new StringBuilder();
    
    // Parcurgem fiecare rand (de sus in jos = rang 8 la rang 1)
    for (int r = 0; r < 8; r++) {
      int empty = 0; // Contor pentru patrate goale consecutive
      
      for (int c = 0; c < 8; c++) {
        String piece = board[r][c];
        
        if (piece == null) {
          // Patrat gol - incrementam contorul
          empty++;
        } else {
          // Piesa gasita
          if (empty > 0) {
            // Scriem numarul de patrate goale anterioare
            fen.append(empty);
            empty = 0;
          }
          // Scriem piesa (majuscula = alb, minuscula = negru)
          fen.append(pieceToFenChar(piece));
        }
      }
      
      // Daca randul se termina cu patrate goale
      if (empty > 0) {
        fen.append(empty);
      }
      
      // Separator intre randuri (nu dupa ultimul)
      if (r < 7) {
        fen.append("/");
      }
    }
    
    // Adaugam informatii suplimentare: rand la mutare, rocade, en passant, etc
    fen.append(" ").append(whiteTurn ? "w" : "b").append(" - - 0 1");
    
    return fen.toString();
  }

  /**
   * Converteste formatul intern al piesei in caracter FEN.
   * Alb = majuscula, Negru = minuscula
   */
  private char pieceToFenChar(String piece) {
    boolean isWhite = piece.startsWith("w");
    char type = piece.charAt(1); // K, Q, R, B, N, P
    return isWhite ? Character.toUpperCase(type) : Character.toLowerCase(type);
  }

  /* ============ DTO-uri pentru raspunsuri JSON ============ */
  
  /**
   * Raspuns trimis dupa o mutare reusita.
   */
  public static class MoveAppliedMsg {
    public final String type = "MOVE_APPLIED";
    public final String fen;      // Pozitia actuala
    public final boolean check;   // Daca e sah (nefolosit in versiunea simplificata)
    
    public MoveAppliedMsg(String fen, boolean check) { 
      this.fen = fen; 
      this.check = check; 
    }
  }

  /**
   * Raspuns trimis cand jocul s-a terminat.
   */
  public static class GameOverMsg {
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

  /**
   * Raspuns trimis in caz de eroare.
   */
  public static class ErrorMsg {
    public final String type = "ERROR";
    public final String message;  // Mesajul de eroare
    
    public ErrorMsg(String message) { 
      this.message = message; 
    }
  }
}
