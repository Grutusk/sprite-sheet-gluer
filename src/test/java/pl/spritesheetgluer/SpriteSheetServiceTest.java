package pl.spritesheetgluer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.spritesheetgluer.definition.CharacterDefinition;
import pl.spritesheetgluer.sprite.*;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpriteSheetServiceTest {
  @TempDir
  Path tempDir;

  @Test
  void composesSpriteSheetWithExpectedDimensions() throws Exception {
    Path root = TestResourceHelper.copyResourceDirectory("test", tempDir);
    SpriteSheetScanner scanner = new SpriteSheetScanner();
    CharacterDefinition character = scanner.scan(root).get(0);

    SpriteSheetComposer composer = new SpriteSheetComposer();
    SpriteSheetComposition composition = composer.compose(character);
    SpriteSheetRender render = composition.render();
    assertTrue(composition.excludedFrames().isEmpty());

    int expectedRows = character.animations().stream()
        .mapToInt(animation -> animation.directions().size())
        .sum();
    int expectedColumns = character.animations().stream()
        .flatMap(animation -> animation.directions().stream())
        .mapToInt(direction -> direction.frames().size())
        .max()
        .orElse(0);
    int expectedFrames = character.animations().stream()
        .flatMap(animation -> animation.directions().stream())
        .mapToInt(direction -> direction.frames().size())
        .sum();
    Path firstFrame = character.animations().stream()
        .flatMap(animation -> animation.directions().stream())
        .flatMap(direction -> direction.frames().stream())
        .findFirst()
        .orElseThrow();
    BufferedImage firstImage = ImageIO.read(firstFrame.toFile());
    int expectedCellWidth = firstImage.getWidth();
    int expectedCellHeight = firstImage.getHeight();

    assertEquals(expectedRows, render.rows());
    assertEquals(expectedColumns, render.columns());
    assertEquals(expectedFrames, render.frameCount());
    assertEquals(expectedCellWidth, render.cellWidth());
    assertEquals(expectedCellHeight, render.cellHeight());
    assertEquals(expectedColumns * expectedCellWidth, render.image().getWidth());
    assertEquals(expectedRows * expectedCellHeight, render.image().getHeight());
  }

  @Test
  void writesSpriteSheetToCharacterFolder() throws Exception {
    Path root = TestResourceHelper.copyResourceDirectory("test", tempDir);

    SpriteSheetService service = new SpriteSheetService();
    List<SpriteSheetResult> results = service.generate(root);

    assertEquals(1, results.size());
    SpriteSheetResult result = results.get(0);
    Path expectedPath = root.resolve(root.getFileName().toString() + ".png");
    Path expectedMappingPath = root.resolve(root.getFileName() + ".frames.txt");
    assertEquals(expectedPath, result.outputPath());
    assertEquals(expectedMappingPath, result.mappingPath());
    assertTrue(result.cellWidth() > 0);
    assertTrue(result.cellHeight() > 0);
    assertTrue(Files.exists(result.outputPath()));
    assertTrue(Files.exists(result.mappingPath()));
    assertTrue(result.excludedFrames().isEmpty());

    List<String> lines = Files.readAllLines(result.mappingPath());
    assertFalse(lines.isEmpty());
    assertEquals("grid: " + result.rows() + "x" + result.columns(), lines.get(0));
  }

  @Test
  void writesSpriteSheetForFlatRoot() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("flat"));
    Path first = root.resolve("2.png");
    Path second = root.resolve("1.png");
    writePng(first, 6, 6, new Color(0, 0, 255, 255));
    writePng(second, 6, 6, new Color(255, 255, 0, 255));

    SpriteSheetService service = new SpriteSheetService();
    List<SpriteSheetResult> results = service.generate(root);

    assertEquals(1, results.size());
    SpriteSheetResult result = results.get(0);
    assertEquals(1, result.rows());
    assertEquals(2, result.columns());
    Path expectedPath = root.resolve(root.getFileName().toString() + ".png");
    Path expectedMappingPath = root.resolve(root.getFileName() + ".frames.txt");
    assertEquals(expectedPath, result.outputPath());
    assertEquals(expectedMappingPath, result.mappingPath());
    assertTrue(Files.exists(result.outputPath()));
    assertTrue(Files.exists(result.mappingPath()));
    assertTrue(result.excludedFrames().isEmpty());

    List<String> lines = Files.readAllLines(result.mappingPath());
    assertEquals("grid: " + result.rows() + "x" + result.columns(), lines.get(0));
    assertTrue(lines.get(1).startsWith(root.getFileName().toString() + " -> "));
  }

  @Test
  void skipsMismatchedFrameSizes() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("mixed"));
    Path animation = Files.createDirectory(root.resolve("walk"));
    Path keepA = animation.resolve("a.png");
    Path keepB = animation.resolve("b.png");
    Path dropC = animation.resolve("c.png");
    writePng(keepA, 8, 8, new Color(0, 128, 0, 255));
    writePng(keepB, 8, 8, new Color(0, 255, 255, 255));
    writePng(dropC, 12, 12, new Color(255, 0, 255, 255));

    SpriteSheetService service = new SpriteSheetService();
    List<SpriteSheetResult> results = service.generate(root);

    assertEquals(1, results.size());
    SpriteSheetResult result = results.get(0);
    assertEquals(1, result.rows());
    assertEquals(2, result.columns());
    assertEquals(2, result.frameCount());
    assertEquals(1, result.excludedFrames().size());
    assertEquals(dropC, result.excludedFrames().get(0));
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
