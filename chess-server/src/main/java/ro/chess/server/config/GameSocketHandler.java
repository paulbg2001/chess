package ro.chess.server.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import ro.chess.server.service.GameService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handler pentru conexiunile WebSocket.
 * Gestioneaza 2 jucatori: WHITE si BLACK.
 * Primul care se conecteaza primeste ALB, al doilea NEGRU.
 */
@Component
public class GameSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper om = new ObjectMapper();
    private final GameService gameService;

    public GameSocketHandler(GameService gameService) {
        this.gameService = gameService;
    }

    // Map: sesiune -> culoare ("WHITE" sau "BLACK")
    private final Map<WebSocketSession, String> players = new ConcurrentHashMap<>();

    // Referinte catre sesiunile celor 2 jucatori
    private WebSocketSession whitePlayer = null;
    private WebSocketSession blackPlayer = null;

    /**
     * Apelat cand un client se conecteaza.
     * Asigneaza culoarea (WHITE pentru primul, BLACK pentru al doilea).
     * Daca sunt deja 2 jucatori, refuza conexiunea.
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession s) throws Exception {
        String sessionId = s.getId();
        String color;

        // Sincronizam pentru a evita race conditions la asignarea culorilor
        synchronized (this) {
            if (whitePlayer == null) {
                // Primul jucator -> ALB
                whitePlayer = s;
                color = "WHITE";
            } else if (blackPlayer == null) {
                // Al doilea jucator -> NEGRU
                blackPlayer = s;
                color = "BLACK";
            } else {
                // Jocul e plin, refuzam conexiunea
                s.sendMessage(new TextMessage(
                        "{\"type\":\"ERROR\",\"message\":\"Game is full. 2 players already connected.\"}"));
                s.close();
                return;
            }
            players.put(s, color);
        }

        // Trimitem mesaj de bun venit cu: culoarea, pozitia curenta, daca e randul lui
        String fen = gameService.getCurrentFen();
        boolean yourTurn = gameService.isWhiteTurn() == color.equals("WHITE");

        String welcomeMsg = om.writeValueAsString(Map.of(
                "type", "WELCOME",
                "color", color,
                "fen", fen,
                "yourTurn", yourTurn));
        s.sendMessage(new TextMessage(welcomeMsg));

        // Notificam toti jucatorii despre numarul de conexiuni
        broadcastPlayerCount();
    }

    /**
     * Apelat cand un client se deconecteaza.
     * Elibereaza slotul de jucator si notifica celalalt jucator.
     */
    @Override
    public void afterConnectionClosed(WebSocketSession s, CloseStatus status) throws Exception {
        synchronized (this) {
            String color = players.remove(s);

            // Eliberam slotul corespunzator
            if (s == whitePlayer) {
                whitePlayer = null;
            } else if (s == blackPlayer) {
                blackPlayer = null;
            }

            // Notificam jucatorul ramas (daca exista)
            if (color != null) {
                broadcastPlayerCount();
            }
        }
    }

    /**
     * Apelat cand primim un mesaj de la client.
     * Proceseaza comenzile: MAKE_MOVE, RESET_GAME, UNDO_MOVE.
     */
    @Override
    protected void handleTextMessage(WebSocketSession s, TextMessage message) throws Exception {
        String payload = message.getPayload();
        JsonNode root = om.readTree(payload);
        String type = root.path("type").asText("");
        String playerColor = players.get(s);

        switch (type) {
            case "MAKE_MOVE": {
                String from = root.path("from").asText();
                String to = root.path("to").asText();

                // Verificam daca e randul acestui jucator
                boolean isWhite = "WHITE".equals(playerColor);
                if (gameService.isWhiteTurn() != isWhite) {
                    s.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"message\":\"Nu este randul tau!\"}"));
                    return;
                }

                // Aplicam mutarea si trimitem rezultatul la ambii jucatori
                String response = gameService.applyMove(from, to);
                broadcast(response);
                break;
            }

            case "RESET_GAME": {
                // Cineva a apasat Reset
                String response = gameService.resetGame();
                broadcast(response);
                break;
            }

            case "UNDO_MOVE": {
                // Cineva a apasat Undo
                String response = gameService.undoMove();
                broadcast(response);
                break;
            }

            default:
                s.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"message\":\"Comanda necunoscuta\"}"));
                break;
        }

    }

    /**
     * Trimite un mesaj catre ambii jucatori conectati.
     */
    private void broadcast(String message) throws Exception {
        TextMessage msg = new TextMessage(message);

        if (whitePlayer != null && whitePlayer.isOpen()) {
            whitePlayer.sendMessage(msg);
        }
        if (blackPlayer != null && blackPlayer.isOpen()) {
            blackPlayer.sendMessage(msg);
        }
    }

    /**
     * Notifica toti jucatorii despre numarul de conexiuni active.
     * Util pentru UI (ex: "Jucatori: 1/2").
     */
    private void broadcastPlayerCount() throws Exception {
        int count = (whitePlayer != null ? 1 : 0) + (blackPlayer != null ? 1 : 0);

        String msg = om.writeValueAsString(Map.of(
                "type", "PLAYERS_UPDATE",
                "count", count,
                "whiteConnected", whitePlayer != null,
                "blackConnected", blackPlayer != null));
        broadcast(msg);
    }
}
