package fr.lirmm.fca4j.ui;

import fr.lirmm.fca4j.ui.controller.MainController;
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
    public static final String APP_VERSION;
    public static final String APP_URL;
    public static final String APP_SCM;
    public static final String APP_ORG_NAME;
    public static final String APP_ORG_URL;
    public static final String APP_INCEPTION;
    public static final String APP_LICENSE;
    public static final String[] APP_DEVELOPERS;

    static {
        var props = new java.util.Properties();
        try (var is = MainApp.class.getResourceAsStream("/app.properties")) {
            if (is != null) props.load(is);
        } catch (Exception ignored) {}

        APP_VERSION   = props.getProperty("app.version",    "0.1.0");
        APP_URL       = props.getProperty("app.url",        "");
        APP_SCM       = props.getProperty("app.scm",        "");
        APP_ORG_NAME  = props.getProperty("app.org.name",   "LIRMM");
        APP_ORG_URL   = props.getProperty("app.org.url",    "");
        APP_INCEPTION = props.getProperty("app.inception",  "2026");
        APP_LICENSE   = props.getProperty("app.license",    "BSD-3-Clause");
        APP_DEVELOPERS = new String[]{
            props.getProperty("app.dev.1.name","") + " (" + props.getProperty("app.dev.1.org","") + ")",
            props.getProperty("app.dev.2.name","") + " (" + props.getProperty("app.dev.2.org","") + ")",
            props.getProperty("app.dev.3.name","") + " (" + props.getProperty("app.dev.3.org","") + ")",
        };
    }
    @Override
    public void start(Stage primaryStage) throws IOException {
        // Force le chargement du pack au démarrage
        FontIcon.of(Material2AL.ADD); 
        URL fxml = getClass().getResource("/fr/lirmm/fca4j/ui/fxml/main.fxml");
        FXMLLoader loader = new FXMLLoader(fxml, I18n.getBundle());
        Scene scene = new Scene(loader.load(), 1200, 750);
        MainController mainController = loader.getController();
        scene.getStylesheets().add(
            getClass().getResource("/fr/lirmm/fca4j/ui/css/app.css").toExternalForm()
        );
        // Icône de la fenêtre (barre de titre + taskbar)
        try {
        	primaryStage.getIcons().addAll(
        		    new Image(getClass().getResourceAsStream("/fr/lirmm/fca4j/ui/icons/fca4j-ui_16x16.png")),
        		    new Image(getClass().getResourceAsStream("/fr/lirmm/fca4j/ui/icons/fca4j-ui_32x32.png")),
        		    new Image(getClass().getResourceAsStream("/fr/lirmm/fca4j/ui/icons/fca4j-ui_48x48.png")),
        		    new Image(getClass().getResourceAsStream("/fr/lirmm/fca4j/ui/icons/fca4j-ui_128x128.png")),
           		    new Image(getClass().getResourceAsStream("/fr/lirmm/fca4j/ui/icons/fca4j-ui_256x256.png")),
           	       	new Image(getClass().getResourceAsStream("/fr/lirmm/fca4j/ui/icons/fca4j-ui_512x512.png")),
           	        new Image(getClass().getResourceAsStream("/fr/lirmm/fca4j/ui/icons/fca4j-ui_1024x1024.png")	)
        		);            
        } catch (Exception ignored) {
        	 ignored.printStackTrace();
        }

        primaryStage.setTitle(I18n.get("app.title") + " " + APP_VERSION);
        primaryStage.setScene(scene);
        primaryStage.show();
        primaryStage.setOnCloseRequest(e -> {
            if (!mainController.confirmDiscardAll()) {
                e.consume(); // annuler la fermeture
                return;
            }
            mainController.shutdown();
        });   }
    
    public static void main(String[] args) {
        launch(args);
    }
}