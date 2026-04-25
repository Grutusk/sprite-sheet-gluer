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
import pl.spritesheetgluer.sprite.ExistingSpriteSheetResult;
import pl.spritesheetgluer.sprite.ExistingSpriteSheetService;
import pl.spritesheetgluer.sprite.LooseFrameSpriteSheetBatchResult;
import pl.spritesheetgluer.sprite.LooseFrameSpriteSheetResult;
import pl.spritesheetgluer.sprite.LooseFrameSpriteSheetService;
import pl.spritesheetgluer.sprite.SpriteSheetResult;
import pl.spritesheetgluer.sprite.SpriteSheetService;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SpriteSheetController {
  private final ObjectProperty<Path> rootPath = new SimpleObjectProperty<>();
  private final ObjectProperty<Path> looseRootPath = new SimpleObjectProperty<>();
  private final ObjectProperty<Path> mergeRootPath = new SimpleObjectProperty<>();
  private final BooleanProperty busy = new SimpleBooleanProperty(false);
  private final BooleanProperty looseBusy = new SimpleBooleanProperty(false);
  private final BooleanProperty mergeBusy = new SimpleBooleanProperty(false);
  private final SpriteSheetService spriteSheetService = new SpriteSheetService();
  private final LooseFrameSpriteSheetService looseFrameSpriteSheetService = new LooseFrameSpriteSheetService();
  private final ExistingSpriteSheetService existingSpriteSheetService = new ExistingSpriteSheetService();
  @FXML
  private TextField rootField;
  @FXML
  private TextArea logArea;
  @FXML
  private Button generateButton;
  @FXML
  private TextField looseRootField;
  @FXML
  private TextArea looseLogArea;
  @FXML
  private Button looseGenerateButton;
  @FXML
  private TextField looseCellWidthField;
  @FXML
  private TextField looseCellHeightField;
  @FXML
  private TextField mergeRootField;
  @FXML
  private TextArea mergeLogArea;
  @FXML
  private TextField mergeCellWidthField;
  @FXML
  private TextField mergeCellHeightField;
  @FXML
  private Button mergeButton;

  @FXML
  private void initialize() {
    bindPathField(rootPath, rootField);
    bindPathField(looseRootPath, looseRootField);
    bindPathField(mergeRootPath, mergeRootField);

    generateButton.disableProperty().bind(
        rootPath.isNull().or(busy)
    );
    looseGenerateButton.disableProperty().bind(
        looseRootPath.isNull().or(looseBusy)
    );
    mergeButton.disableProperty().bind(
        mergeRootPath.isNull().or(mergeBusy)
    );
  }

  @FXML
  private void onBrowse() {
    chooseDirectory(rootPath, rootField, "Select Root Folder");
  }

  @FXML
  private void onBrowseMergeRoot() {
    chooseDirectory(mergeRootPath, mergeRootField, "Select Sprite Sheet Folder");
  }

  @FXML
  private void onBrowseLooseRoot() {
    chooseDirectory(looseRootPath, looseRootField, "Select Loose Frame Folder");
  }

  @FXML
  private void onGenerate() {
    Path root = rootPath.get();
    if (root == null) {
      appendLog(logArea, "Select a root folder first.");
      return;
    }

    Task<List<SpriteSheetResult>> task = new Task<>() {
      @Override
      protected List<SpriteSheetResult> call() throws Exception {
        return spriteSheetService.generate(root);
      }
    };

    busy.set(true);
    appendLog(logArea, "Generating sprite sheets for " + root + " (no scaling)...");

    task.setOnSucceeded(event -> {
      busy.set(false);
      List<SpriteSheetResult> results = task.getValue();
      appendLog(logArea, "Done. Generated " + results.size() + " sprite sheet(s).");
      List<String> skippedLines = new ArrayList<>();
      int skippedCount = 0;
      for (SpriteSheetResult result : results) {
        appendLog(
            logArea,
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
        appendLog(logArea, "Warning: skipped " + skippedCount + " image(s) due to size mismatch.");
        StringBuilder message = new StringBuilder(
            "These images were not included because their sizes did not match:"
        );
        for (String line : skippedLines) {
          message.append(System.lineSeparator()).append(line);
        }
        showWarning("Sprite sheet generation warning", message.toString());
      }
    });

    task.setOnFailed(event -> {
      busy.set(false);
      Throwable error = task.getException();
      String message = error == null ? "Unknown error." : error.getMessage();
      appendLog(logArea, "Failed: " + message);
      showError("Sprite sheet generation failed", message);
    });

    startWorker(task, "sprite-sheet-generator");
  }

  @FXML
  private void onGenerateLooseSheets() {
    Path root = looseRootPath.get();
    if (root == null) {
      appendLog(looseLogArea, "Select a frame folder first.");
      return;
    }

    int cellWidth;
    int cellHeight;
    try {
      cellWidth = parsePositiveInt(looseCellWidthField, "Frame width");
      cellHeight = parsePositiveInt(looseCellHeightField, "Frame height");
    } catch (IllegalArgumentException error) {
      appendLog(looseLogArea, "Failed: " + error.getMessage());
      showError("Grouped sprite sheet generation failed", error.getMessage());
      return;
    }

    Task<LooseFrameSpriteSheetBatchResult> task = new Task<>() {
      @Override
      protected LooseFrameSpriteSheetBatchResult call() throws Exception {
        return looseFrameSpriteSheetService.generate(root, cellWidth, cellHeight);
      }
    };

    looseBusy.set(true);
    appendLog(
        looseLogArea,
        "Generating prefix-grouped sprite sheets from " + root
            + " (cell: " + cellWidth + "x" + cellHeight + ")"
            + " (Godot limit: "
            + LooseFrameSpriteSheetService.GODOT_MAX_TEXTURE_SIZE + "x"
            + LooseFrameSpriteSheetService.GODOT_MAX_TEXTURE_SIZE + ")..."
    );

    task.setOnSucceeded(event -> {
      looseBusy.set(false);
      LooseFrameSpriteSheetBatchResult batch = task.getValue();
      List<LooseFrameSpriteSheetResult> results = batch.sheets();
      long prefixCount = results.stream().map(LooseFrameSpriteSheetResult::prefix).distinct().count();
      appendLog(
          looseLogArea,
          "Done. Generated " + results.size() + " sprite sheet(s) across " + prefixCount + " prefix group(s)."
      );
      for (LooseFrameSpriteSheetResult result : results) {
        appendLog(
            looseLogArea,
            "Saved " + result.outputName()
                + " (prefix: " + result.prefix()
                + ", sheet: " + result.sheetIndex() + "/" + result.totalSheets()
                + ", cell: " + result.cellWidth() + "x" + result.cellHeight()
                + ", grid: " + result.columns() + "x" + result.rows()
                + ", frames: " + result.frameCount()
                + ") -> " + result.outputPath()
                + " (map: " + result.mappingPath() + ")"
        );
        appendLog(
            looseLogArea,
            "Atlas size: " + result.imageWidth() + "x" + result.imageHeight()
                + " (Godot limit: "
                + LooseFrameSpriteSheetService.GODOT_MAX_TEXTURE_SIZE + "x"
                + LooseFrameSpriteSheetService.GODOT_MAX_TEXTURE_SIZE + ")."
        );
        if (result.totalSheets() > 1 && result.sheetIndex() == 1) {
          appendLog(
              looseLogArea,
              "Prefix " + result.prefix() + " was split into " + result.totalSheets()
                  + " sheets to stay within Godot's texture-size limit."
          );
        }
      }
      if (!batch.excludedFrames().isEmpty()) {
        Map<String, Integer> detectedSizes = batch.detectedFrameSizes();
        String selectedSize = cellWidth + "x" + cellHeight;
        appendLog(
            looseLogArea,
            "Detected image sizes: " + formatSizeSummary(detectedSizes)
        );
        String swappedSize = cellHeight + "x" + cellWidth;
        int selectedCount = detectedSizes.getOrDefault(selectedSize, 0);
        int swappedCount = detectedSizes.getOrDefault(swappedSize, 0);
        if (!selectedSize.equals(swappedSize) && swappedCount > selectedCount) {
          appendLog(
              looseLogArea,
              "Hint: more files match " + swappedSize + " than " + selectedSize
                  + ". Check whether width and height are swapped."
          );
        }
        appendLog(
            looseLogArea,
            "Warning: skipped " + batch.excludedFrames().size()
                + " image(s) because they do not match the selected frame size."
        );
        StringBuilder message = new StringBuilder(
            "These images were not included because their sizes do not match "
                + cellWidth + "x" + cellHeight + ":"
        );
        message.append(System.lineSeparator())
            .append(System.lineSeparator())
            .append("Detected image sizes: ")
            .append(formatSizeSummary(detectedSizes));
        int previewLimit = 20;
        List<Path> excludedFrames = batch.excludedFrames();
        int previewCount = Math.min(previewLimit, excludedFrames.size());
        if (previewCount > 0) {
          message.append(System.lineSeparator())
              .append(System.lineSeparator())
              .append("Examples:");
        }
        for (int index = 0; index < previewCount; index++) {
          message.append(System.lineSeparator()).append(root.relativize(excludedFrames.get(index)));
        }
        int remaining = excludedFrames.size() - previewCount;
        if (remaining > 0) {
          message.append(System.lineSeparator())
              .append("... and ")
              .append(remaining)
              .append(" more.");
        }
        showWarning("Grouped sprite sheet warning", message.toString());
      }
    });

    task.setOnFailed(event -> {
      looseBusy.set(false);
      Throwable error = task.getException();
      String message = error == null ? "Unknown error." : error.getMessage();
      appendLog(looseLogArea, "Failed: " + message);
      showError("Grouped sprite sheet generation failed", message);
    });

    startWorker(task, "loose-frame-sprite-sheet-generator");
  }

  @FXML
  private void onMergeSheets() {
    Path root = mergeRootPath.get();
    if (root == null) {
      appendLog(mergeLogArea, "Select a sprite sheet folder first.");
      return;
    }

    int cellWidth;
    int cellHeight;
    try {
      cellWidth = parsePositiveInt(mergeCellWidthField, "Frame width");
      cellHeight = parsePositiveInt(mergeCellHeightField, "Frame height");
    } catch (IllegalArgumentException error) {
      appendLog(mergeLogArea, "Failed: " + error.getMessage());
      showError("Sprite sheet merge failed", error.getMessage());
      return;
    }

    Task<ExistingSpriteSheetResult> task = new Task<>() {
      @Override
      protected ExistingSpriteSheetResult call() throws Exception {
        return existingSpriteSheetService.generate(root, cellWidth, cellHeight);
      }
    };

    mergeBusy.set(true);
    appendLog(
        mergeLogArea,
        "Merging existing sprite sheets from " + root + " (cell: " + cellWidth + "x" + cellHeight + ")..."
    );

    task.setOnSucceeded(event -> {
      mergeBusy.set(false);
      ExistingSpriteSheetResult result = task.getValue();
      appendLog(mergeLogArea, "Done. Created merged sprite sheet.");
      appendLog(
          mergeLogArea,
          "Saved " + result.outputName()
              + " (cell: " + result.cellWidth() + "x" + result.cellHeight()
              + ", grid: " + result.columns() + "x" + result.rows()
              + ", frames: " + result.frameCount() + ") -> " + result.outputPath()
              + " (map: " + result.mappingPath() + ")"
      );
      appendLog(
          mergeLogArea,
          "Packed atlas size: " + result.imageWidth() + "x" + result.imageHeight()
              + " (Godot limit: "
              + ExistingSpriteSheetService.GODOT_MAX_TEXTURE_SIZE + "x"
              + ExistingSpriteSheetService.GODOT_MAX_TEXTURE_SIZE + ")."
      );
      appendLog(
          mergeLogArea,
          "Verified saved PNG matches all " + result.frameCount() + " source frame(s) pixel-for-pixel."
      );
      if (!result.excludedSheets().isEmpty()) {
        appendLog(
            mergeLogArea,
            "Warning: skipped " + result.excludedSheets().size()
                + " source sheet(s) because they do not fit the selected frame size."
        );
        StringBuilder message = new StringBuilder(
            "These sprite sheets were not included because their sizes are not divisible by "
                + cellWidth + "x" + cellHeight + ":"
        );
        for (Path excludedSheet : result.excludedSheets()) {
          String name = root.relativize(excludedSheet).toString();
          message.append(System.lineSeparator()).append(name);
        }
        showWarning("Sprite sheet merge warning", message.toString());
      }
    });

    task.setOnFailed(event -> {
      mergeBusy.set(false);
      Throwable error = task.getException();
      String message = error == null ? "Unknown error." : error.getMessage();
      appendLog(mergeLogArea, "Failed: " + message);
      showError("Sprite sheet merge failed", message);
    });

    startWorker(task, "existing-sprite-sheet-merger");
  }

  private void bindPathField(ObjectProperty<Path> pathProperty, TextField field) {
    pathProperty.addListener((observable, oldValue, newValue) -> {
      if (newValue == null) {
        field.clear();
      } else {
        field.setText(newValue.toString());
      }
    });
  }

  private void chooseDirectory(ObjectProperty<Path> pathProperty, TextField field, String title) {
    DirectoryChooser chooser = new DirectoryChooser();
    chooser.setTitle(title);
    Path current = pathProperty.get();
    if (current != null && current.toFile().isDirectory()) {
      chooser.setInitialDirectory(current.toFile());
    }
    File selected = chooser.showDialog(field.getScene().getWindow());
    if (selected != null) {
      pathProperty.set(selected.toPath());
    }
  }

  private int parsePositiveInt(TextField field, String label) {
    String text = field.getText();
    try {
      int value = Integer.parseInt(text == null ? "" : text.trim());
      if (value <= 0) {
        throw new IllegalArgumentException(label + " must be a positive integer.");
      }
      return value;
    } catch (NumberFormatException error) {
      throw new IllegalArgumentException(label + " must be a positive integer.");
    }
  }

  private void appendLog(TextArea target, String message) {
    if (target.getText().isEmpty()) {
      target.setText(message);
    } else {
      target.appendText(System.lineSeparator() + message);
    }
  }

  private void startWorker(Task<?> task, String threadName) {
    Thread worker = new Thread(task, threadName);
    worker.setDaemon(true);
    worker.start();
  }

  private void showError(String title, String message) {
    showAlert(Alert.AlertType.ERROR, title, message);
  }

  private void showWarning(String title, String message) {
    showAlert(Alert.AlertType.WARNING, title, message);
  }

  private String formatSizeSummary(Map<String, Integer> detectedSizes) {
    return detectedSizes.entrySet().stream()
        .sorted(
            Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                .reversed()
                .thenComparing(Map.Entry::getKey)
        )
        .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
        .collect(Collectors.joining(", "));
  }

  private void showAlert(Alert.AlertType type, String title, String message) {
    Alert alert = new Alert(type);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(message == null ? "Unknown error." : message);
    alert.showAndWait();
  }
}
