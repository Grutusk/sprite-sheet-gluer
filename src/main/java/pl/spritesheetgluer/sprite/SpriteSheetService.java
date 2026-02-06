package pl.spritesheetgluer.sprite;

import pl.spritesheetgluer.definition.CharacterDefinition;
import pl.spritesheetgluer.definition.DirectionDefinition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SpriteSheetService {

  private final SpriteSheetScanner scanner;
  private final SpriteSheetComposer composer;
  private final SpriteSheetWriter writer;
  private final SpriteSheetMetadataWriter metadataWriter;

  public SpriteSheetService() {
    this(
        new SpriteSheetScanner(),
        new SpriteSheetComposer(),
        new SpriteSheetWriter(),
        new SpriteSheetMetadataWriter()
    );
  }

  public SpriteSheetService(
      SpriteSheetScanner scanner,
      SpriteSheetComposer composer,
      SpriteSheetWriter writer,
      SpriteSheetMetadataWriter metadataWriter
  ) {
    this.scanner = Objects.requireNonNull(scanner, "scanner");
    this.composer = Objects.requireNonNull(composer, "composer");
    this.writer = Objects.requireNonNull(writer, "writer");
    this.metadataWriter = Objects.requireNonNull(metadataWriter, "metadataWriter");
  }

  public List<SpriteSheetResult> generate(Path root) throws IOException {
    Objects.requireNonNull(root, "root");

    List<CharacterDefinition> characters = scanner.scan(root);
    if (characters.isEmpty()) {
      throw new IllegalStateException("No character folders found under: " + root);
    }

    List<SpriteSheetResult> results = new ArrayList<>();
    for (CharacterDefinition character : characters) {
      SpriteSheetRender render = composer.compose(character);
      String outputFileName = character.name() + ".png";
      Path outputPath = character.root().resolve(outputFileName);
      writer.write(render.image(), outputPath);
      Path mappingPath = character.root().resolve(character.name() + ".frames.txt");
      metadataWriter.write(mappingPath, buildMapping(character, render.rows(), render.columns()));
      results.add(new SpriteSheetResult(
          character.name(),
          outputPath,
          mappingPath,
          render.columns(),
          render.rows(),
          render.frameCount(),
          render.cellWidth(),
          render.cellHeight()
      ));
    }
    return results;
  }

  /**
   * Builds row-major frame indices for each animation folder so Godot can map
   * animation names to the generated sprite sheet grid. The first line stores
   * the grid size as rows x columns.
   */
  private List<String> buildMapping(CharacterDefinition character, int rows, int columns) {
    List<DirectionDefinition> directions = character.animations().stream()
        .flatMap(animation -> animation.directions().stream())
        .toList();
    List<String> lines = new ArrayList<>();
    lines.add("grid: " + rows + "x" + columns);
    for (int row = 0; row < directions.size(); row++) {
      DirectionDefinition direction = directions.get(row);
      String name = toAnimationName(character, direction);
      List<String> indices = new ArrayList<>();
      for (int col = 0; col < direction.frames().size(); col++) {
        indices.add(String.valueOf(row * columns + col));
      }
      lines.add(name + " -> " + String.join(", ", indices));
    }
    return lines;
  }

  /**
   * Uses the animation folder path relative to the character root as the name,
   * normalized to forward slashes for stability across OSes.
   */
  private String toAnimationName(CharacterDefinition character, DirectionDefinition direction) {
    String name = character.root().relativize(direction.root()).toString();
    if (name.isBlank()) {
      return direction.name();
    }
    return name.replace('\\', '/');
  }
}
