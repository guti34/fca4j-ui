package fr.lirmm.fca4j.ui;

import fr.lirmm.fca4j.ui.util.I18n;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class MainApp extends Application {

    public static final String APP_TITLE   = "FCA4J UI";
    public static final String APP_VERSION = "0.1.0";

    @Override
    public void start(Stage primaryStage) throws IOException {
        URL fxml = getClass().getResource("/fr/lirmm/fca4j/ui/fxml/main.fxml");
        FXMLLoader loader = new FXMLLoader(fxml, I18n.getBundle());
        Scene scene = new Scene(loader.load(), 1200, 750);
        scene.getStylesheets().add(
            getClass().getResource("/fr/lirmm/fca4j/ui/css/app.css").toExternalForm()
        );
        primaryStage.setTitle(I18n.get("app.title") + " " + APP_VERSION);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}