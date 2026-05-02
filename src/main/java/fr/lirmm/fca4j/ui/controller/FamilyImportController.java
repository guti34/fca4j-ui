/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.ui.controller;

import fr.lirmm.fca4j.ui.model.CommandBuilder;
import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.I18n;
import fr.lirmm.fca4j.ui.util.Utilities;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class FamilyImportController implements Initializable {

    @FXML private TitledPane       inputPane;
    @FXML private TitledPane       outputPane;
    @FXML private TitledPane       advancedPane;
    @FXML private TextField        inputFileField;
    @FXML private ComboBox<String> modelFormatCombo;
    @FXML private TextField        outputFileField;
    @FXML private ComboBox<String> familyFormatCombo;
    @FXML private Label            separatorLabel;
    @FXML private ComboBox<String> separatorCombo;
    @FXML private Spinner<Integer> timeoutSpinner;
    @FXML private CheckBox         verboseCheckBox;
    @FXML private Button btnEditModel;
    
    private Consumer<java.nio.file.Path> openInModelEditor;

    private Consumer<CommandBuilder> onRun;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        modelFormatCombo.getItems().addAll("JSON", "XML");
        modelFormatCombo.setValue("JSON");
     // XML non encore supporté
        modelFormatCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setDisable(false); return; }
                setText(item);
                setDisable("XML".equals(item));
                setStyle("XML".equals(item) ? "-fx-text-fill: gray;" : "");
            }
        });
        modelFormatCombo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item);
            }
        });
        familyFormatCombo.getItems().addAll("RCFT", "RCFGZ", "RCFAL");
        familyFormatCombo.setValue("RCFT");

        separatorCombo.getItems().addAll("COMMA", "SEMICOLON", "TAB");
        separatorCombo.setValue("COMMA");
        separatorLabel.setVisible(false);
        separatorCombo.setVisible(false);

        timeoutSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 3600, 0, 10));
        Utilities.bindPathTooltip(inputFileField);
        Utilities.bindPathTooltip(outputFileField);        
    }

    public void configure(Consumer<CommandBuilder> onRun) {
        this.onRun = onRun;
        inputPane.setText(I18n.get("section.model"));
        outputPane.setText(I18n.get("section.output"));
        advancedPane.setText(I18n.get("section.advanced"));

        // Icône crayon
        org.kordamp.ikonli.javafx.FontIcon editIcon =
            new org.kordamp.ikonli.javafx.FontIcon(
                org.kordamp.ikonli.material2.Material2AL.EDIT);
        editIcon.setIconSize(14);
        btnEditModel.setGraphic(editIcon);
        btnEditModel.setText("");
        btnEditModel.setTooltip(new Tooltip(I18n.get("btn.open.in.editor")));
        btnEditModel.setDisable(true);
        inputFileField.textProperty().addListener((obs, old, val) ->
            btnEditModel.setDisable(val.isBlank()));
        loadPrefs();
    }
    public void setOpenInModelEditor(Consumer<java.nio.file.Path> callback) {
        this.openInModelEditor = callback;
    }

    @FXML private void onEditModel() {
        String path = inputFileField.getText().trim();
        if (path.isBlank()) {
            new Alert(Alert.AlertType.WARNING,
                I18n.get("error.no.input.detail")).showAndWait();
            return;
        }
        if (openInModelEditor != null)
            openInModelEditor.accept(java.nio.file.Path.of(path));
    }
    @FXML private void onBrowseInput() {
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.get("label.input.file"));
        fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("JSON", "*.json"),
            new FileChooser.ExtensionFilter("XML",  "*.xml"),
            new FileChooser.ExtensionFilter(I18n.get("filter.all"), "*.*")
        );
        File f = fc.showOpenDialog(inputFileField.getScene().getWindow());
        if (f != null) {
            inputFileField.setText(f.getAbsolutePath());
            AppPreferences.setLastDirectory(f.getParent());
            // Auto-détecter le format modèle
            if (f.getName().toLowerCase().endsWith(".xml"))
                modelFormatCombo.setValue("XML");
            else
                modelFormatCombo.setValue("JSON");
            // Sortie par défaut
            if (outputFileField.getText().isBlank()) {
                String base = f.getAbsolutePath().replaceAll("\\.[^.]+$", "");
                outputFileField.setText(base + ".rcft");
            }
        }
    }

    @FXML private void onBrowseOutput() {
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.get("label.output.file"));
        fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("RCFT", "*.rcft"),
            new FileChooser.ExtensionFilter("RCFGZ", "*.rcfgz"),
            new FileChooser.ExtensionFilter("RCFAL", "*.rcfal")
        );
        File f = fc.showSaveDialog(outputFileField.getScene().getWindow());
        if (f != null) {
            outputFileField.setText(f.getAbsolutePath());
            String name = f.getName().toLowerCase();
            if      (name.endsWith(".rcfgz")) familyFormatCombo.setValue("RCFGZ");
            else if (name.endsWith(".rcfal")) familyFormatCombo.setValue("RCFAL");
            else                              familyFormatCombo.setValue("RCFT");
        }
    }

    public void onRun() {
        if (inputFileField.getText().isBlank()) {
            new Alert(Alert.AlertType.WARNING,
                I18n.get("error.no.input.detail")).showAndWait();
            return;
        }
        savePrefs();
        CommandBuilder builder = new CommandBuilder()
            .command("FAMILY_IMPORT")
            .inputFile(inputFileField.getText().trim())
            .familyImportModelFormat(modelFormatCombo.getValue())
            .verbose(verboseCheckBox.isSelected());

        if (!outputFileField.getText().isBlank())
            AppPreferences.saveOutputForInput("FAMILY_IMPORT",
                    inputFileField.getText().trim(),
                    outputFileField.getText().trim());
        builder.outputFile(
                Utilities.resolveOutput(outputFileField.getText().trim(), inputFileField));

        String fmt = familyFormatCombo.getValue();
        if (!"RCFT".equals(fmt)) builder.familyFormat(fmt);

        int to = timeoutSpinner.getValue();
        if (to > 0) builder.timeout(to);

    	if (!outputFileField.getText().isBlank())
    	    AppPreferences.saveOutputForInput(
    	        "FAMILY_IMPORT",
    	        inputFileField.getText().trim(),
    	        outputFileField.getText().trim());
        if (onRun != null) onRun.accept(builder);
    }

    private static final String P = "FAMILY_IMPORT.";

    private void savePrefs() {
        AppPreferences.saveString(P + "modelFormat",  modelFormatCombo.getValue());
        AppPreferences.saveString(P + "familyFormat", familyFormatCombo.getValue());
        AppPreferences.saveBool  (P + "verbose",      verboseCheckBox.isSelected());
        AppPreferences.saveInt   (P + "timeout",      timeoutSpinner.getValue());
    }

    private void loadPrefs() {
        String mf = AppPreferences.loadString(P + "modelFormat", "JSON");
        if (modelFormatCombo.getItems().contains(mf)) modelFormatCombo.setValue(mf);
        String ff = AppPreferences.loadString(P + "familyFormat", "RCFT");
        if (familyFormatCombo.getItems().contains(ff)) familyFormatCombo.setValue(ff);
        verboseCheckBox.setSelected(AppPreferences.loadBool(P + "verbose", false));
        timeoutSpinner.getValueFactory().setValue(AppPreferences.loadInt(P + "timeout", 0));
    }
    public void setInputFile(String path) {
        if (path == null || path.isBlank()) return;
        inputFileField.setText(path);

        if (path.toLowerCase().endsWith(".xml"))
            modelFormatCombo.setValue("XML");
        else
            modelFormatCombo.setValue("JSON");

        String savedOutput = AppPreferences.loadOutputForInput("FAMILY_IMPORT", path);
        outputFileField.setText(savedOutput.isBlank()
            ? path.replaceAll("\\.[^.]+$", "") + ".rcft" : savedOutput);
    }
    }