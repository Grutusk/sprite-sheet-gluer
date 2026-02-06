package pl.spritesheetgluer.sprite;

import java.awt.image.BufferedImage;
import java.util.Objects;

public record SpriteSheetRender(
    BufferedImage image,
    int columns,
    int rows,
    int frameCount,
    int cellWidth,
    int cellHeight
) {
  public SpriteSheetRender {
    Objects.requireNonNull(image, "image");
    if (cellWidth <= 0) {
      throw new IllegalArgumentException("cellWidth must be positive: " + cellWidth);
    }
    if (cellHeight <= 0) {
      throw new IllegalArgumentException("cellHeight must be positive: " + cellHeight);
    }
  }
}
