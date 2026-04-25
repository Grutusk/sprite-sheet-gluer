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

public class LooseFrameSpriteSheetService {
  public static final int GODOT_MAX_TEXTURE_SIZE = ExistingSpriteSheetService.GODOT_MAX_TEXTURE_SIZE;
  private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("png", "jpg", "jpeg");
  private static final Comparator<Path> NAME_COMPARATOR =
      Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT));
  private static final String OUTPUT_SUFFIX = "-sheet";

  private final SpriteSheetWriter writer;
  private final SpriteSheetMetadataWriter metadataWriter;
  private final int maxTextureSize;

  public LooseFrameSpriteSheetService() {
    this(new SpriteSheetWriter(), new SpriteSheetMetadataWriter(), GODOT_MAX_TEXTURE_SIZE);
  }

  public LooseFrameSpriteSheetService(
      SpriteSheetWriter writer,
      SpriteSheetMetadataWriter metadataWriter
  ) {
    this(writer, metadataWriter, GODOT_MAX_TEXTURE_SIZE);
  }

  public LooseFrameSpriteSheetService(
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

  public LooseFrameSpriteSheetBatchResult generate(Path root) throws IOException {
    return generate(root, null);
  }

  public LooseFrameSpriteSheetBatchResult generate(Path root, int cellWidth, int cellHeight) throws IOException {
    validateCellSize(cellWidth, cellHeight);
    return generate(root, new SizeKey(cellWidth, cellHeight));
  }

  private LooseFrameSpriteSheetBatchResult generate(Path root, SizeKey expectedSize) throws IOException {
    Objects.requireNonNull(root, "root");
    if (!Files.isDirectory(root)) {
      throw new IllegalArgumentException("Root path must be a directory: " + root);
    }

    List<Path> sourceFrames = listSortedFiles(root).stream()
        .filter(this::isImageFile)
        .filter(this::isSourceFrame)
        .toList();
    if (sourceFrames.isEmpty()) {
      throw new IllegalStateException("No loose frame images found under: " + root);
    }

    Map<String, PrefixGroup> groups = groupFramesByPrefix(sourceFrames);
    List<LooseFrameSpriteSheetResult> results = new ArrayList<>();
    List<Path> excludedFrames = new ArrayList<>();
    Map<String, Integer> detectedFrameSizes = new LinkedHashMap<>();
    for (PrefixGroup group : groups.values()) {
      GroupProcessingResult groupResult = processGroup(root, group, expectedSize);
      results.addAll(groupResult.results());
      excludedFrames.addAll(groupResult.excludedFrames());
      mergeCounts(detectedFrameSizes, groupResult.detectedFrameSizes());
    }

    if (results.isEmpty()) {
      if (expectedSize != null) {
        throw new IllegalStateException(
            "No frames matching " + expectedSize.width() + "x" + expectedSize.height()
                + " found under: " + root
        );
      }
      throw new IllegalStateException("No sprite sheets could be generated under: " + root);
    }

    return new LooseFrameSpriteSheetBatchResult(results, excludedFrames, detectedFrameSizes);
  }

  private GroupProcessingResult processGroup(Path root, PrefixGroup group, SizeKey expectedSize) throws IOException {
    Map<SizeKey, Integer> sizeCounts = new LinkedHashMap<>();
    List<FrameCandidate> candidates = new ArrayList<>();
    for (Path framePath : group.frames()) {
      BufferedImage image = readImage(framePath);
      SizeKey size = new SizeKey(image.getWidth(), image.getHeight());
      sizeCounts.merge(size, 1, Integer::sum);
      candidates.add(new FrameCandidate(framePath, image, size));
    }

    SizeKey targetSize = expectedSize == null ? selectTargetSize(sizeCounts) : expectedSize;
    if (targetSize == null) {
      throw new IllegalStateException("No readable frames found for prefix: " + group.prefix());
    }

    List<FrameCandidate> includedFrames = new ArrayList<>();
    List<Path> excludedFrames = new ArrayList<>();
    for (FrameCandidate candidate : candidates) {
      if (candidate.size().equals(targetSize)) {
        includedFrames.add(candidate);
      } else {
        excludedFrames.add(candidate.path());
      }
    }

    int cellWidth = targetSize.width();
    int cellHeight = targetSize.height();
    int maxColumns = maxTextureSize / cellWidth;
    int maxRows = maxTextureSize / cellHeight;
    if (maxColumns <= 0 || maxRows <= 0) {
      throw new IllegalStateException(
          "Prefix " + group.prefix()
              + " uses frame size " + cellWidth + "x" + cellHeight
              + ", which exceeds the max texture size of "
              + maxTextureSize + "x" + maxTextureSize
      );
    }

    long maxFramesPerSheet = (long) maxColumns * maxRows;
    int totalSheets = (int) ((includedFrames.size() + maxFramesPerSheet - 1) / maxFramesPerSheet);
    List<LooseFrameSpriteSheetResult> results = new ArrayList<>();
    for (int sheetNumber = 0; sheetNumber < totalSheets; sheetNumber++) {
      int fromIndex = (int) Math.min((long) sheetNumber * maxFramesPerSheet, includedFrames.size());
      int toIndex = (int) Math.min((long) fromIndex + maxFramesPerSheet, includedFrames.size());
      List<FrameCandidate> sheetFrames = includedFrames.subList(fromIndex, toIndex);
      results.add(writeSheet(root, group.prefix(), sheetNumber + 1, totalSheets, sheetFrames, cellWidth, cellHeight));
    }

    return new GroupProcessingResult(results, excludedFrames, formatSizeCounts(sizeCounts));
  }

  private LooseFrameSpriteSheetResult writeSheet(
      Path root,
      String prefix,
      int sheetIndex,
      int totalSheets,
      List<FrameCandidate> frames,
      int cellWidth,
      int cellHeight
  ) throws IOException {
    Layout layout = findBestLayout(frames.size(), cellWidth, cellHeight);
    if (layout == null) {
      throw new IllegalStateException(
          "Prefix " + prefix
              + " cannot fit within " + maxTextureSize + "x" + maxTextureSize
              + " at frame size " + cellWidth + "x" + cellHeight
      );
    }

    BufferedImage spriteSheet = new BufferedImage(
        layout.columns() * cellWidth,
        layout.rows() * cellHeight,
        BufferedImage.TYPE_INT_ARGB
    );
    Graphics2D graphics = spriteSheet.createGraphics();
    try {
      graphics.setComposite(AlphaComposite.Src);
      graphics.setColor(new Color(0, 0, 0, 0));
      graphics.fillRect(0, 0, spriteSheet.getWidth(), spriteSheet.getHeight());
      graphics.setComposite(AlphaComposite.SrcOver);

      for (int index = 0; index < frames.size(); index++) {
        FrameCandidate frame = frames.get(index);
        int column = index % layout.columns();
        int row = index / layout.columns();
        graphics.drawImage(frame.image(), column * cellWidth, row * cellHeight, null);
      }
    } finally {
      graphics.dispose();
    }

    String outputName = buildOutputName(prefix, sheetIndex, totalSheets);
    Path outputPath = root.resolve(outputName + ".png");
    Path mappingPath = root.resolve(outputName + ".frames.txt");
    writer.write(spriteSheet, outputPath);
    metadataWriter.write(mappingPath, buildMappingLines(frames, layout.rows(), layout.columns()));

    return new LooseFrameSpriteSheetResult(
        prefix,
        outputName,
        outputPath,
        mappingPath,
        layout.columns(),
        layout.rows(),
        frames.size(),
        cellWidth,
        cellHeight,
        sheetIndex,
        totalSheets
    );
  }

  private Map<String, PrefixGroup> groupFramesByPrefix(List<Path> sourceFrames) {
    Map<String, PrefixGroupBuilder> grouped = new LinkedHashMap<>();
    for (Path sourceFrame : sourceFrames) {
      String fileName = sourceFrame.getFileName().toString();
      String prefix = inferPrefix(fileName);
      String key = prefix.toLowerCase(Locale.ROOT);
      PrefixGroupBuilder builder = grouped.computeIfAbsent(
          key,
          unused -> new PrefixGroupBuilder(prefix, new ArrayList<>())
      );
      builder.frames().add(sourceFrame);
    }

    Map<String, PrefixGroup> groups = new LinkedHashMap<>();
    for (Map.Entry<String, PrefixGroupBuilder> entry : grouped.entrySet()) {
      PrefixGroupBuilder builder = entry.getValue();
      groups.put(entry.getKey(), new PrefixGroup(builder.prefix(), List.copyOf(builder.frames())));
    }
    return groups;
  }

  private String inferPrefix(String fileName) {
    String baseName = stripExtension(fileName).trim();
    if (baseName.isEmpty()) {
      return "sheet";
    }

    int separatorIndex = firstSeparatorIndex(baseName);
    String prefixCandidate;
    if (separatorIndex >= 0) {
      prefixCandidate = baseName.substring(0, separatorIndex);
    } else {
      int digitIndex = firstDigitIndex(baseName);
      if (digitIndex >= 0) {
        prefixCandidate = baseName.substring(0, digitIndex);
        if (prefixCandidate.length() > 1
            && Character.isUpperCase(prefixCandidate.charAt(prefixCandidate.length() - 1))
            && Character.isLowerCase(prefixCandidate.charAt(prefixCandidate.length() - 2))) {
          prefixCandidate = prefixCandidate.substring(0, prefixCandidate.length() - 1);
        }
      } else {
        prefixCandidate = baseName;
      }
    }

    String cleaned = trimGroupingCharacters(prefixCandidate);
    return cleaned.isBlank() ? baseName : cleaned;
  }

  private int firstSeparatorIndex(String baseName) {
    for (int index = 0; index < baseName.length(); index++) {
      char character = baseName.charAt(index);
      if (Character.isWhitespace(character) || character == '_' || character == '-') {
        return index;
      }
    }
    return -1;
  }

  private int firstDigitIndex(String baseName) {
    for (int index = 0; index < baseName.length(); index++) {
      if (Character.isDigit(baseName.charAt(index))) {
        return index;
      }
    }
    return -1;
  }

  private String trimGroupingCharacters(String value) {
    int start = 0;
    int end = value.length();
    while (start < end && isGroupingTrimCharacter(value.charAt(start))) {
      start++;
    }
    while (end > start && isGroupingTrimCharacter(value.charAt(end - 1))) {
      end--;
    }
    return value.substring(start, end).trim();
  }

  private boolean isGroupingTrimCharacter(char character) {
    return Character.isWhitespace(character) || character == '_' || character == '-';
  }

  private List<String> buildMappingLines(List<FrameCandidate> frames, int rows, int columns) {
    List<String> lines = new ArrayList<>();
    lines.add("grid: " + rows + "x" + columns);
    for (int index = 0; index < frames.size(); index++) {
      String name = stripExtension(frames.get(index).path().getFileName().toString());
      lines.add(name + " -> " + index);
    }
    return lines;
  }

  private Layout findBestLayout(int frameCount, int cellWidth, int cellHeight) {
    int maxColumns = maxTextureSize / cellWidth;
    int maxRows = maxTextureSize / cellHeight;
    int minColumns = Math.max(1, (int) Math.ceil((double) frameCount / maxRows));
    int maxCandidateColumns = Math.min(frameCount, maxColumns);

    Layout best = null;
    for (int columns = minColumns; columns <= maxCandidateColumns; columns++) {
      int rows = (int) Math.ceil((double) frameCount / columns);
      if (rows > maxRows) {
        continue;
      }
      Layout candidate = new Layout(columns, rows);
      if (best == null || isBetterLayout(candidate, best, cellWidth, cellHeight)) {
        best = candidate;
      }
    }
    return best;
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

  private boolean isSourceFrame(Path file) {
    String baseName = stripExtension(file.getFileName().toString());
    return !baseName.toLowerCase(Locale.ROOT).matches(".*" + OUTPUT_SUFFIX + "(?:-\\d+)?");
  }

  private void validateCellSize(int cellWidth, int cellHeight) {
    if (cellWidth <= 0) {
      throw new IllegalArgumentException("Cell width must be positive: " + cellWidth);
    }
    if (cellHeight <= 0) {
      throw new IllegalArgumentException("Cell height must be positive: " + cellHeight);
    }
  }

  private List<Path> listSortedFiles(Path root) throws IOException {
    try (Stream<Path> stream = Files.list(root)) {
      return stream.filter(Files::isRegularFile).sorted(NAME_COMPARATOR).toList();
    }
  }

  private String buildOutputName(String prefix, int sheetIndex, int totalSheets) {
    String base = prefix + OUTPUT_SUFFIX;
    if (totalSheets == 1) {
      return base;
    }
    return String.format(Locale.ROOT, "%s-%02d", base, sheetIndex);
  }

  private String stripExtension(String fileName) {
    int dot = fileName.lastIndexOf('.');
    if (dot < 0) {
      return fileName;
    }
    return fileName.substring(0, dot);
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

  private Map<String, Integer> formatSizeCounts(Map<SizeKey, Integer> sizeCounts) {
    Map<String, Integer> formatted = new LinkedHashMap<>();
    for (Map.Entry<SizeKey, Integer> entry : sizeCounts.entrySet()) {
      formatted.put(formatSize(entry.getKey()), entry.getValue());
    }
    return formatted;
  }

  private void mergeCounts(Map<String, Integer> target, Map<String, Integer> source) {
    for (Map.Entry<String, Integer> entry : source.entrySet()) {
      target.merge(entry.getKey(), entry.getValue(), Integer::sum);
    }
  }

  private String formatSize(SizeKey size) {
    return size.width() + "x" + size.height();
  }

  private record SizeKey(int width, int height) {
  }

  private record FrameCandidate(Path path, BufferedImage image, SizeKey size) {
  }

  private record PrefixGroup(String prefix, List<Path> frames) {
  }

  private record PrefixGroupBuilder(String prefix, List<Path> frames) {
  }

  private record GroupProcessingResult(
      List<LooseFrameSpriteSheetResult> results,
      List<Path> excludedFrames,
      Map<String, Integer> detectedFrameSizes
  ) {
  }

  private record Layout(int columns, int rows) {
  }
}
