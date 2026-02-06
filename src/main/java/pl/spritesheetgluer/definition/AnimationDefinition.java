package pl.spritesheetgluer.definition;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record AnimationDefinition(String name, Path root, List<DirectionDefinition> directions) {
  public AnimationDefinition {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(root, "root");
    Objects.requireNonNull(directions, "directions");
  }
}
