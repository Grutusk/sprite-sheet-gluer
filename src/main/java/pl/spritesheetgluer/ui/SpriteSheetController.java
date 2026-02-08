package pl.spritesheetgluer.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import pl.spritesheetgluer.sprite.SpriteSheetResult;
import pl.spritesheetgluer.sprite.SpriteSheetService;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SpriteSheetController {
  private final ObjectProperty<Path> rootPath = new SimpleObjectProperty<>();
  private final BooleanProperty busy = new SimpleBooleanProperty(false);
  private final SpriteSheetService spriteSheetService = new SpriteSheetService();
  @FXML
  private TextField rootField;
  @FXML
  private TextArea logArea;
  @FXML
  private Button generateButton;

  @FXML
  private void initialize() {
    rootPath.addListener((observable, oldValue, newValue) -> {
      if (newValue == null) {
        rootField.clear();
      } else {
        rootField.setText(newValue.toString());
      }
    });

    generateButton.disableProperty().bind(
        rootPath.isNull().or(busy)
    );
  }

  @FXML
  private void onBrowse() {
    DirectoryChooser chooser = new DirectoryChooser();
    chooser.setTitle("Select Root Folder");
    Path current = rootPath.get();
    if (current != null && current.toFile().isDirectory()) {
      chooser.setInitialDirectory(current.toFile());
    }
    File selected = chooser.showDialog(rootField.getScene().getWindow());
    if (selected != null) {
      rootPath.set(selected.toPath());
    }
  }

  @FXML
  private void onGenerate() {
    Path root = rootPath.get();
    if (root == null) {
      appendLog("Select a root folder first.");
      return;
    }

    Task<List<SpriteSheetResult>> task = new Task<>() {
      @Override
      protected List<SpriteSheetResult> call() throws Exception {
        return spriteSheetService.generate(root);
      }
    };

    busy.set(true);
    appendLog("Generating sprite sheets for " + root + " (no scaling)...");

    task.setOnSucceeded(event -> {
      busy.set(false);
      List<SpriteSheetResult> results = task.getValue();
      appendLog("Done. Generated " + results.size() + " sprite sheet(s).");
      List<String> skippedLines = new ArrayList<>();
      int skippedCount = 0;
      for (SpriteSheetResult result : results) {
        appendLog(
            "Saved " + result.characterName()
                + " (cell: " + result.cellWidth() + "x" + result.cellHeight()
                + ", grid: " + result.columns() + "x" + result.rows()
                + ", frames: " + result.frameCount() + ") -> " + result.outputPath()
                + " (map: " + result.mappingPath() + ")"
        );
        List<Path> excludedFrames = result.excludedFrames();
        if (!excludedFrames.isEmpty()) {
          Path base = result.outputPath().getParent();
          for (Path frame : excludedFrames) {
            String name = base == null ? frame.toString() : base.relativize(frame).toString();
            skippedLines.add(result.characterName() + ": " + name);
          }
          skippedCount += excludedFrames.size();
        }
      }
      if (!skippedLines.isEmpty()) {
        appendLog("Warning: skipped " + skippedCount + " image(s) due to size mismatch.");
        StringBuilder message = new StringBuilder(
            "These images were not included because their sizes did not match:"
        );
        for (String line : skippedLines) {
          message.append(System.lineSeparator()).append(line);
        }
        showWarning(message.toString());
      }
    });

    task.setOnFailed(event -> {
      busy.set(false);
      Throwable error = task.getException();
      String message = error == null ? "Unknown error." : error.getMessage();
      appendLog("Failed: " + message);
      showError(message);
    });

    Thread worker = new Thread(task, "sprite-sheet-generator");
    worker.setDaemon(true);
    worker.start();
  }

  private void appendLog(String message) {
    if (logArea.getText().isEmpty()) {
      logArea.setText(message);
    } else {
      logArea.appendText(System.lineSeparator() + message);
    }
  }

  private void showError(String message) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle("Sprite sheet generation failed");
    alert.setHeaderText(null);
    alert.setContentText(message == null ? "Unknown error." : message);
    alert.showAndWait();
  }

  private void showWarning(String message) {
    Alert alert = new Alert(Alert.AlertType.WARNING);
    alert.setTitle("Sprite sheet generation warning");
    alert.setHeaderText(null);
    alert.setContentText(message == null ? "Warning." : message);
    alert.showAndWait();
  }
}
