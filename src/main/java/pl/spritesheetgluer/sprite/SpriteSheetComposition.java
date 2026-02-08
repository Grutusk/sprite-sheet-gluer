package pl.spritesheetgluer.sprite;

import pl.spritesheetgluer.definition.DirectionDefinition;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record SpriteSheetComposition(
    SpriteSheetRender render,
    List<DirectionDefinition> directions,
    List<Path> excludedFrames
) {
  public SpriteSheetComposition {
    Objects.requireNonNull(render, "render");
    Objects.requireNonNull(directions, "directions");
    Objects.requireNonNull(excludedFrames, "excludedFrames");
    directions = List.copyOf(directions);
    excludedFrames = List.copyOf(excludedFrames);
  }
}
