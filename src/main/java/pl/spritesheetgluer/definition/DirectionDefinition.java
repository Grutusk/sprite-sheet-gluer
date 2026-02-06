package pl.spritesheetgluer.definition;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record DirectionDefinition(String name, Path root, List<Path> frames) {
  public DirectionDefinition {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(root, "root");
    Objects.requireNonNull(frames, "frames");
  }
}
