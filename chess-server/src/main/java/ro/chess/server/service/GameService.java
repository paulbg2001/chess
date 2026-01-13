package ro.chess.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import ro.chess.server.dto.ErrorMsg;
import ro.chess.server.dto.GameOverMsg;
import ro.chess.server.dto.MoveAppliedMsg;
import ro.chess.server.model.GameState;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Serviciul principal care tine minte unde sunt piesele.
 * Aici se intampla toata "magia" jocului.
 */
@Service
public class GameService {

    // Asta e tabla noastra de sah. E o matrice de 8 pe 8.
    // Daca un patrat e null, inseamna ca e gol.
    // Daca are text (ex: "wK", "bQ"), inseamna ca e o piesa acolo.
    // w = white (alb), b = black (negru)
    // K=King (Rege), Q=Queen (Regina), R=Rook (Tura), B=Bishop (Nebun), N=Knight
    // (Cal), P=Pawn (Pion)
    private final String[][] board = new String[8][8];

    // Tine minte al cui e randul.
    // true = randul albului, false = randul negrului
    private boolean whiteTurn = true;

    // Folosit pentru a trimite mesaje JSON (inteleger serverul cu clientul)
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Aici tinem minte mutarile ca sa putem da "Undo" (inapoi)
    private final Deque<GameState> history = new ArrayDeque<>();

    public GameService() {
        // Cand porneste serverul, aranjam piesele
        resetBoard();
    }

    /**
     * Returneaza pozitia curenta sub forma de text (FEN).
     * FEN e un standard ca sa descrii o tabla de sah prin text.
     */
    public String getCurrentFen() {
        return generateFen();
    }

    /**
     * Ne zice daca e randul albului.
     */
    public boolean isWhiteTurn() {
        return whiteTurn;
    }

    /**
     * Reseteaza tot jocul de la zero.
     */
    public String resetGame() throws Exception {
        resetBoard();
        history.clear(); // Stergem istoricul
        // Trimitem noua stare la jucatori
        return objectMapper.writeValueAsString(new MoveAppliedMsg(generateFen(), false));
    }

