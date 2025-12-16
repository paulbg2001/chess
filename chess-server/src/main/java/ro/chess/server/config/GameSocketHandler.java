package ro.chess.server.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
//@RequiredArgsConstructor
public class GameSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper om = new ObjectMapper();
    private final GameService game;

    public GameSocketHandler(GameService game) {
        this.game = game;
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
//                log.info("[CONNECT] Jucator WHITE conectat. Session: {}", sessionId);
            } else if (blackPlayer == null) {
                // Al doilea jucator -> NEGRU
                blackPlayer = s;
                color = "BLACK";
//                log.info("[CONNECT] Jucator BLACK conectat. Session: {}", sessionId);
            } else {
                // Jocul e plin, refuzam conexiunea
//                log.warn("[REJECT] Conexiune refuzata - joc plin. Session: {}", sessionId);
                s.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"message\":\"Game is full. 2 players already connected.\"}"));
                s.close();
                return;
            }
            players.put(s, color);
        }

        // Trimitem mesaj de bun venit cu: culoarea, pozitia curenta, daca e randul lui
        String fen = game.getCurrentFen();
        boolean yourTurn = game.isWhiteTurn() == color.equals("WHITE");

        String welcomeMsg = om.writeValueAsString(Map.of(
                "type", "WELCOME",
                "color", color,
                "fen", fen,
                "yourTurn", yourTurn
        ));
        s.sendMessage(new TextMessage(welcomeMsg));
//        log.debug("[SEND] Welcome -> {}: {}", color, welcomeMsg);

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
//                log.info("[DISCONNECT] Jucator WHITE deconectat. Status: {}", status);
            } else if (s == blackPlayer) {
                blackPlayer = null;
//                log.info("[DISCONNECT] Jucator BLACK deconectat. Status: {}", status);
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

//        log.debug("[RECV] {} -> {}", playerColor, payload);

        switch (type) {
            case "MAKE_MOVE" -> {
                String from = root.path("from").asText();
                String to = root.path("to").asText();

                // Verificam daca e randul acestui jucator
                boolean isWhite = "WHITE".equals(playerColor);
                if (game.isWhiteTurn() != isWhite) {
//                    log.warn("[MOVE] {} a incercat sa mute dar nu e randul lui!", playerColor);
                    s.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"message\":\"Nu este randul tau!\"}"));
                    return;
                }

//                log.info("[MOVE] {} muta: {} -> {}", playerColor, from, to);

                // Aplicam mutarea si trimitem rezultatul la ambii jucatori
                String response = game.applyMove(from, to);
                broadcast(response);
            }

            case "RESET_GAME" -> {
//                log.info("[RESET] {} a cerut reset", playerColor);
                String response = game.resetGame();
                broadcast(response);
            }

            case "UNDO_MOVE" -> {
//                log.info("[UNDO] {} a cerut undo", playerColor);
                String response = game.undoMove();
                broadcast(response);
            }

            default -> {
//                log.warn("[UNKNOWN] Tip necunoscut: {} de la {}", type, playerColor);
                s.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"message\":\"Unknown type\"}"));
            }
        }
    }

    /**
     * Trimite un mesaj catre ambii jucatori conectati.
     */
    private void broadcast(String message) throws Exception {
        TextMessage msg = new TextMessage(message);

        if (whitePlayer != null && whitePlayer.isOpen()) {
            whitePlayer.sendMessage(msg);
//            log.debug("[BROADCAST] -> WHITE: {}", message);
        }
        if (blackPlayer != null && blackPlayer.isOpen()) {
            blackPlayer.sendMessage(msg);
//            log.debug("[BROADCAST] -> BLACK: {}", message);
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
                "blackConnected", blackPlayer != null
        ));

//        log.info("[PLAYERS] Conectati: {}/2 (WHITE={}, BLACK={})",
//                count, whitePlayer != null, blackPlayer != null);

        broadcast(msg);
    }
}
