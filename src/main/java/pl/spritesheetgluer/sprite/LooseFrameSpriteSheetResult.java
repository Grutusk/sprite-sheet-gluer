package pl.spritesheetgluer.sprite;

import java.nio.file.Path;
import java.util.Objects;

public record LooseFrameSpriteSheetResult(
    String prefix,
    String outputName,
    Path outputPath,
    Path mappingPath,
    int columns,
    int rows,
    int frameCount,
    int cellWidth,
    int cellHeight,
    int sheetIndex,
    int totalSheets
) {
  public LooseFrameSpriteSheetResult {
    Objects.requireNonNull(prefix, "prefix");
    Objects.requireNonNull(outputName, "outputName");
    Objects.requireNonNull(outputPath, "outputPath");
    Objects.requireNonNull(mappingPath, "mappingPath");
    if (columns <= 0) {
      throw new IllegalArgumentException("columns must be positive: " + columns);
    }
    if (rows <= 0) {
      throw new IllegalArgumentException("rows must be positive: " + rows);
    }
    if (frameCount <= 0) {
      throw new IllegalArgumentException("frameCount must be positive: " + frameCount);
    }
    if (cellWidth <= 0) {
      throw new IllegalArgumentException("cellWidth must be positive: " + cellWidth);
    }
    if (cellHeight <= 0) {
      throw new IllegalArgumentException("cellHeight must be positive: " + cellHeight);
    }
    if (sheetIndex <= 0) {
      throw new IllegalArgumentException("sheetIndex must be positive: " + sheetIndex);
    }
    if (totalSheets <= 0) {
      throw new IllegalArgumentException("totalSheets must be positive: " + totalSheets);
    }
    if (sheetIndex > totalSheets) {
      throw new IllegalArgumentException(
          "sheetIndex must not exceed totalSheets: " + sheetIndex + " > " + totalSheets
      );
    }
  }

  public int imageWidth() {
    return columns * cellWidth;
  }

  public int imageHeight() {
    return rows * cellHeight;
  }
}
