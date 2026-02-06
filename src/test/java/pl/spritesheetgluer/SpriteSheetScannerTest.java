package pl.spritesheetgluer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.spritesheetgluer.definition.CharacterDefinition;
import pl.spritesheetgluer.sprite.SpriteSheetScanner;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SpriteSheetScannerTest {
  @TempDir
  Path tempDir;

  @Test
  void scansCharacterFolderFromResources() throws Exception {
    Path root = TestResourceHelper.copyResourceDirectory("test", tempDir);

    SpriteSheetScanner scanner = new SpriteSheetScanner();
    List<CharacterDefinition> characters = scanner.scan(root);

    assertEquals(1, characters.size());
    CharacterDefinition character = characters.get(0);
    assertEquals(root.getFileName().toString(), character.name());
    assertFalse(character.animations().isEmpty());
  }
}
