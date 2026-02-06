package pl.spritesheetgluer.sprite;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SpriteSheetWriter {
  public void write(BufferedImage image, Path outputPath) throws IOException {
    Files.createDirectories(outputPath.getParent());
    ImageIO.write(image, "png", outputPath.toFile());
  }
}
