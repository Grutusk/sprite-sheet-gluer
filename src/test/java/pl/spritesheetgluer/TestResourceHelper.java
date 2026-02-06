package pl.spritesheetgluer;

import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

public final class TestResourceHelper {
  private TestResourceHelper() {
  }

  public static Path copyResourceDirectory(String resourceName, Path destinationRoot)
      throws IOException, URISyntaxException {
    URL resource = TestResourceHelper.class.getClassLoader().getResource(resourceName);
    Assumptions.assumeTrue(resource != null, "Missing test resource folder: " + resourceName);

    Path sourceRoot = Path.of(resource.toURI());
    Path targetRoot = destinationRoot.resolve(sourceRoot.getFileName().toString());

    Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        Path targetDir = targetRoot.resolve(sourceRoot.relativize(dir));
        Files.createDirectories(targetDir);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Path targetFile = targetRoot.resolve(sourceRoot.relativize(file));
        Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
        return FileVisitResult.CONTINUE;
      }
    });

    return targetRoot;
  }
}
