package pl.spritesheetgluer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.spritesheetgluer.sprite.ExistingSpriteSheetResult;
import pl.spritesheetgluer.sprite.ExistingSpriteSheetService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExistingSpriteSheetServiceTest {
  @TempDir
  Path tempDir;

  @Test
  void mergesExistingSheetsRowByRow() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("FireMage"));
    writeSheet(root.resolve("Attack.png"), 4, 4, new Color[][]{
        {Color.RED, Color.GREEN},
        {Color.BLUE, Color.YELLOW}
    });
    writeSheet(root.resolve("Idle.png"), 4, 4, new Color[][]{
        {Color.CYAN, Color.MAGENTA}
    });

    ExistingSpriteSheetService service = new ExistingSpriteSheetService();
    ExistingSpriteSheetResult result = service.generate(root, 4, 4);

    assertEquals("FireMage-merged", result.outputName());
    assertEquals(2, result.columns());
    assertEquals(3, result.rows());
    assertEquals(6, result.frameCount());
    assertTrue(Files.exists(result.outputPath()));
    assertTrue(Files.exists(result.mappingPath()));
    assertTrue(result.excludedSheets().isEmpty());

    BufferedImage merged = ImageIO.read(result.outputPath().toFile());
    assertEquals(8, merged.getWidth());
    assertEquals(12, merged.getHeight());
    assertCellColor(merged, 0, 0, 4, 4, Color.RED);
    assertCellColor(merged, 1, 0, 4, 4, Color.GREEN);
    assertCellColor(merged, 0, 1, 4, 4, Color.BLUE);
    assertCellColor(merged, 1, 1, 4, 4, Color.YELLOW);
    assertCellColor(merged, 0, 2, 4, 4, Color.CYAN);
    assertCellColor(merged, 1, 2, 4, 4, Color.MAGENTA);

    List<String> lines = Files.readAllLines(result.mappingPath());
    assertIterableEquals(List.of(
        "grid: 3x2",
        "Attack/right -> 0, 1",
        "Attack/down_se -> 2, 3",
        "Idle/right -> 4, 5"
    ), lines);
  }

  @Test
  void skipsSheetsThatDoNotFitTheSelectedGrid() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("mixed"));
    writeRawPng(root.resolve("broken.png"), 5, 4, Color.BLACK);
    writeSheet(root.resolve("valid.png"), 4, 4, new Color[][]{
        {Color.ORANGE, Color.PINK}
    });

    ExistingSpriteSheetService service = new ExistingSpriteSheetService();
    ExistingSpriteSheetResult result = service.generate(root, 4, 4);

    assertEquals(List.of(root.resolve("broken.png")), result.excludedSheets());
    assertEquals(2, result.columns());
    assertEquals(1, result.rows());
    assertEquals(2, result.frameCount());
  }

  @Test
  void packsSheetsIntoCompactAtlasWithinTextureLimit() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("packed"));
    writeSheet(root.resolve("01.png"), 4, 4, new Color[][]{
        {Color.RED, Color.RED, Color.RED},
        {Color.GREEN, Color.GREEN, Color.GREEN}
    });
    writeSheet(root.resolve("02.png"), 4, 4, new Color[][]{
        {Color.BLUE, Color.BLUE, Color.BLUE},
        {Color.YELLOW, Color.YELLOW, Color.YELLOW}
    });
    writeSheet(root.resolve("03.png"), 4, 4, new Color[][]{
        {Color.CYAN, Color.CYAN, Color.CYAN},
        {Color.MAGENTA, Color.MAGENTA, Color.MAGENTA}
    });
    writeSheet(root.resolve("04.png"), 4, 4, new Color[][]{
        {Color.ORANGE, Color.ORANGE, Color.ORANGE},
        {Color.PINK, Color.PINK, Color.PINK}
    });

    ExistingSpriteSheetService service = new ExistingSpriteSheetService(
        new SpriteSheetWriter(),
        new SpriteSheetMetadataWriter(),
        24
    );
    ExistingSpriteSheetResult result = service.generate(root, 4, 4);

    assertEquals(6, result.columns());
    assertEquals(4, result.rows());
    assertEquals(24, result.imageWidth());
    assertEquals(16, result.imageHeight());

    BufferedImage merged = ImageIO.read(result.outputPath().toFile());
    assertNotNull(merged);
    assertCellColor(merged, 0, 0, 4, 4, Color.RED);
    assertCellColor(merged, 3, 0, 4, 4, Color.BLUE);
    assertCellColor(merged, 0, 2, 4, 4, Color.CYAN);
    assertCellColor(merged, 3, 2, 4, 4, Color.ORANGE);

    List<String> lines = Files.readAllLines(result.mappingPath());
    assertIterableEquals(List.of(
        "grid: 4x6",
        "01/right -> 0, 1, 2",
        "01/down_se -> 6, 7, 8",
        "02/right -> 3, 4, 5",
        "02/down_se -> 9, 10, 11",
        "03/right -> 12, 13, 14",
        "03/down_se -> 18, 19, 20",
        "04/right -> 15, 16, 17",
        "04/down_se -> 21, 22, 23"
    ), lines);
  }

  @Test
  void ignoresPreviousMergedOutputWhenCollectingSources() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("FireMage"));
    writeSheet(root.resolve("Attack.png"), 4, 4, new Color[][]{
        {Color.RED}
    });
    writeSheet(root.resolve("FireMage-merged.png"), 4, 4, new Color[][]{
        {Color.BLACK},
        {Color.WHITE}
    });

    ExistingSpriteSheetService service = new ExistingSpriteSheetService();
    ExistingSpriteSheetResult result = service.generate(root, 4, 4);

    assertEquals(1, result.columns());
    assertEquals(1, result.rows());
    assertEquals(1, result.frameCount());
    BufferedImage merged = ImageIO.read(result.outputPath().toFile());
    assertNotNull(merged);
    assertCellColor(merged, 0, 0, 4, 4, Color.RED);

    List<String> lines = Files.readAllLines(result.mappingPath());
    assertIterableEquals(List.of(
        "grid: 1x1",
        "Attack/right -> 0"
    ), lines);
  }

  @Test
  void failsWhenNoSheetMatchesTheGrid() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("invalid-only"));
    writeRawPng(root.resolve("broken.png"), 5, 7, Color.BLACK);

    ExistingSpriteSheetService service = new ExistingSpriteSheetService();

    IllegalStateException error = assertThrows(
        IllegalStateException.class,
        () -> service.generate(root, 4, 4)
    );
    assertTrue(error.getMessage().contains("No sprite sheets divisible by 4x4"));
  }

  @Test
  void failsWhenSavedOutputIsNotPixelIdentical() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("corrupt"));
    writeSheet(root.resolve("Attack.png"), 4, 4, new Color[][]{
        {Color.RED, Color.GREEN}
    });

    SpriteSheetWriter corruptingWriter = new SpriteSheetWriter() {
      @Override
      public void write(BufferedImage image, Path outputPath) throws IOException {
        BufferedImage corrupted = new BufferedImage(
            image.getWidth(),
            image.getHeight(),
            BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D graphics = corrupted.createGraphics();
        try {
          graphics.drawImage(image, 0, 0, null);
        } finally {
          graphics.dispose();
        }
        corrupted.setRGB(0, 0, new Color(1, 2, 3, 255).getRGB());
        super.write(corrupted, outputPath);
      }
    };
    ExistingSpriteSheetService service = new ExistingSpriteSheetService(
        corruptingWriter,
        new SpriteSheetMetadataWriter()
    );

    IOException error = assertThrows(IOException.class, () -> service.generate(root, 4, 4));
    assertTrue(error.getMessage().contains("Saved sprite sheet verification failed"));
  }

  @Test
  void ignoresRgbDifferencesInsideFullyTransparentPixels() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("transparent-rgb"));
    BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
    image.setRGB(0, 0, 0x00010101);
    image.setRGB(1, 0, new Color(255, 0, 0, 255).getRGB());
    ImageIO.write(image, "png", root.resolve("Attack.png").toFile());

    ExistingSpriteSheetService service = new ExistingSpriteSheetService();
    ExistingSpriteSheetResult result = service.generate(root, 4, 4);

    assertEquals(1, result.columns());
    assertEquals(1, result.rows());
    assertTrue(Files.exists(result.outputPath()));
    assertTrue(Files.exists(result.mappingPath()));
  }

  @Test
  void usesPerSheetDirectionOrderOverridesWhenPresent() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("overrides"));
    writeSheet(root.resolve("Idle.png"), 4, 4, new Color[][]{
        {Color.RED},
        {Color.GREEN},
        {Color.BLUE},
        {Color.YELLOW},
        {Color.CYAN}
    });
    Files.writeString(
        root.resolve(ExistingSpriteSheetService.DIRECTION_ORDER_FILE),
        """
        default=right,down_se,down,down_sw,left,up_nw,up,up_ne
        Idle=right,down_se,down,left,down_sw,up_nw,up,up_ne
        """
    );

    ExistingSpriteSheetService service = new ExistingSpriteSheetService();
    ExistingSpriteSheetResult result = service.generate(root, 4, 4);

    List<String> lines = Files.readAllLines(result.mappingPath());
    assertEquals("Idle/right -> 0", lines.get(1));
    assertEquals("Idle/down_se -> 1", lines.get(2));
    assertEquals("Idle/down -> 2", lines.get(3));
    assertEquals("Idle/left -> 3", lines.get(4));
    assertEquals("Idle/down_sw -> 4", lines.get(5));
  }

  @Test
  void fallsBackToGenericRowNamesAfterKnownDirections() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("overflow"));
    writeSheet(root.resolve("Attack.png"), 4, 4, new Color[][]{
        {Color.RED},
        {Color.GREEN},
        {Color.BLUE},
        {Color.YELLOW},
        {Color.CYAN},
        {Color.MAGENTA},
        {Color.ORANGE},
        {Color.PINK},
        {Color.GRAY}
    });

    ExistingSpriteSheetService service = new ExistingSpriteSheetService();
    ExistingSpriteSheetResult result = service.generate(root, 4, 4);

    List<String> lines = Files.readAllLines(result.mappingPath());
    assertEquals("Attack/up_ne -> 7", lines.get(8));
    assertEquals("Attack/row-09 -> 8", lines.get(9));
  }

  private static void writeSheet(Path path, int cellWidth, int cellHeight, Color[][] cells) throws IOException {
    int rows = cells.length;
    int columns = cells[0].length;
    BufferedImage image = new BufferedImage(
        columns * cellWidth,
        rows * cellHeight,
        BufferedImage.TYPE_INT_ARGB
    );
    Graphics2D graphics = image.createGraphics();
    try {
      for (int row = 0; row < rows; row++) {
        for (int col = 0; col < columns; col++) {
          graphics.setColor(cells[row][col]);
          graphics.fillRect(col * cellWidth, row * cellHeight, cellWidth, cellHeight);
        }
      }
    } finally {
      graphics.dispose();
    }
    ImageIO.write(image, "png", path.toFile());
  }

  private static void writeRawPng(Path path, int width, int height, Color color) throws IOException {
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
