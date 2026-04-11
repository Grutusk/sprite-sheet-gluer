package pl.spritesheetgluer.sprite;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record ExistingSpriteSheetResult(
    String outputName,
    Path outputPath,
    Path mappingPath,
    int columns,
    int rows,
    int frameCount,
    int cellWidth,
    int cellHeight,
    List<Path> excludedSheets
) {
  public ExistingSpriteSheetResult {
    Objects.requireNonNull(outputName, "outputName");
    Objects.requireNonNull(outputPath, "outputPath");
    Objects.requireNonNull(mappingPath, "mappingPath");
    Objects.requireNonNull(excludedSheets, "excludedSheets");
    excludedSheets = List.copyOf(excludedSheets);
    if (cellWidth <= 0) {
      throw new IllegalArgumentException("cellWidth must be positive: " + cellWidth);
    }
    if (cellHeight <= 0) {
      throw new IllegalArgumentException("cellHeight must be positive: " + cellHeight);
    }
  }

  public int imageWidth() {
    return columns * cellWidth;
  }

  public int imageHeight() {
    return rows * cellHeight;
  }
}
