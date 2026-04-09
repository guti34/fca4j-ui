package fr.lirmm.fca4j.ui.controller;

import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.I18n;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.net.URL;
import java.util.Locale;
import java.util.ResourceBundle;

public class PreferencesController implements Initializable {

    @FXML private Label            titleLabel;
    @FXML private Label            jarLabel;
    @FXML private Label            jarHintLabel;
    @FXML private Label            dotLabel;
    @FXML private Label            dotHintLabel;
    @FXML private Label            langLabel;
    @FXML private TextField        jarPathField;
    @FXML private TextField        dotPathField;
    @FXML private ComboBox<Locale> languageCombo;
    @FXML private Button           saveButton;
    @FXML private Button           cancelButton;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Textes via I18n
        titleLabel.setText(I18n.get("prefs.section.title"));
        jarLabel.setText(I18n.get("prefs.jar.label"));
        jarPathField.setPromptText(I18n.get("prefs.jar.prompt"));
        jarHintLabel.setText(I18n.get("prefs.jar.hint"));
        dotLabel.setText(I18n.get("prefs.dot.label"));
        dotPathField.setPromptText(I18n.get("prefs.dot.prompt"));
        dotHintLabel.setText(I18n.get("prefs.dot.hint"));
        langLabel.setText(I18n.get("prefs.language.label"));
        saveButton.setText(I18n.get("button.save"));
        cancelButton.setText(I18n.get("button.cancel"));

        // Valeurs courantes
        jarPathField.setText(AppPreferences.getFca4jJarPath());
        dotPathField.setText(AppPreferences.getDotPath());

        // Sélecteur de langue
        languageCombo.getItems().setAll(I18n.SUPPORTED_LOCALES);
        languageCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Locale l) {
                return l == null ? "" : I18n.displayName(l);
            }
            @Override public Locale fromString(String s) { return null; }
        });
        languageCombo.setValue(I18n.getLocale());
    }

    @FXML
    private void onBrowseJar() {
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.get("prefs.jar.label"));
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("JAR", "*.jar"));
        var file = fc.showOpenDialog(jarPathField.getScene().getWindow());
        if (file != null) jarPathField.setText(file.getAbsolutePath());
    }

    @FXML
    private void onBrowseDot() {
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.get("prefs.dot.label"));
        var file = fc.showOpenDialog(dotPathField.getScene().getWindow());
        if (file != null) dotPathField.setText(file.getAbsolutePath());
    }

    @FXML
    private void onSave() {
        AppPreferences.setFca4jJarPath(jarPathField.getText().trim());
        AppPreferences.setDotPath(dotPathField.getText().trim());

        Locale selected = languageCombo.getValue();
        if (selected != null && !selected.equals(I18n.getLocale())) {
            I18n.setLocale(selected);
            // Informer l'utilisateur qu'un rechargement est nécessaire
            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.setTitle(I18n.get("prefs.title"));
            info.setHeaderText(null);
            info.setContentText(
                    "en".equals(selected.getLanguage())
                        ? "Language changed. Please restart the application."
                        : "es".equals(selected.getLanguage())
                        ? "Idioma cambiado. Por favor reinicie la aplicación."
                        : "Langue modifiée. Veuillez redémarrer l'application."
                );
            info.showAndWait();
        }

        closeWindow();
    }

    @FXML
    private void onCancel() { closeWindow(); }

    private void closeWindow() {
        ((Stage) jarPathField.getScene().getWindow()).close();
    }
}
