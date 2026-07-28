/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.ui.controller;

import fr.lirmm.fca4j.ui.service.Fca4jRunner;
import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.BrowserRegistry;
import fr.lirmm.fca4j.ui.util.BrowserRegistry.Browser;
import fr.lirmm.fca4j.ui.util.I18n;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class PreferencesController implements Initializable {

    @FXML private Label             titleLabel;
    @FXML private Label             jarLabel;
    @FXML private Label             jarHintLabel;
    @FXML private Label             dotLabel;
    @FXML private Label             dotHintLabel;
    @FXML private Label             langLabel;
    @FXML private Label             browserLabel;
    @FXML private Label             browserHintLabel;
    @FXML private Label             vmArgsLabel;
    @FXML private Label             vmArgsHintLabel;
    @FXML private TextField         jarPathField;
    @FXML private TextField         dotPathField;
    @FXML private TextField         vmArgsField;
    @FXML private ComboBox<Locale>  languageCombo;
    @FXML private ComboBox<Browser> browserCombo;
    @FXML private Button            refreshBrowsersButton;
    @FXML private Button            saveButton;
    @FXML private Button            cancelButton;
    @FXML private CheckBox          useExternalJarCheckBox;
    @FXML private Button            browseJarButton;

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
        browserLabel.setText(I18n.get("prefs.browser.label"));
        browserHintLabel.setText(I18n.get("prefs.browser.hint"));
        refreshBrowsersButton.setTooltip(new Tooltip(I18n.get("prefs.browser.refresh")));
        saveButton.setText(I18n.get("button.save"));
        cancelButton.setText(I18n.get("button.cancel"));
        useExternalJarCheckBox.setText(I18n.get("prefs.jar.external"));
        vmArgsLabel.setText(I18n.get("prefs.vmargs.label"));
        vmArgsHintLabel.setText(I18n.get("prefs.vmargs.hint"));

        // Valeurs courantes
        dotPathField.setText(AppPreferences.getDotPath());
        vmArgsField.setText(AppPreferences.getVmArgs());

        // Checkbox JAR externe
        boolean useExternal = AppPreferences.isUseExternalFca4j();
        useExternalJarCheckBox.setSelected(useExternal);
        if (Fca4jRunner.hasEmbeddedJar()) {
            jarHintLabel.setText(I18n.get("prefs.jar.embedded.version",
                Fca4jRunner.getEmbeddedVersion()));
        } else {
            jarHintLabel.setText(I18n.get("prefs.jar.hint"));
        }
        jarPathField.setText(AppPreferences.getFca4jJarPath());
        jarPathField.setDisable(!useExternal);
        browseJarButton.setDisable(!useExternal);

        useExternalJarCheckBox.selectedProperty().addListener((obs, old, val) -> {
            jarPathField.setDisable(!val);
            browseJarButton.setDisable(!val);
        });

        // Sélecteur de langue
        languageCombo.getItems().setAll(I18n.SUPPORTED_LOCALES);
        languageCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Locale l) {
                return l == null ? "" : I18n.displayName(l);
            }
            @Override public Locale fromString(String s) { return null; }
        });
        languageCombo.setValue(I18n.getLocale());

        // Sélecteur de navigateur
        initBrowserCombo();
    }

    // ── Navigateur ───────────────────────────────────────────────────────────

    private void initBrowserCombo() {
        browserCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Browser b) {
                if (b == null) return "";
                if (b.isSystemDefault()) return I18n.get("prefs.browser.default");
                return b.webkit()
                    ? b.name() + " " + I18n.get("prefs.browser.webkit.suffix")
                    : b.name();
            }
            @Override public Browser fromString(String s) { return null; }
        });

        browserCombo.getSelectionModel().selectedItemProperty()
            .addListener((obs, old, val) -> updateBrowserHint(val));

        loadBrowsersAsync();
    }

    /**
     * La détection interroge la base de registre sous Windows et Spotlight
     * sous macOS : elle ne doit pas s'exécuter sur le thread JavaFX.
     */
    private void loadBrowsersAsync() {
        browserCombo.getItems().setAll(Browser.systemDefault());
        browserCombo.getSelectionModel().selectFirst();
        browserCombo.setDisable(true);
        refreshBrowsersButton.setDisable(true);
        browserHintLabel.setText(I18n.get("prefs.browser.detecting"));

        Task<List<Browser>> task = new Task<>() {
            @Override protected List<Browser> call() {
                return BrowserRegistry.installed();
            }
        };
        task.setOnSucceeded(e -> {
            List<Browser> items = new ArrayList<>();
            items.add(Browser.systemDefault());
            items.addAll(task.getValue());
            browserCombo.getItems().setAll(items);

            String saved = AppPreferences.getPreferredBrowserId();
            Browser selected = items.stream()
                .filter(b -> b.id().equals(saved))
                .findFirst()
                .orElse(items.get(0));
            browserCombo.getSelectionModel().select(selected);

            browserCombo.setDisable(false);
            refreshBrowsersButton.setDisable(false);
            updateBrowserHint(selected);
        });
        task.setOnFailed(e -> {
            browserCombo.setDisable(false);
            refreshBrowsersButton.setDisable(false);
            browserHintLabel.setText(I18n.get("prefs.browser.hint"));
        });

        Thread thread = new Thread(task, "browser-detect");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateBrowserHint(Browser browser) {
        if (browser != null && browser.webkit()) {
            browserHintLabel.setText(I18n.get("prefs.browser.webkit.warning"));
            browserHintLabel.setStyle(
                "-fx-text-fill: #b35c00; -fx-font-size: 11px; -fx-wrap-text: true;");
        } else {
            browserHintLabel.setText(I18n.get("prefs.browser.hint"));
            browserHintLabel.setStyle(
                "-fx-text-fill: gray; -fx-font-size: 11px; -fx-wrap-text: true;");
        }
    }

    @FXML
    private void onRefreshBrowsers() {
        BrowserRegistry.refresh();
        loadBrowsersAsync();
    }

    // ── Actions ──────────────────────────────────────────────────────────────

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
        AppPreferences.setUseExternalFca4j(useExternalJarCheckBox.isSelected());
        AppPreferences.setFca4jJarPath(jarPathField.getText().trim());
        AppPreferences.setDotPath(dotPathField.getText().trim());
        AppPreferences.setVmArgs(vmArgsField.getText().trim());

        Browser browser = browserCombo.getValue();
        AppPreferences.setPreferredBrowserId(browser == null ? "" : browser.id());

        Locale selected = languageCombo.getValue();
        if (selected != null && !selected.equals(I18n.getLocale())) {
            I18n.setLocale(selected);
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
