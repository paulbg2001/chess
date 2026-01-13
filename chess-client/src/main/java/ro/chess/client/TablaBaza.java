package ro.chess.client;

import javafx.geometry.Pos;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * Clasa de Baza pentru Tabla.
 * Se ocupa DOAR de desenarea patratelelor (alb/verde).
 * 
 * Aici demonstram mostenirea: TablaBaza -> TablaSah (BoardView).
 */
public class TablaBaza extends GridPane {

    // Dimensiunea unui patratel
    protected static final double MARIME_PATRAT = 80;

    // Matricea de celule vizuale (unde punem piese)
    protected final StackPane[][] celule = new StackPane[8][8];

    public TablaBaza() {
        deseneazaGrila();
    }

    private void deseneazaGrila() {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                // Cream containerul pentru un patratel
                StackPane cell = new StackPane();
                cell.setPrefSize(MARIME_PATRAT, MARIME_PATRAT);

                // Calculam culoarea (sah = alternant)
                boolean esteAlb = (r + c) % 2 == 0;
                Color culoare = esteAlb ? Color.web("#EEEED2") : Color.web("#769656");

                // Desenam fundalul
                Rectangle bg = new Rectangle(MARIME_PATRAT, MARIME_PATRAT);
                bg.setFill(culoare);

                cell.getChildren().add(bg);
                cell.setAlignment(Pos.CENTER);

                // Salvam referinta si adaugam in grid
                celule[r][c] = cell;
                add(cell, c, r);
            }
        }
    }
}
