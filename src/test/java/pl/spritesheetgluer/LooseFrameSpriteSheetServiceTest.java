package pl.spritesheetgluer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.spritesheetgluer.sprite.LooseFrameSpriteSheetBatchResult;
import pl.spritesheetgluer.sprite.LooseFrameSpriteSheetResult;
import pl.spritesheetgluer.sprite.LooseFrameSpriteSheetService;
import pl.spritesheetgluer.sprite.SpriteSheetMetadataWriter;
import pl.spritesheetgluer.sprite.SpriteSheetWriter;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LooseFrameSpriteSheetServiceTest {
  @TempDir
  Path tempDir;

  @Test
  void groupsLooseFramesByPrefixAndIgnoresPreviousSheetOutputs() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("loose"));
    writePng(root.resolve("Stone 01.png"), 4, 4, Color.RED);
    writePng(root.resolve("StoneB03.png"), 4, 4, Color.GREEN);
    writePng(root.resolve("Stone_02.png"), 4, 4, Color.BLUE);
    writePng(root.resolve("Ground 01.png"), 4, 4, Color.YELLOW);
    writePng(root.resolve("Stone-sheet.png"), 4, 4, Color.BLACK);

    LooseFrameSpriteSheetService service = new LooseFrameSpriteSheetService();
    LooseFrameSpriteSheetBatchResult batch = service.generate(root);

    assertTrue(batch.excludedFrames().isEmpty());
    assertEquals(Map.of("4x4", 4), batch.detectedFrameSizes());
    assertEquals(2, batch.sheets().size());

    LooseFrameSpriteSheetResult ground = batch.sheets().get(0);
    assertEquals("Ground", ground.prefix());
    assertEquals("Ground-sheet", ground.outputName());
    assertEquals(1, ground.columns());
    assertEquals(1, ground.rows());
    assertEquals(1, ground.frameCount());

    LooseFrameSpriteSheetResult stone = batch.sheets().get(1);
    assertEquals("Stone", stone.prefix());
    assertEquals("Stone-sheet", stone.outputName());
    assertEquals(2, stone.columns());
    assertEquals(2, stone.rows());
    assertEquals(3, stone.frameCount());

    BufferedImage stoneSheet = ImageIO.read(stone.outputPath().toFile());
    assertEquals(8, stoneSheet.getWidth());
    assertEquals(8, stoneSheet.getHeight());
    assertCellColor(stoneSheet, 0, 0, 4, 4, Color.RED);
    assertCellColor(stoneSheet, 1, 0, 4, 4, Color.BLUE);
    assertCellColor(stoneSheet, 0, 1, 4, 4, Color.GREEN);

    List<String> mapping = Files.readAllLines(stone.mappingPath());
    assertIterableEquals(List.of(
        "grid: 2x2",
        "Stone 01 -> 0",
        "Stone_02 -> 1",
        "StoneB03 -> 2"
    ), mapping);
  }

  @Test
  void splitsLargePrefixGroupAcrossMultipleSheetsWhenTextureLimitIsReached() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("split"));
    writePng(root.resolve("Stone 01.png"), 4, 4, Color.RED);
    writePng(root.resolve("Stone 02.png"), 4, 4, Color.GREEN);
    writePng(root.resolve("Stone 03.png"), 4, 4, Color.BLUE);
    writePng(root.resolve("Stone 04.png"), 4, 4, Color.YELLOW);
    writePng(root.resolve("Stone 05.png"), 4, 4, Color.CYAN);

    LooseFrameSpriteSheetService service = new LooseFrameSpriteSheetService(
        new SpriteSheetWriter(),
        new SpriteSheetMetadataWriter(),
        8
    );
    LooseFrameSpriteSheetBatchResult batch = service.generate(root);

    assertTrue(batch.excludedFrames().isEmpty());
    assertEquals(2, batch.sheets().size());

    LooseFrameSpriteSheetResult first = batch.sheets().get(0);
    assertEquals("Stone-sheet-01", first.outputName());
    assertEquals(1, first.sheetIndex());
    assertEquals(2, first.totalSheets());
    assertEquals(2, first.columns());
    assertEquals(2, first.rows());
    assertEquals(4, first.frameCount());

    LooseFrameSpriteSheetResult second = batch.sheets().get(1);
    assertEquals("Stone-sheet-02", second.outputName());
    assertEquals(2, second.sheetIndex());
    assertEquals(2, second.totalSheets());
    assertEquals(1, second.columns());
    assertEquals(1, second.rows());
    assertEquals(1, second.frameCount());

    List<String> firstMapping = Files.readAllLines(first.mappingPath());
    assertIterableEquals(List.of(
        "grid: 2x2",
        "Stone 01 -> 0",
        "Stone 02 -> 1",
        "Stone 03 -> 2",
        "Stone 04 -> 3"
    ), firstMapping);

    List<String> secondMapping = Files.readAllLines(second.mappingPath());
    assertIterableEquals(List.of(
        "grid: 1x1",
        "Stone 05 -> 0"
    ), secondMapping);
  }

  @Test
  void skipsFramesThatDoNotMatchTheMostCommonPrefixSize() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("mismatch"));
    Path included = root.resolve("Ground 01.png");
    Path excluded = root.resolve("Ground 02.png");
    writePng(included, 4, 4, Color.ORANGE);
    writePng(excluded, 8, 8, Color.PINK);
    writePng(root.resolve("Stone 01.png"), 4, 4, Color.GRAY);

    LooseFrameSpriteSheetService service = new LooseFrameSpriteSheetService();
    LooseFrameSpriteSheetBatchResult batch = service.generate(root);

    assertEquals(List.of(excluded), batch.excludedFrames());
    assertEquals(2, batch.sheets().size());
    assertEquals(1, batch.sheets().get(0).frameCount());
    assertEquals(1, batch.sheets().get(1).frameCount());
  }

  @Test
  void usesExplicitFrameSizeWhenProvided() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("explicit-size"));
    Path included = root.resolve("Stone 01.png");
    Path excludedA = root.resolve("Stone 02.png");
    Path excludedB = root.resolve("Ground 01.png");
    writePng(included, 4, 4, Color.RED);
    writePng(excludedA, 8, 8, Color.GREEN);
    writePng(excludedB, 8, 8, Color.BLUE);

    LooseFrameSpriteSheetService service = new LooseFrameSpriteSheetService();
    LooseFrameSpriteSheetBatchResult batch = service.generate(root, 4, 4);

    assertEquals(List.of(excludedB, excludedA), batch.excludedFrames());
    assertEquals(Map.of("4x4", 1, "8x8", 2), batch.detectedFrameSizes());
    assertEquals(1, batch.sheets().size());
    LooseFrameSpriteSheetResult result = batch.sheets().get(0);
    assertEquals("Stone", result.prefix());
    assertEquals(1, result.frameCount());

    List<String> mapping = Files.readAllLines(result.mappingPath());
    assertIterableEquals(List.of(
        "grid: 1x1",
        "Stone 01 -> 0"
    ), mapping);
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

  private static void assertCellColor(
      BufferedImage image,
      int column,
      int row,
      int cellWidth,
      int cellHeight,
      Color expected
  ) {
    int x = column * cellWidth + 1;
    int y = row * cellHeight + 1;
    assertEquals(expected.getRGB(), image.getRGB(x, y));
  }
}
