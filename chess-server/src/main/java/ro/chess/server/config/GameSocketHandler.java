package ro.chess.server.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import ro.chess.server.service.GameService;


@Component
@RequiredArgsConstructor
public class GameSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper om = new ObjectMapper();
    private final GameService game;

    @Override
    public void afterConnectionEstablished(WebSocketSession s) throws Exception {
        s.sendMessage(new TextMessage("{\"type\":\"HELLO\",\"msg\":\"connected\"}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession s, TextMessage message) throws Exception {
        JsonNode root = om.readTree(message.getPayload());
        String type = root.path("type").asText("");
        switch (type) {
            case "MAKE_MOVE" -> {
                String from = root.path("from").asText();
                String to = root.path("to").asText();
                String response = game.applyMove(from, to, null);
                s.sendMessage(new TextMessage(response));
            }
            case "RESET_GAME" -> {
                String response = game.resetGame();
                s.sendMessage(new TextMessage(response));
            }
            case "UNDO_MOVE" -> {
                String response = game.undoMove();
                s.sendMessage(new TextMessage(response));
            }

            default -> s.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"message\":\"Unknown type\"}"));
        }
    }
}