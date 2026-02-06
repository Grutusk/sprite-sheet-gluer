package pl.spritesheetgluer.sprite;

import java.nio.file.Path;
import java.util.Objects;

public record SpriteSheetResult(
    String characterName,
    Path outputPath,
    Path mappingPath,
    int columns,
    int rows,
    int frameCount,
    int cellWidth,
    int cellHeight
) {
  public SpriteSheetResult {
    Objects.requireNonNull(characterName, "characterName");
    Objects.requireNonNull(outputPath, "outputPath");
    Objects.requireNonNull(mappingPath, "mappingPath");
    if (cellWidth <= 0) {
      throw new IllegalArgumentException("cellWidth must be positive: " + cellWidth);
    }
    if (cellHeight <= 0) {
      throw new IllegalArgumentException("cellHeight must be positive: " + cellHeight);
    }
  }
}
