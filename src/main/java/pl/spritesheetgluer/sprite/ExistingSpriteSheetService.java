package pl.spritesheetgluer.sprite;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public class ExistingSpriteSheetService {
  public static final int GODOT_MAX_TEXTURE_SIZE = 16_384;
  public static final String DIRECTION_ORDER_FILE = "direction-order.txt";
  private static final List<String> DIRECTION_NAMES = List.of(
      "right",
      "down_se",
      "down",
      "down_sw",
      "left",
      "up_nw",
      "up",
      "up_ne"
  );
  private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("png", "jpg", "jpeg");
  private static final Comparator<Path> NAME_COMPARATOR =
      Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT));

  private final SpriteSheetWriter writer;
  private final SpriteSheetMetadataWriter metadataWriter;
  private final int maxTextureSize;

  public ExistingSpriteSheetService() {
    this(new SpriteSheetWriter(), new SpriteSheetMetadataWriter(), GODOT_MAX_TEXTURE_SIZE);
  }

  public ExistingSpriteSheetService(
      SpriteSheetWriter writer,
      SpriteSheetMetadataWriter metadataWriter
  ) {
    this(writer, metadataWriter, GODOT_MAX_TEXTURE_SIZE);
  }

  public ExistingSpriteSheetService(
      SpriteSheetWriter writer,
      SpriteSheetMetadataWriter metadataWriter,
      int maxTextureSize
  ) {
    this.writer = Objects.requireNonNull(writer, "writer");
    this.metadataWriter = Objects.requireNonNull(metadataWriter, "metadataWriter");
    if (maxTextureSize <= 0) {
      throw new IllegalArgumentException("maxTextureSize must be positive: " + maxTextureSize);
    }
    this.maxTextureSize = maxTextureSize;
  }

  public ExistingSpriteSheetResult generate(Path root, int cellWidth, int cellHeight) throws IOException {
    Objects.requireNonNull(root, "root");
    validateCellSize(cellWidth, cellHeight);
    if (!Files.isDirectory(root)) {
      throw new IllegalArgumentException("Root path must be a directory: " + root);
    }

    String outputName = buildOutputName(root);
    Path outputPath = root.resolve(outputName + ".png");
    Path mappingPath = root.resolve(outputName + ".frames.txt");
    List<Path> sourceSheets = listSortedFiles(root).stream()
        .filter(this::isImageFile)
        .filter(path -> !path.equals(outputPath))
        .toList();
    if (sourceSheets.isEmpty()) {
      throw new IllegalStateException("No sprite sheets found under: " + root);
    }

    List<SourceSheet> validSheets = new ArrayList<>();
    List<Path> excludedSheets = new ArrayList<>();
    for (Path sourceSheet : sourceSheets) {
      BufferedImage image = readImage(sourceSheet);
      if (!fitsGrid(image, cellWidth, cellHeight)) {
        excludedSheets.add(sourceSheet);
        continue;
      }

      int columns = image.getWidth() / cellWidth;
      int rows = image.getHeight() / cellHeight;
      validSheets.add(new SourceSheet(sourceSheet, image, columns, rows));
    }

    if (validSheets.isEmpty()) {
      throw new IllegalStateException(
          "No sprite sheets divisible by " + cellWidth + "x" + cellHeight + " found under: " + root
      );
    }
    DirectionOrderConfig directionOrderConfig = loadDirectionOrderConfig(root);

    int maxTextureColumns = maxTextureSize / cellWidth;
    int maxTextureRows = maxTextureSize / cellHeight;
    if (maxTextureColumns <= 0 || maxTextureRows <= 0) {
      throw new IllegalStateException(
          "Cell size " + cellWidth + "x" + cellHeight
              + " exceeds the max texture size of " + maxTextureSize + "x" + maxTextureSize
      );
    }

    for (SourceSheet sourceSheet : validSheets) {
      if (sourceSheet.columns() > maxTextureColumns || sourceSheet.rows() > maxTextureRows) {
        throw new IllegalStateException(
            "Source sheet " + sourceSheet.path().getFileName()
                + " is too large for the max texture size of " + maxTextureSize + "x" + maxTextureSize
                + " at cell size " + cellWidth + "x" + cellHeight
        );
      }
    }

    Layout layout = findBestLayout(validSheets, maxTextureColumns, maxTextureRows, cellWidth, cellHeight);
    if (layout == null) {
      throw new IllegalStateException(
          "Merged sprite sheet cannot fit within " + maxTextureSize + "x" + maxTextureSize
              + " at cell size " + cellWidth + "x" + cellHeight
              + ". Reduce the number of source sheets or split them into multiple folders."
      );
    }

    BufferedImage mergedSheet = new BufferedImage(
        layout.columns() * cellWidth,
        layout.rows() * cellHeight,
        BufferedImage.TYPE_INT_ARGB
    );
    List<MappingEntry> mappingEntries = new ArrayList<>();

    int frameCount = 0;
    Graphics2D graphics = mergedSheet.createGraphics();
    try {
      graphics.setComposite(AlphaComposite.Src);
      graphics.setColor(new Color(0, 0, 0, 0));
      graphics.fillRect(0, 0, mergedSheet.getWidth(), mergedSheet.getHeight());
      graphics.setComposite(AlphaComposite.SrcOver);

      for (Placement placement : layout.placements()) {
        SourceSheet sourceSheet = placement.sourceSheet();
        graphics.drawImage(
            sourceSheet.image(),
            placement.column() * cellWidth,
            placement.row() * cellHeight,
            null
        );
        appendMappingEntries(mappingEntries, placement, layout.columns(), directionOrderConfig);
        frameCount += sourceSheet.columns() * sourceSheet.rows();
      }
    } finally {
      graphics.dispose();
    }

    List<String> mappingLines = buildMappingLines(mappingEntries, layout.rows(), layout.columns());
    writer.write(mergedSheet, outputPath);
    metadataWriter.write(mappingPath, mappingLines);
    verifySavedOutput(outputPath, layout, cellWidth, cellHeight);

    return new ExistingSpriteSheetResult(
        outputName,
        outputPath,
        mappingPath,
        layout.columns(),
        layout.rows(),
        frameCount,
        cellWidth,
        cellHeight,
        excludedSheets
    );
  }

  private void appendMappingEntries(
      List<MappingEntry> mappingEntries,
      Placement placement,
      int atlasColumns,
      DirectionOrderConfig directionOrderConfig
  ) {
    SourceSheet sourceSheet = placement.sourceSheet();
    String sourceName = stripExtension(sourceSheet.path().getFileName().toString());
    for (int row = 0; row < sourceSheet.rows(); row++) {
      List<String> indices = new ArrayList<>();
      int outputRow = placement.row() + row;
      for (int col = 0; col < sourceSheet.columns(); col++) {
        indices.add(String.valueOf(outputRow * atlasColumns + placement.column() + col));
      }
      String rowName = directionNameForRow(sourceName, row, directionOrderConfig);
      mappingEntries.add(new MappingEntry(
          outputRow,
          placement.column(),
          sourceName + "/" + rowName + " -> " + String.join(", ", indices)
      ));
    }
  }

  private List<String> buildMappingLines(List<MappingEntry> mappingEntries, int rows, int columns) {
    List<String> lines = new ArrayList<>();
    lines.add("grid: " + rows + "x" + columns);
    mappingEntries.stream()
        .map(MappingEntry::line)
        .forEach(lines::add);
    return lines;
  }

  private String directionNameForRow(
      String sourceName,
      int row,
      DirectionOrderConfig directionOrderConfig
  ) {
    List<String> directionNames = directionOrderConfig.directionNamesFor(sourceName);
    if (row < directionNames.size()) {
      return directionNames.get(row);
    }
    return String.format(Locale.ROOT, "row-%02d", row + 1);
  }

  private void verifySavedOutput(
      Path outputPath,
      Layout layout,
      int cellWidth,
      int cellHeight
  ) throws IOException {
    BufferedImage savedOutput = readImage(outputPath);
    int expectedWidth = layout.columns() * cellWidth;
    int expectedHeight = layout.rows() * cellHeight;
    if (savedOutput.getWidth() != expectedWidth || savedOutput.getHeight() != expectedHeight) {
      throw new IOException(
          "Saved sprite sheet verification failed: expected "
              + expectedWidth + "x" + expectedHeight
              + " but found " + savedOutput.getWidth() + "x" + savedOutput.getHeight()
      );
    }

    for (Placement placement : layout.placements()) {
      verifySheetPlacement(savedOutput, placement, cellWidth, cellHeight);
    }
  }

  private void verifySheetPlacement(
      BufferedImage savedOutput,
      Placement placement,
      int cellWidth,
      int cellHeight
  ) throws IOException {
    SourceSheet sourceSheet = placement.sourceSheet();
    BufferedImage sourceImage = sourceSheet.image();
    int width = sourceImage.getWidth();
    int height = sourceImage.getHeight();
    int[] sourcePixels = sourceImage.getRGB(0, 0, width, height, null, 0, width);
    int[] savedPixels = savedOutput.getRGB(
        placement.column() * cellWidth,
        placement.row() * cellHeight,
        width,
        height,
        null,
        0,
        width
    );
    for (int index = 0; index < sourcePixels.length; index++) {
      if (!pixelsVisuallyEqual(sourcePixels[index], savedPixels[index])) {
        int x = index % width;
        int y = index / width;
        int frameColumn = x / cellWidth;
        int frameRow = y / cellHeight;
        throw new IOException(
            "Saved sprite sheet verification failed at "
                + stripExtension(sourceSheet.path().getFileName().toString())
                + "/" + directionNameForRow(
                stripExtension(sourceSheet.path().getFileName().toString()),
                frameRow,
                DirectionOrderConfig.defaultConfig()
            )
                + " frame " + (frameColumn + 1)
                + " pixel (" + (x % cellWidth) + ", " + (y % cellHeight) + "): expected "
                + formatArgb(sourcePixels[index]) + " but found " + formatArgb(savedPixels[index])
        );
      }
    }
  }

  private boolean pixelsVisuallyEqual(int sourceArgb, int savedArgb) {
    int sourceAlpha = (sourceArgb >>> 24) & 0xFF;
    int savedAlpha = (savedArgb >>> 24) & 0xFF;
    if (sourceAlpha == 0 && savedAlpha == 0) {
      return true;
    }
    return sourceArgb == savedArgb;
  }

  private String formatArgb(int argb) {
    return String.format(Locale.ROOT, "#%08X", argb);
  }

  private DirectionOrderConfig loadDirectionOrderConfig(Path root) throws IOException {
    Path configPath = root.resolve(DIRECTION_ORDER_FILE);
    if (!Files.exists(configPath)) {
      return DirectionOrderConfig.defaultConfig();
    }

    Map<String, List<String>> overrides = new LinkedHashMap<>();
    List<String> defaultDirections = DIRECTION_NAMES;
    List<String> lines = Files.readAllLines(configPath);
    for (String line : lines) {
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      int separator = trimmed.indexOf('=');
      if (separator < 0) {
        throw new IOException("Invalid direction order line in " + configPath + ": " + line);
      }

      String key = trimmed.substring(0, separator).trim();
      String value = trimmed.substring(separator + 1).trim();
      if (key.isEmpty() || value.isEmpty()) {
        throw new IOException("Invalid direction order line in " + configPath + ": " + line);
      }

      List<String> directions = parseDirectionNames(value, configPath);
      if (key.equalsIgnoreCase("default") || key.equals("*")) {
        defaultDirections = directions;
      } else {
        overrides.put(key, directions);
      }
    }
    return new DirectionOrderConfig(defaultDirections, overrides);
  }

  private List<String> parseDirectionNames(String value, Path configPath) throws IOException {
    List<String> directions = new ArrayList<>();
    for (String part : value.split(",")) {
      String name = part.trim();
      if (!name.isEmpty()) {
        directions.add(name);
      }
    }
    if (directions.isEmpty()) {
      throw new IOException("Empty direction order entry in " + configPath);
    }
    return List.copyOf(directions);
  }

  private Layout findBestLayout(
      List<SourceSheet> sourceSheets,
      int maxTextureColumns,
      int maxTextureRows,
      int cellWidth,
      int cellHeight
  ) {
    int minColumns = sourceSheets.stream()
        .mapToInt(SourceSheet::columns)
        .max()
        .orElse(0);
    int sumColumns = sourceSheets.stream()
        .mapToInt(SourceSheet::columns)
        .sum();
    int maxCandidateColumns = Math.min(maxTextureColumns, sumColumns);
    Layout best = null;
    for (int candidateColumns = minColumns; candidateColumns <= maxCandidateColumns; candidateColumns++) {
      Layout candidate = packShelves(sourceSheets, candidateColumns, maxTextureRows);
      if (candidate == null) {
        continue;
      }
      if (best == null || isBetterLayout(candidate, best, cellWidth, cellHeight)) {
        best = candidate;
      }
    }
    return best;
  }

  private Layout packShelves(List<SourceSheet> sourceSheets, int maxColumns, int maxRows) {
    List<Placement> placements = new ArrayList<>();
    int currentColumn = 0;
    int currentRow = 0;
    int shelfHeight = 0;
    int usedColumns = 0;
    for (SourceSheet sourceSheet : sourceSheets) {
      if (currentColumn > 0 && currentColumn + sourceSheet.columns() > maxColumns) {
        currentRow += shelfHeight;
        currentColumn = 0;
        shelfHeight = 0;
      }
      if (currentRow + sourceSheet.rows() > maxRows) {
        return null;
      }

      placements.add(new Placement(sourceSheet, currentColumn, currentRow));
      currentColumn += sourceSheet.columns();
      shelfHeight = Math.max(shelfHeight, sourceSheet.rows());
      usedColumns = Math.max(usedColumns, currentColumn);
    }

    int usedRows = currentRow + shelfHeight;
    if (usedRows > maxRows) {
      return null;
    }
    return new Layout(List.copyOf(placements), usedColumns, usedRows);
  }

  private boolean isBetterLayout(Layout candidate, Layout currentBest, int cellWidth, int cellHeight) {
    int candidateWidth = candidate.columns() * cellWidth;
    int candidateHeight = candidate.rows() * cellHeight;
    int bestWidth = currentBest.columns() * cellWidth;
    int bestHeight = currentBest.rows() * cellHeight;

    int candidateLongestSide = Math.max(candidateWidth, candidateHeight);
    int bestLongestSide = Math.max(bestWidth, bestHeight);
    if (candidateLongestSide != bestLongestSide) {
      return candidateLongestSide < bestLongestSide;
    }

    long candidateArea = (long) candidateWidth * candidateHeight;
    long bestArea = (long) bestWidth * bestHeight;
    if (candidateArea != bestArea) {
      return candidateArea < bestArea;
    }

    int candidateAspectDelta = Math.abs(candidateWidth - candidateHeight);
    int bestAspectDelta = Math.abs(bestWidth - bestHeight);
    if (candidateAspectDelta != bestAspectDelta) {
      return candidateAspectDelta < bestAspectDelta;
    }

    return candidateWidth < bestWidth;
  }

  private BufferedImage readImage(Path path) throws IOException {
    BufferedImage image = ImageIO.read(path.toFile());
    if (image == null) {
      throw new IOException("Unsupported image format: " + path);
    }
    return image;
  }

  private boolean fitsGrid(BufferedImage image, int cellWidth, int cellHeight) {
    return image.getWidth() >= cellWidth
        && image.getHeight() >= cellHeight
        && image.getWidth() % cellWidth == 0
        && image.getHeight() % cellHeight == 0;
  }

  private boolean isImageFile(Path file) {
    if (!Files.isRegularFile(file)) {
      return false;
    }
    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
    int dot = name.lastIndexOf('.');
    if (dot < 0 || dot == name.length() - 1) {
      return false;
    }
    String extension = name.substring(dot + 1);
    return SUPPORTED_EXTENSIONS.contains(extension);
  }

  private List<Path> listSortedFiles(Path root) throws IOException {
    try (Stream<Path> stream = Files.list(root)) {
      return stream.filter(Files::isRegularFile).sorted(NAME_COMPARATOR).toList();
    }
  }

  private String buildOutputName(Path root) {
    Path fileName = root.getFileName();
    String baseName = fileName == null ? "merged" : fileName.toString();
    return baseName + "-merged";
  }

  private String stripExtension(String fileName) {
    int dot = fileName.lastIndexOf('.');
    if (dot < 0) {
      return fileName;
    }
    return fileName.substring(0, dot);
  }

  private void validateCellSize(int cellWidth, int cellHeight) {
    if (cellWidth <= 0) {
      throw new IllegalArgumentException("Cell width must be positive: " + cellWidth);
    }
    if (cellHeight <= 0) {
      throw new IllegalArgumentException("Cell height must be positive: " + cellHeight);
    }
  }

  private record SourceSheet(Path path, BufferedImage image, int columns, int rows) {
  }

  private record Placement(SourceSheet sourceSheet, int column, int row) {
  }

  private record Layout(List<Placement> placements, int columns, int rows) {
  }

  private record MappingEntry(int outputRow, int outputColumn, String line) {
  }

  private record DirectionOrderConfig(List<String> defaultDirections, Map<String, List<String>> overrides) {
    private DirectionOrderConfig {
      defaultDirections = List.copyOf(defaultDirections);
      overrides = Map.copyOf(overrides);
    }

    private static DirectionOrderConfig defaultConfig() {
      return new DirectionOrderConfig(DIRECTION_NAMES, Map.of());
    }

    private List<String> directionNamesFor(String sourceName) {
      return overrides.getOrDefault(sourceName, defaultDirections);
    }
  }
}
