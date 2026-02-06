package pl.spritesheetgluer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.spritesheetgluer.definition.CharacterDefinition;
import pl.spritesheetgluer.sprite.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
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
    SpriteSheetRender render = composer.compose(character);

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

    List<String> lines = Files.readAllLines(result.mappingPath());
    assertFalse(lines.isEmpty());
    assertEquals("grid: " + result.rows() + "x" + result.columns(), lines.get(0));
  }

}
