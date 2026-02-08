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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

public class SpriteSheetComposer {
  /**
   * Composes a sprite sheet without scaling; each frame is placed in a fixed-size cell.
   * Frames that do not match the most common size are skipped.
   * Rows follow the scan order of directions, and columns follow file name order.
   */
  public SpriteSheetComposition compose(CharacterDefinition character) throws IOException {
    List<DirectionDefinition> directions = character.animations().stream()
        .flatMap(animation -> animation.directions().stream())
        .toList();

    if (directions.isEmpty()) {
      throw new IllegalStateException("No directions found for: " + character.name());
    }

    Map<SizeKey, Integer> sizeCounts = new LinkedHashMap<>();
    Map<DirectionDefinition, List<FrameCandidate>> candidatesByDirection = new LinkedHashMap<>();
    for (DirectionDefinition direction : directions) {
      List<FrameCandidate> candidates = new ArrayList<>();
      for (Path path : direction.frames()) {
        BufferedImage frame = readImage(path);
        SizeKey size = new SizeKey(frame.getWidth(), frame.getHeight());
        sizeCounts.merge(size, 1, Integer::sum);
        candidates.add(new FrameCandidate(path, frame, size));
      }
      candidatesByDirection.put(direction, candidates);
    }

    SizeKey targetSize = selectTargetSize(sizeCounts);
    if (targetSize == null) {
      throw new IllegalStateException("No frames found for: " + character.name());
    }

    int maxFrames = 0;
    int cellWidth = targetSize.width();
    int cellHeight = targetSize.height();
    List<List<BufferedImage>> directionFrames = new ArrayList<>();
    List<DirectionDefinition> filteredDirections = new ArrayList<>();
    List<Path> excludedFrames = new ArrayList<>();
    for (Map.Entry<DirectionDefinition, List<FrameCandidate>> entry : candidatesByDirection.entrySet()) {
      List<BufferedImage> frames = new ArrayList<>();
      List<Path> includedPaths = new ArrayList<>();
      for (FrameCandidate candidate : entry.getValue()) {
        if (candidate.size().equals(targetSize)) {
          frames.add(candidate.image());
          includedPaths.add(candidate.path());
        } else {
          excludedFrames.add(candidate.path());
        }
      }
      if (!frames.isEmpty()) {
        DirectionDefinition direction = entry.getKey();
        filteredDirections.add(new DirectionDefinition(direction.name(), direction.root(), includedPaths));
        directionFrames.add(frames);
        maxFrames = Math.max(maxFrames, frames.size());
      }
    }

    if (filteredDirections.isEmpty()) {
      throw new IllegalStateException(
          "No frames matching " + targetSize.width() + "x" + targetSize.height()
              + " for: " + character.name()
      );
    }

    int rows = filteredDirections.size();
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

      SpriteSheetRender render = new SpriteSheetRender(sheet, columns, rows, frameCount, cellWidth, cellHeight);
      return new SpriteSheetComposition(render, filteredDirections, excludedFrames);
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

  private SizeKey selectTargetSize(Map<SizeKey, Integer> sizeCounts) {
    SizeKey target = null;
    int maxCount = 0;
    for (Map.Entry<SizeKey, Integer> entry : sizeCounts.entrySet()) {
      int count = entry.getValue();
      if (count > maxCount) {
        maxCount = count;
        target = entry.getKey();
      }
    }
    return target;
  }

  private record SizeKey(int width, int height) {
  }

  private record FrameCandidate(Path path, BufferedImage image, SizeKey size) {
  }
}
