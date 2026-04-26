package pl.spritesheetgluer.sprite;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record LooseFrameSpriteSheetBatchResult(
    List<LooseFrameSpriteSheetResult> sheets,
    List<Path> excludedFrames,
    List<Path> unmatchedPrefixFrames,
    Map<String, Integer> detectedFrameSizes
) {
  public LooseFrameSpriteSheetBatchResult {
    Objects.requireNonNull(sheets, "sheets");
    Objects.requireNonNull(excludedFrames, "excludedFrames");
    Objects.requireNonNull(unmatchedPrefixFrames, "unmatchedPrefixFrames");
    Objects.requireNonNull(detectedFrameSizes, "detectedFrameSizes");
    sheets = List.copyOf(sheets);
    excludedFrames = List.copyOf(excludedFrames);
    unmatchedPrefixFrames = List.copyOf(unmatchedPrefixFrames);
    detectedFrameSizes = Map.copyOf(detectedFrameSizes);
  }
}
