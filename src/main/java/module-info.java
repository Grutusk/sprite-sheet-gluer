module pl.smallapps.spritesheetgluer {
  requires java.desktop;
  requires javafx.controls;
  requires javafx.fxml;

  requires org.controlsfx.controls;
  requires com.dlsc.formsfx;
  requires net.synedra.validatorfx;
  requires org.kordamp.ikonli.javafx;
  requires org.kordamp.bootstrapfx.core;

  opens pl.spritesheetgluer to javafx.fxml;
  exports pl.spritesheetgluer;
  exports pl.spritesheetgluer.definition;
  opens pl.spritesheetgluer.definition to javafx.fxml;
  exports pl.spritesheetgluer.ui;
  opens pl.spritesheetgluer.ui to javafx.fxml;
  exports pl.spritesheetgluer.sprite;
  opens pl.spritesheetgluer.sprite to javafx.fxml;
}
