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

/**
 * Aplicatia Principala (Clientul).
 * Aici desenam ferestrele si ne conectam la server.
 */
public class ChessApp extends Application {

    // Serverul cu care vorbim (WebSocket)
    private WebSocket ws;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    // Elemente de interfata (etichete, butoane)
    private final Label statusLbl = new Label("Status: DECONECTAT");
    private final Label colorLbl = new Label("");
    private final Label turnLbl = new Label("");
    private final Label playersLbl = new Label("Jucatori: 0/2");

    // Zona de text unde vedem ce mesaje trimitem/primim (pentru debug)
    private final TextArea wsLog = new TextArea();

    // Format pentru ora (ca sa stim cand s-a intamplat ceva)
    private final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Folosit pentru citit JSON
    private final ObjectMapper om = new ObjectMapper();

    private String serverHost = "localhost";
    private URI serverUri;
    private BoardView board; // Asta e tabla noastra desenata
    private String myColor = null; // Culoarea mea ("WHITE" sau "BLACK")

    @Override
    public void start(Stage stage) {
        // Prima data aratam fereastra de conectare
        showConnectionDialog(stage);
    }

    /**
     * Fereastra mica unde scrii IP-ul serverului.
     */
    private void showConnectionDialog(Stage stage) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Conectare");
        dialog.setHeaderText("Unde e serverul?");

        TextField ipField = new TextField("localhost");
        ipField.setPromptText("ex: localhost sau 192.168.x.x");

        VBox content = new VBox(10, new Label("Adresa IP:"), ipField);
        content.setPadding(new Insets(20));

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                return ipField.getText().trim();
            }
            return null;
        });

        // Daca apasa OK, pornim jocul
        dialog.showAndWait().ifPresentOrElse(
                host -> {
                    serverHost = host.isEmpty() ? "localhost" : host;
                    initializeGame(stage);
                },
                () -> Platform.exit());
    }

    /**
     * Aici construim toata interfata jocului.
     */
    private void initializeGame(Stage stage) {
        String url = "ws://" + serverHost + ":8080/ws";
        this.serverUri = URI.create(url);

        // Setari pentru zona de log (sa nu putem scrie in ea, doar sa citim)
        wsLog.setEditable(false);
        wsLog.setPrefRowCount(4);
        wsLog.setStyle("-fx-font-family: Monospaced; -fx-font-size: 11px;");

        // Stilizam etichetele
        colorLbl.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        turnLbl.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        // Butonul de Undo (Muta inapoi)
        Button undoBtn = new Button("Muta Inapoi");
        undoBtn.setOnAction(e -> {
            if (connected.get()) {
                sendJson("{\"type\":\"UNDO_MOVE\"}");
            }
        });

        // Buton de reset
        Button resetBtn = new Button("Reset Joc");
        resetBtn.setOnAction(e -> {
            if (connected.get()) {
                sendJson("{\"type\":\"RESET_GAME\"}");
            }
        });

        // Bara de sus cu informatii
        HBox topBar = new HBox(10, statusLbl, new Separator(), colorLbl, new Separator(), turnLbl, new Separator(),
                undoBtn, resetBtn);
        topBar.setPadding(new Insets(10));
        topBar.setAlignment(Pos.CENTER_LEFT);

        // Cream tabla de sah
        board = new BoardView();

        // Ce se intampla cand incercam sa mutam o piesa cu mouse-ul
        board.setOnMoveAttempt((from, to) -> {
            if (!connected.get()) {
                return;
            }
            sendJson(om.createObjectNode()
                    .put("type", "MAKE_MOVE")
                    .put("from", from)
                    .put("to", to)
                    .toString());
        });

        // Punem totul in fereastra
        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(board);
        root.setBottom(wsLog);

        stage.setTitle("Joc de sah - " + serverHost);
        stage.setScene(new Scene(root, 800, 850)); // Dimensiunea ferestrei
        stage.show();

        // Ne conectam efectiv la server
        connectWs(url);
    }

    /**
     * Se conecteaza la WebSocket si asculta mesaje.
     */
    private void connectWs(String url) {
        try {
            HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(URI.create(url), new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket webSocket) {
                            ws = webSocket;
                            connected.set(true);
                            updateStatus("CONECTAT!");
                            log("Succes conectare!");
                            WebSocket.Listener.super.onOpen(webSocket);
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            // Cand primim un mesaj de la server
                            String json = data.toString();
                            log("Primit: " + json);
                            handleMessage(json);
                            return WebSocket.Listener.super.onText(webSocket, data, last);
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                            connected.set(false);
                            updateStatus("Deconectat.");
                            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            updateStatus("Eroare: " + error.getMessage());
                        }
                    });
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setContentText("Nu m-am putut conecta la server!");
            alert.showAndWait();
        }
    }

    /**
     * Aici decisem ce facem cu mesajul primit de la server.
     */
    private void handleMessage(String json) {
        try {
            JsonNode root = om.readTree(json);
            String type = root.path("type").asText();

            Platform.runLater(() -> {
                switch (type) {
                    case "WELCOME":
                        // Mesaj de bun venit
                        myColor = root.path("color").asText();
                        String fen = root.path("fen").asText();
                        board.setPosition(fen);

                        // Afisam culoarea noastra
                        if (myColor.equals("WHITE")) {
                            colorLbl.setText("Esti: ALB");
                            colorLbl.setTextFill(Color.ORANGE);
                        } else {
                            colorLbl.setText("Esti: NEGRU");
                            colorLbl.setTextFill(Color.BLACK);
                        }
                        break;
                    case "MOVE_APPLIED":
                        // S-a facut o mutare, actualizam tabla
                        String fenMove = root.path("fen").asText();
                        board.setPosition(fenMove);

                        // Vedem al cui e randul (doar informativ)
                        boolean whiteToMove = fenMove.contains(" w ");
                        if (whiteToMove) {
                            turnLbl.setText("Urmeaza: ALBUL");
                        } else {
                            turnLbl.setText("Urmeaza: NEGRUL");
                        }
                        break;
                    case "PLAYERS_UPDATE":
                        // Cati jucatori sunt
                        int count = root.path("count").asInt();
                        playersLbl.setText("Jucatori: " + count);
                        break;
                    case "GAME_OVER":
                        // S-a terminat jocul
                        String winner = root.path("winner").asText();
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Joc Gata");
                        alert.setHeaderText("Castigator: " + winner);
                        alert.showAndWait();

                        // Resetam automat
                        sendJson("{\"type\":\"RESET_GAME\"}");
                        break;
                    case "ERROR":
                        String msg = root.path("message").asText();
                        log("EROARE: " + msg);
                        break;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendJson(String json) {
        if (ws != null && connected.get()) {
            ws.sendText(json, true);
            log("Trimis: " + json);
        }
    }

    private void updateStatus(String text) {
        Platform.runLater(() -> statusLbl.setText(text));
    }

    private void log(String line) {
        Platform.runLater(() -> {
            wsLog.appendText("[" + LocalTime.now().format(HHMMSS) + "] " + line + "\n");
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
