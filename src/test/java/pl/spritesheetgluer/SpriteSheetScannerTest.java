package pl.spritesheetgluer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.spritesheetgluer.definition.CharacterDefinition;
import pl.spritesheetgluer.sprite.SpriteSheetScanner;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SpriteSheetScannerTest {
  @TempDir
  Path tempDir;

  @Test
  void scansCharacterFolderFromResources() throws Exception {
    Path root = TestResourceHelper.copyResourceDirectory("test", tempDir);

    SpriteSheetScanner scanner = new SpriteSheetScanner();
    List<CharacterDefinition> characters = scanner.scan(root);

    assertEquals(1, characters.size());
    CharacterDefinition character = characters.get(0);
    assertEquals(root.getFileName().toString(), character.name());
    assertFalse(character.animations().isEmpty());
  }

  @Test
  void scansFlatRootFrames() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("flat"));
    Path first = root.resolve("b.png");
    Path second = root.resolve("a.png");
    writePng(first, 8, 8, new Color(255, 0, 0, 255));
    writePng(second, 8, 8, new Color(0, 255, 0, 255));

    SpriteSheetScanner scanner = new SpriteSheetScanner();
    List<CharacterDefinition> characters = scanner.scan(root);

    assertEquals(1, characters.size());
    CharacterDefinition character = characters.get(0);
    assertEquals(root.getFileName().toString(), character.name());
    assertEquals(1, character.animations().size());
    assertEquals(1, character.animations().get(0).directions().size());
    List<Path> frames = character.animations().get(0).directions().get(0).frames();
    assertEquals(List.of(second, first), frames);
  }

  private static void writePng(Path path, int width, int height, Color color) throws IOException {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setColor(color);
      graphics.fillRect(0, 0, width, height);
    } finally {
      graphics.dispose();
    }
    ImageIO.write(image, "png", path.toFile());
  }
}
