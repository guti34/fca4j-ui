package fr.lirmm.fca4j.ui;

import fr.lirmm.fca4j.ui.util.I18n;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class MainApp extends Application {

    public static final String APP_TITLE   = "FCA4J UI";
    public static final String APP_VERSION = "0.1.0";

    @Override
    public void start(Stage primaryStage) throws IOException {
        // Force le chargement du pack au démarrage
        FontIcon.of(Material2AL.ADD); 
        URL fxml = getClass().getResource("/fr/lirmm/fca4j/ui/fxml/main.fxml");
        FXMLLoader loader = new FXMLLoader(fxml, I18n.getBundle());
        Scene scene = new Scene(loader.load(), 1200, 750);
        scene.getStylesheets().add(
            getClass().getResource("/fr/lirmm/fca4j/ui/css/app.css").toExternalForm()
        );
        // Icône de la fenêtre (barre de titre + taskbar)
        try {
            InputStream iconStream = getClass()
                .getResourceAsStream("/fr/lirmm/fca4j/ui/icons/fca4j-logo.png");
            if (iconStream != null) {
                primaryStage.getIcons().add(new Image(iconStream));
            }
        } catch (Exception ignored) {}

        primaryStage.setTitle(I18n.get("app.title") + " " + APP_VERSION);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}