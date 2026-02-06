package pl.spritesheetgluer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class SpriteSheetApplication extends Application {
  @Override
  public void start(Stage stage) throws IOException {
    FXMLLoader fxmlLoader =
        new FXMLLoader(SpriteSheetApplication.class.getResource("sprite-sheet-view.fxml"));
    Scene scene = new Scene(fxmlLoader.load(), 720, 420);
    stage.setTitle("Sprite Sheet Gluer");
    stage.setScene(scene);
    stage.show();
  }
}
