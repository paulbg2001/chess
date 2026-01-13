package ro.chess.client;

import javafx.scene.image.Image;
import java.util.HashMap;
import java.util.Map;

/**
 * Gestionam imaginile pieselor aici.
 * Le incarcam o singura data si le tinem minte (cache) ca sa nu le citim de pe
 * disc la fiecare frame.
 */
public class Assets {
    // Aici tinem minte pozele deja incarcate: "wP" -> Poza cu Pion Alb
    private static final Map<String, Image> cachePoze = new HashMap<>();

    /**
     * Da-mi poza pentru piesa ceruta (ex: "wK" = White King / Rege Alb).
     */
    public static Image getImaginePiesa(String numePiesa) {
        // Daca am incarcat-o deja, o returnam direct
        if (cachePoze.containsKey(numePiesa)) {
            return cachePoze.get(numePiesa);
        }

        // Daca nu, o cautam in fisiere
        try {
            // Calea catre poza: /pieces/wK.png
            String cale = "/pieces/" + numePiesa + ".png";
            // Incarcam imaginea
            Image img = new Image(Assets.class.getResourceAsStream(cale), 64, 64, true, true);

            // O tinem minte pentru data viitoare
            cachePoze.put(numePiesa, img);
            return img;
        } catch (Exception e) {
            System.err.println("Nu am gasit poza pentru: " + numePiesa);
            return null;
        }
    }
}