    /**
     * Da o mutare inapoi (Undo).
     */
    public String undoMove() throws Exception {
        if (history.isEmpty()) {
            return objectMapper.writeValueAsString(new ErrorMsg("Nu am ce sa anulez!"));
        }

        // Luam ultima stare salvata
        GameState prev = history.pop();

        // Punem piesele inapoi cum erau
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                board[r][c] = prev.getBoard()[r][c];
            }
        }
        whiteTurn = prev.isWhiteTurn();

        return objectMapper.writeValueAsString(new MoveAppliedMsg(generateFen(), false));
    }

    /**
     * Functia care pune piesele la locurile lor de start.
     */
    private void resetBoard() {
        // Mai intai stergem tot de pe tabla
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                board[r][c] = null;
            }
        }

        // Punem piesele negre sus (randul 0 al matricei)
        // Ordine: Tura, Cal, Nebun, Regina, Rege, Nebun, Cal, Tura
        board[0][0] = "bR";
        board[0][1] = "bN";
        board[0][2] = "bB";
        board[0][3] = "bQ";
        board[0][4] = "bK";
        board[0][5] = "bB";
        board[0][6] = "bN";
        board[0][7] = "bR";

        // Punem pionii negri (randul 1 al matricei)
        for (int c = 0; c < 8; c++) {
            board[1][c] = "bP";
        }

        // Punem piesele albe jos (randul 7 al matricei)
        board[7][0] = "wR";
        board[7][1] = "wN";
        board[7][2] = "wB";
        board[7][3] = "wQ";
        board[7][4] = "wK";
        board[7][5] = "wB";
        board[7][6] = "wN";
        board[7][7] = "wR";

        // Punem pionii albi (randul 6 al matricei)
        for (int c = 0; c < 8; c++) {
            board[6][c] = "wP";
        }

        // Albul incepe mereu
        whiteTurn = true;
    }

    /**
     * Aici se face mutarea propriu-zisa.
     * Primim de unde pleaca piesa (from) si unde ajunge (to).
     * Ex: "e2" -> "e4"
     */
    public String applyMove(String from, String to) throws Exception {
        // Transformam coordonatele din text (a-h, 1-8) in numere pentru matrice (0-7)
        // 'a' e 0, 'b' e 1 ...
        int fromCol = from.charAt(0) - 'a';
        // '8' e 0, '1' e 7 (matricea e invers fata de tabla standard)
        int fromRow = 8 - Character.getNumericValue(from.charAt(1));

        int toCol = to.charAt(0) - 'a';
        int toRow = 8 - Character.getNumericValue(to.charAt(1));

        // Verificam sa nu iesim de pe tabla (sa nu dea eroare programul)
        if (fromRow < 0 || fromRow > 7 || fromCol < 0 || fromCol > 7 ||
                toRow < 0 || toRow > 7 || toCol < 0 || toCol > 7) {
            return objectMapper.writeValueAsString(new ErrorMsg("Ai apasat in afara tablei!"));
        }

        // Vedem ce piesa vrem sa mutam
        String piece = board[fromRow][fromCol];
        if (piece == null) {
            return objectMapper.writeValueAsString(new ErrorMsg("Nu e nicio piesa acolo!"));
        }

        // Verificam daca e randul corect
        boolean isWhitePiece = piece.startsWith("w");
        if (isWhitePiece != whiteTurn) {
            return objectMapper.writeValueAsString(new ErrorMsg("not your turn"));
        }

        // Vedem daca am capturat ceva (daca era o piesa la destinatie)
        String captured = board[toRow][toCol];

        // Salvam starea inainte de mutare (ca sa mearga butonul Undo)
        history.push(new GameState(board, whiteTurn));

        // MUTAREA PROPRIU-ZISA:
        // 1. Punem piesa la destinatie
        board[toRow][toCol] = piece;
        // 2. Stergem piesa de unde a plecat
        board[fromRow][fromCol] = null;

        // Verificam daca s-a terminat jocul (daca am mancat un Rege)
        if (captured != null && captured.endsWith("K")) {
            String winner = isWhitePiece ? "ALBUL" : "NEGRUL";
            String result = isWhitePiece ? "1-0" : "0-1";
            // Trimitem mesaj ca s-a gata jocul
            return objectMapper
                    .writeValueAsString(new GameOverMsg("SAH MAT (Rege Capturat)", result, winner, generateFen()));
        }

        // Schimbam randul
        whiteTurn = !whiteTurn;

        // Trimitem noua configuratie la toata lumea
        return objectMapper.writeValueAsString(new MoveAppliedMsg(generateFen(), false));
    }

    /**
     * Functia asta transforma matricea noastra intr-un text scurt (FEN).
     * Clientul (interfata grafica) are nevoie de textul asta ca sa deseneze piese.
     */
    private String generateFen() {
        StringBuilder fen = new StringBuilder();

        // Luam fiecare rand la rand
        for (int r = 0; r < 8; r++) {
            int empty = 0; // Numaram cate patrate goale sunt

            for (int c = 0; c < 8; c++) {
                String piece = board[r][c];

                if (piece == null) {
                    empty++; // Inca un loc gol
                } else {
                    if (empty > 0) {
                        fen.append(empty); // Scriem numarul de locuri goale
                        empty = 0;
                    }
                    // Scriem litera piesei
                    fen.append(pieceToFenChar(piece));
                }
            }

            if (empty > 0) {
                fen.append(empty);
            }

            if (r < 7) {
                fen.append("/"); // Separator de randuri
            }
        }

        // Adaugam infomatii extra (cine e la rand etc)
        fen.append(" ").append(whiteTurn ? "w" : "b").append(" - - 0 1");

        return fen.toString();
    }

    private char pieceToFenChar(String piece) {
        boolean isWhite = piece.startsWith("w");
        char type = piece.charAt(1);
        // Piesele albe se scriu cu litere mari, alea negre cu mici
        return isWhite ? Character.toUpperCase(type) : Character.toLowerCase(type);
    }
}
