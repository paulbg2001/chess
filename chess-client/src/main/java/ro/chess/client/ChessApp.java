package ro.chess.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
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
    private final Label colorLbl = new Label("");
    private final Label turnLbl = new Label("");
    private final Label playersLbl = new Label("Jucători: 0/2");
    private final TextArea wsLog = new TextArea();
    private final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final ObjectMapper om = new ObjectMapper();

    private String serverHost = "localhost";
    private URI serverUri;
    private BoardView board;
    private String myColor = null; // "WHITE" or "BLACK"

    @Override
    public void start(Stage stage) {
        // Show connection dialog first
        showConnectionDialog(stage);
    }

    private void showConnectionDialog(Stage stage) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Conectare la Server");
        dialog.setHeaderText("Introdu adresa IP a serverului");

        TextField ipField = new TextField("localhost");
        ipField.setPromptText("ex: 192.168.1.100");
        ipField.setPrefWidth(200);

        VBox content = new VBox(10);
        content.getChildren().addAll(
                new Label("Adresa server (IP sau hostname):"),
                ipField,
                new Label("Port implicit: 8080")
        );
        content.setPadding(new Insets(20));

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                return ipField.getText().trim();
            }
            return null;
        });

        dialog.showAndWait().ifPresentOrElse(
                host -> {
                    serverHost = host.isEmpty() ? "localhost" : host;
                    initializeGame(stage);
                },
                () -> Platform.exit()
        );
    }

    private void initializeGame(Stage stage) {
        String url = "ws://" + serverHost + ":8080/ws";
        this.serverUri = URI.create(url);

        // Log WS sus
        wsLog.setEditable(false);
        wsLog.setWrapText(true);
        wsLog.setPrefRowCount(3);
        wsLog.setStyle("-fx-font-family: Consolas, Menlo, Monospaced; -fx-font-size: 11px;");

        // Color and turn labels
        colorLbl.setFont(Font.font("System", FontWeight.BOLD, 14));
        turnLbl.setFont(Font.font("System", FontWeight.BOLD, 14));
        playersLbl.setFont(Font.font("System", 12));

        Button undoBtn = new Button("↩ Undo");
        undoBtn.setOnAction(e -> {
            if (connected.get()) {
                sendJson("{\"type\":\"UNDO_MOVE\"}");
            }
        });
        Button clearBtn = new Button("Clear");
        clearBtn.setOnAction(e -> wsLog.clear());

        HBox topBar = new HBox(10, statusLbl, new Separator(), colorLbl, turnLbl, new Separator(), playersLbl, new Separator(), undoBtn, clearBtn);
        topBar.setPadding(new Insets(4, 8, 4, 8));
        topBar.setAlignment(Pos.CENTER_LEFT);

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

        stage.setTitle("Chess Client - " + serverHost);
        stage.setScene(new Scene(root, 740, 800));
        stage.show();

        connectWs(url);
    }

    private void connectWs(String url) {
        try {
            ws = HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(URI.create(url), new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket webSocket) {
                            connected.set(true);
                            updateStatus("CONNECTED to " + serverHost);
                            log("→ [OPEN] " + serverUri);
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
                                    case "WELCOME" -> {
                                        myColor = root.path("color").asText();
                                        String fen = root.path("fen").asText();
                                        boolean yourTurn = root.path("yourTurn").asBoolean();
                                        Platform.runLater(() -> {
                                            board.setPosition(fen);
                                            updateColorLabel();
                                            updateTurnLabel(yourTurn);
                                        });
                                    }
                                    case "MOVE_APPLIED" -> {
                                        String fen = root.path("fen").asText();
                                        Platform.runLater(() -> {
                                            board.setPosition(fen);
                                            // Determine whose turn it is from FEN
                                            boolean whiteToMove = fen.contains(" w ");
                                            boolean yourTurn = (myColor.equals("WHITE") == whiteToMove);
                                            updateTurnLabel(yourTurn);
                                        });
                                    }
                                    case "PLAYERS_UPDATE" -> {
                                        int count = root.path("count").asInt();
                                        boolean whiteConnected = root.path("whiteConnected").asBoolean();
                                        boolean blackConnected = root.path("blackConnected").asBoolean();
                                        Platform.runLater(() -> {
                                            playersLbl.setText("Jucători: " + count + "/2");
                                            if (count == 2) {
                                                playersLbl.setTextFill(Color.GREEN);
                                            } else {
                                                playersLbl.setTextFill(Color.ORANGE);
                                            }
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
                                            sendJson("{\"type\":\"RESET_GAME\"}");
                                        });
                                    }
                                    case "ERROR" -> {
                                        String msg = root.path("message").asText("eroare");
                                        Platform.runLater(() -> updateStatus("ERROR: " + msg));
                                    }
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
        } catch (Exception e) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Eroare conexiune");
                alert.setHeaderText("Nu s-a putut conecta la server");
                alert.setContentText("Verifică dacă serverul rulează pe " + serverHost + ":8080");
                alert.showAndWait();
            });
        }
    }

    private void updateColorLabel() {
        Platform.runLater(() -> {
            if ("WHITE".equals(myColor)) {
                colorLbl.setText("♔ ALB");
                colorLbl.setTextFill(Color.DARKGOLDENROD);
            } else if ("BLACK".equals(myColor)) {
                colorLbl.setText("♚ NEGRU");
                colorLbl.setTextFill(Color.DARKSLATEGRAY);
            }
        });
    }

    private void updateTurnLabel(boolean yourTurn) {
        Platform.runLater(() -> {
            if (yourTurn) {
                turnLbl.setText("▶ Rândul TĂU!");
                turnLbl.setTextFill(Color.GREEN);
            } else {
                turnLbl.setText("⏳ Așteaptă...");
                turnLbl.setTextFill(Color.GRAY);
            }
        });
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
