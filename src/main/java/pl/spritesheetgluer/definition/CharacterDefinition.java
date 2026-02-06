package pl.spritesheetgluer.definition;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record CharacterDefinition(String name, Path root, List<AnimationDefinition> animations) {
  public CharacterDefinition {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(root, "root");
    Objects.requireNonNull(animations, "animations");
  }
}
