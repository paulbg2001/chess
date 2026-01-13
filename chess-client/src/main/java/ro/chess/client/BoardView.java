package ro.chess.client;

import javafx.scene.Cursor;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.scene.layout.StackPane;
import ro.chess.client.util.BoardUtils;

import java.util.function.BiConsumer;

/**
 * Tabla de sah "activa".
 * Extinde TablaBaza (mostenire) si adauga logica pieselor si a click-urilor.
 */
public class BoardView extends TablaBaza {

    // Matricea logica de piese (ex: "wP", "bQ")
    private String[][] piese = new String[8][8];

    // Cine asculta mutarile (ChessApp)
    private BiConsumer<String, String> moveHandler;

    // Patratelul selectat (ex: "e2")
    private String patratSelectat = null;

    public BoardView() {
        super(); // Apelam constructorul din TablaBaza ca sa deseneze grila

        // Adaugam logica de click pe fiecare patratel format in baza
        setupClickHandlers();

        // Pozitia de start standard (FEN)
        setPosition("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1");
    }

    /**
     * Parcurgem celule create de parinte si le punem click handler.
     */
    private void setupClickHandlers() {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                final int rand = r;
                final int col = c;
                // 'celule' e protected in TablaBaza, deci il vedem aici
                celule[r][c].setOnMouseClicked(e -> {
                    if (e.getButton() == MouseButton.PRIMARY) {
                        onClickPatrat(rand, col);
                    }
                });
            }
        }
    }

    public void setOnMoveAttempt(BiConsumer<String, String> handler) {
        this.moveHandler = handler;
    }

    /**
     * Primeste un FEN string si pune piesele pe tabla.
     */
    public void setPosition(String fen) {
        // 1. Golim matricea logica
        for (int i = 0; i < 8; i++)
            for (int j = 0; j < 8; j++)
                piese[i][j] = null;

        // 2. Citim FEN-ul
        String[] bucati = fen.split(" ");
        String[] randuri = bucati[0].split("/");

        for (int r = 0; r < 8; r++) {
            int c = 0;
            for (char ch : randuri[r].toCharArray()) {
                if (Character.isDigit(ch)) {
                    // Spatii goale
                    c += Character.getNumericValue(ch);
                } else {
                    // Piesa
                    String culoare = Character.isUpperCase(ch) ? "w" : "b";
                    String tip = String.valueOf(Character.toUpperCase(ch)); // P, N, B, R, Q, K
                    piese[r][c] = culoare + tip;
                    c++;
                }
            }
        }

        // 3. Desenam efectiv imaginile
        deseneazaPiese();
    }

    private void deseneazaPiese() {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                StackPane cell = celule[r][c];

                // Scoatem orice imagine veche (pastram doar Rectangul de fundal - index 0)
                cell.getChildren().removeIf(node -> node instanceof ImageView);

                String codPiesa = piese[r][c];
                if (codPiesa != null) {
                    // Cerem imaginea din Assets (cache)
                    ImageView img = new ImageView(Assets.getImaginePiesa(codPiesa));
                    img.setFitWidth(MARIME_PATRAT * 0.85);
                    img.setFitHeight(MARIME_PATRAT * 0.85);
                    img.setPreserveRatio(true);
                    img.setMouseTransparent(true); // Click-ul trece prin poza
                    cell.getChildren().add(img);
                }
            }
        }
        // Resetam selectia daca tabla s-a schimbat din exterior
        patratSelectat = null;
        evidentiazaPatrat(null);
    }

    private void onClickPatrat(int rand, int col) {
        String coordonata = BoardUtils.toSquare(rand, col); // ex: "e4"
        String piesaAici = piese[rand][col];

        if (patratSelectat == null) {
            // Nu am selectat nimic -> incercam sa selectam
            if (piesaAici != null) {
                patratSelectat = coordonata;
                evidentiazaPatrat(coordonata);
                setCursor(Cursor.HAND);
            }
        } else {
            // Aveam deja o piesa selectata -> vrem sa MUTAM aici
            if (moveHandler != null) {
                moveHandler.accept(patratSelectat, coordonata);
            }

            // Indiferent ce se intampla, deselectam dupa click
            patratSelectat = null;
            evidentiazaPatrat(null);
            setCursor(Cursor.DEFAULT);
        }
    }

    // Deseneaza un contur galben pe patratul selectat
    private void evidentiazaPatrat(String sq) {
        // Stergem conturul de peste tot
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Rectangle bg = (Rectangle) celule[r][c].getChildren().get(0);
                bg.setStroke(null);
            }
        }

        if (sq != null) {
            // Calculam rand/col din string (ex: "e4") folosind BoardUtils
            int c = BoardUtils.getIndexColoana(sq.charAt(0));
            int r = BoardUtils.getIndexRand(sq.charAt(1));

            Rectangle bg = (Rectangle) celule[r][c].getChildren().get(0);
            bg.setStroke(Color.GOLD);
            bg.setStrokeWidth(4);
        }
    }
}
