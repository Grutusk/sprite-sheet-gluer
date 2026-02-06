package pl.spritesheetgluer.sprite;

import pl.spritesheetgluer.definition.CharacterDefinition;
import pl.spritesheetgluer.definition.DirectionDefinition;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class SpriteSheetComposer {
  /**
   * Composes a sprite sheet without scaling; each frame is placed in a fixed-size cell.
   * All frames must share the same width and height or an error is thrown.
   * Rows follow the scan order of directions, and columns follow file name order.
   */
  public SpriteSheetRender compose(CharacterDefinition character) throws IOException {
    List<DirectionDefinition> directions = character.animations().stream()
        .flatMap(animation -> animation.directions().stream())
        .toList();

    if (directions.isEmpty()) {
      throw new IllegalStateException("No directions found for: " + character.name());
    }

    int rows = directions.size();
    int maxFrames = 0;
    int cellWidth = 0;
    int cellHeight = 0;
    List<List<BufferedImage>> directionFrames = new ArrayList<>();
    for (DirectionDefinition direction : directions) {
      List<BufferedImage> frames = new ArrayList<>();
      for (Path path : direction.frames()) {
        BufferedImage frame = readImage(path);
        frames.add(frame);
        if (cellWidth == 0 && cellHeight == 0) {
          cellWidth = frame.getWidth();
          cellHeight = frame.getHeight();
        } else if (frame.getWidth() != cellWidth || frame.getHeight() != cellHeight) {
          throw new IllegalStateException(
              "Frame size mismatch. Expected " + cellWidth + "x" + cellHeight
                  + " but found " + frame.getWidth() + "x" + frame.getHeight()
                  + " for " + path
          );
        }
      }
      directionFrames.add(frames);
      maxFrames = Math.max(maxFrames, frames.size());
    }

    int columns = maxFrames;
    int width = columns * cellWidth;
    int height = rows * cellHeight;

    if (width <= 0 || height <= 0) {
      throw new IllegalStateException("Invalid sprite sheet size for: " + character.name());
    }

    BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = sheet.createGraphics();
    try {
      graphics.setComposite(AlphaComposite.Src);
      graphics.setColor(new Color(0, 0, 0, 0));
      graphics.fillRect(0, 0, width, height);
      graphics.setComposite(AlphaComposite.SrcOver);

      int row = 0;
      int frameCount = 0;
      for (List<BufferedImage> frames : directionFrames) {
        for (int col = 0; col < frames.size(); col++) {
          BufferedImage frame = frames.get(col);
          graphics.drawImage(frame, col * cellWidth, row * cellHeight, null);
          frameCount++;
        }
        row++;
      }

      return new SpriteSheetRender(sheet, columns, rows, frameCount, cellWidth, cellHeight);
    } finally {
      graphics.dispose();
    }
  }

  private BufferedImage readImage(Path path) throws IOException {
    BufferedImage image = ImageIO.read(path.toFile());
    if (image == null) {
      throw new IOException("Unsupported image format: " + path);
    }
    return image;
  }

}
