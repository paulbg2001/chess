package ro.chess.client;

import javafx.scene.image.Image;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Assets {
  private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

  public static Image piece(String key) {
    // key: wK, bQ, etc.
    return CACHE.computeIfAbsent(key, k -> {
      var url = Assets.class.getResource("/pieces/" + k + ".png");
      if (url == null) {
        throw new IllegalStateException("Missing piece image: " + k);
      }
      return new Image(url.toExternalForm(), 64, 64, true, true);
    });
  }

  private Assets() {}
}