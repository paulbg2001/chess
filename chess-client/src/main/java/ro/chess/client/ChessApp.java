package ro.chess.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChessApp extends Application {
    private WebSocket ws;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    private final Label statusLbl = new Label("Status: DISCONNECTED");
    private final TextArea wsLog = new TextArea();
    private final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final ObjectMapper om = new ObjectMapper();

    private URI serverUri;
    private BoardView board;

    @Override
    public void start(Stage stage) {
        String url = "ws://localhost:8080/ws";
        this.serverUri = URI.create(url);

        // Log WS sus
        wsLog.setEditable(false);
        wsLog.setWrapText(true);
        wsLog.setPrefRowCount(3);
        wsLog.setStyle("-fx-font-family: Consolas, Menlo, Monospaced; -fx-font-size: 11px;");

        Button undoBtn = new Button("↩ Undo");
        undoBtn.setOnAction(e -> {
            if (connected.get()) {
                sendJson("{\"type\":\"UNDO_MOVE\"}");
            }
        });
        Button clearBtn = new Button("Clear");
        clearBtn.setOnAction(e -> wsLog.clear());
        Button copyBtn = new Button("Copy");
        copyBtn.setOnAction(e -> {
            var cb = javafx.scene.input.Clipboard.getSystemClipboard();
            var cc = new javafx.scene.input.ClipboardContent();
            cc.putString(wsLog.getText());
            cb.setContent(cc);
        });
        HBox topBar = new HBox(10, statusLbl, new Separator(), undoBtn, clearBtn, copyBtn);
        topBar.setPadding(new Insets(4, 8, 4, 8));

        board = new BoardView();
        board.setOnMoveAttempt((from, to) -> {
            if (!connected.get()) {
                log("! not connected, ignoring move " + from + "-" + to);
                return;
            }
            sendJson(om.createObjectNode()
                    .put("type", "MAKE_MOVE")
                    .put("from", from)
                    .put("to", to)
                    .toString());
        });

        BorderPane root = new BorderPane();
        root.setTop(new VBox(topBar, wsLog));
        root.setCenter(board);

        stage.setTitle("Chess Client");
        stage.setScene(new Scene(root, 740, 800));
        stage.show();

        connectWs(url);
    }

    private void connectWs(String url) {
        ws = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create(url), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        connected.set(true);
                        updateStatus("CONNECTED on " + readablePort(serverUri));
                        log("→ [OPEN] " + serverUri);
                        String hello = "{\"type\":\"CLIENT_HELLO\"}";
                        webSocket.sendText(hello, true);
                        log("→ " + hello);
                        WebSocket.Listener.super.onOpen(webSocket);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        var json = data.toString();
                        log("← " + json);
                        try {
                            JsonNode root = om.readTree(json);
                            String type = root.path("type").asText("");
                            switch (type) {
                                case "MOVE_APPLIED" -> {
                                    String fen = root.path("fen").asText();
                                    boolean check = root.path("check").asBoolean(false);
                                    Platform.runLater(() -> {
                                        board.setPosition(fen);
                                        if (check) updateStatus("CHECK");
                                    });
                                }
                                case "GAME_OVER" -> {
                                    String reason = root.path("reason").asText();
                                    String result = root.path("result").asText();
                                    String winner = root.path("winner").asText("");
                                    String fen = root.path("fen").asText();
                                    Platform.runLater(() -> {
                                        board.setPosition(fen);
                                        updateStatus("GAME OVER: " + winner + " a câștigat!");
                                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                                        alert.setTitle("Joc terminat");
                                        alert.setHeaderText(winner + " a câștigat!");
                                        alert.setContentText("Rezultat: " + result);
                                        alert.showAndWait();
                                        // După OK, resetează jocul
                                        sendJson("{\"type\":\"RESET_GAME\"}");
                                    });
                                }
                                case "ERROR" -> updateStatus("ERROR: " + root.path("message").asText("illegal move"));
                                default -> {
                                }
                            }
                        } catch (Exception ignored) {
                        }
                        return WebSocket.Listener.super.onText(webSocket, data, last);
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        connected.set(false);
                        updateStatus("DISCONNECTED (" + statusCode + ")");
                        log("← [CLOSE] " + statusCode + " reason=" + reason);
                        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        connected.set(false);
                        updateStatus("ERROR: " + error.getMessage());
                        log("! [ERROR] " + error);
                        WebSocket.Listener.super.onError(webSocket, error);
                    }
                }).join();
    }

    private void sendJson(String json) {
        if (!connected.get()) {
            log("! send blocked (disconnected): " + json);
            return;
        }
        ws.sendText(json.replaceAll("\\s+", " "), true);
        log("→ " + json.replaceAll("\\s+", " "));
    }

    private void updateStatus(String text) {
        Platform.runLater(() -> statusLbl.setText("Status: " + text));
    }

    private void log(String line) {
        String ts = LocalTime.now().format(HHMMSS);
        Platform.runLater(() -> {
            wsLog.appendText("[" + ts + "] " + line + "\n");
            wsLog.setScrollTop(Double.MAX_VALUE);
        });
    }

    private String readablePort(URI uri) {
        if (uri.getPort() != -1) return String.valueOf(uri.getPort());
        return switch (uri.getScheme()) {
            case "ws" -> "80";
            case "wss" -> "443";
            default -> "(unknown)";
        };
    }

    @Override
    public void stop() {
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            } catch (Exception ignored) {
            }
        }
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}