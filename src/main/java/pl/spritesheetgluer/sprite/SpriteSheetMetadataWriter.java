package pl.spritesheetgluer.sprite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SpriteSheetMetadataWriter {
  public void write(Path outputPath, List<String> lines) throws IOException {
    Files.createDirectories(outputPath.getParent());
    Files.write(outputPath, lines, StandardCharsets.UTF_8);
  }
}
