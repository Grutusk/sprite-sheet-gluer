package pl.spritesheetgluer.sprite;

import pl.spritesheetgluer.definition.AnimationDefinition;
import pl.spritesheetgluer.definition.CharacterDefinition;
import pl.spritesheetgluer.definition.DirectionDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class SpriteSheetScanner {
  private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("png", "jpg", "jpeg");
  private static final Comparator<Path> NAME_COMPARATOR =
      Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT));

  public List<CharacterDefinition> scan(Path root) throws IOException {
    if (root == null) {
      throw new IllegalArgumentException("Root path must not be null.");
    }
    if (!Files.isDirectory(root)) {
      throw new IllegalArgumentException("Root path must be a directory: " + root);
    }

    if (isCharacterRoot(root)) {
      return List.of(scanCharacter(root));
    }

    List<CharacterDefinition> characters = new ArrayList<>();
    for (Path candidate : listSortedDirectories(root)) {
      if (isCharacterRoot(candidate)) {
        characters.add(scanCharacter(candidate));
      }
    }
    return characters;
  }

  /**
   * Scans a single character root. Animations may store frames directly or
   * in nested direction subfolders.
   */
  private CharacterDefinition scanCharacter(Path root) throws IOException {
    List<AnimationDefinition> animations = new ArrayList<>();
    for (Path animationDir : listSortedDirectories(root)) {
      List<DirectionDefinition> directions = new ArrayList<>();
      processFrames(animationDir, directions);
      for (Path directionDir : listSortedDirectories(animationDir)) {
        processFrames(directionDir, directions);
      }
      if (!directions.isEmpty()) {
        animations.add(new AnimationDefinition(animationDir.getFileName().toString(), animationDir, directions));
      }
    }

    if (animations.isEmpty()) {
      throw new IllegalStateException("No animation frames found under: " + root);
    }

    return new CharacterDefinition(root.getFileName().toString(), root, animations);
  }

  private void processFrames(Path animationDir, List<DirectionDefinition> directions) throws IOException {
    List<Path> directFrames = new ArrayList<>();
    for (Path file : listSortedFiles(animationDir)) {
      if (isImageFile(file)) {
        directFrames.add(file);
      }
    }
    if (!directFrames.isEmpty()) {
      directions.add(new DirectionDefinition(
          animationDir.getFileName().toString(),
          animationDir,
          directFrames
      ));
    }
  }

  private boolean isCharacterRoot(Path root) throws IOException {
    if (!Files.isDirectory(root)) {
      return false;
    }
    for (Path animationDir : listSortedDirectories(root)) {
      if (containsImages(animationDir)) {
        return true;
      }
      for (Path directionDir : listSortedDirectories(animationDir)) {
        if (containsImages(directionDir)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean containsImages(Path dir) throws IOException {
    try (Stream<Path> stream = Files.list(dir)) {
      return stream.anyMatch(this::isImageFile);
    }
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

  private List<Path> listSortedDirectories(Path root) throws IOException {
    try (Stream<Path> stream = Files.list(root)) {
      return stream.filter(Files::isDirectory).sorted(NAME_COMPARATOR).toList();
    }
  }

  private List<Path> listSortedFiles(Path root) throws IOException {
    try (Stream<Path> stream = Files.list(root)) {
      return stream.filter(Files::isRegularFile).sorted(NAME_COMPARATOR).toList();
    }
  }
}
