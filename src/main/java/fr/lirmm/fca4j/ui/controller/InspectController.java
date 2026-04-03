package fr.lirmm.fca4j.ui.controller;

import fr.lirmm.fca4j.ui.model.CommandBuilder;
import fr.lirmm.fca4j.ui.model.CommandDescriptor;
import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.I18n;
import fr.lirmm.fca4j.ui.util.Utilities;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Contrôleur du panneau pour la commande INSPECT.
 * Inspecte un contexte formel et affiche ses statistiques dans la console.
 */
public class InspectController implements Initializable {

    // ── TitledPanes et bouton ─────────────────────────────────────────────────
    @FXML private TitledPane       inputPane;
    @FXML private TitledPane       advancedPane;

    // ── Bouton édition ────────────────────────────────────────────────────────
    @FXML private Button           editInputButton;
    private Consumer<Path>         openInEditor;

    // ── Entrée ────────────────────────────────────────────────────────────────
    @FXML private TextField        inputFileField;
    @FXML private ComboBox<String> inputFormatCombo;
    @FXML private Label            separatorLabel;
    @FXML private ComboBox<String> separatorCombo;

    // ── Options avancées ──────────────────────────────────────────────────────
    @FXML private Spinner<Integer> timeoutSpinner;
    @FXML private CheckBox         verboseCheckBox;

    private Consumer<CommandBuilder> onRun;
    private Consumer<String> onInputChanged;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        inputFormatCombo.getItems().addAll("(auto)", "CXT", "SLF", "XML", "CEX", "CSV");
        inputFormatCombo.setValue("(auto)");

        separatorCombo.getItems().addAll("COMMA", "SEMICOLON", "TAB");
        separatorCombo.setValue("COMMA");
        separatorLabel.setVisible(false);
        separatorCombo.setVisible(false);
        inputFormatCombo.valueProperty().addListener((obs, old, val) -> {
            boolean csv = "CSV".equals(val);
            separatorLabel.setVisible(csv);
            separatorCombo.setVisible(csv);
        });

        timeoutSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 3600, 0, 10));
        Utilities.bindPathTooltip(inputFileField);
    }

    public void configure(CommandDescriptor desc, Consumer<CommandBuilder> onRun,
            Consumer<Path> openInEditor, Consumer<String> onInputChanged) {
this.onInputChanged = onInputChanged;
        this.onRun        = onRun;
        this.openInEditor = openInEditor;

        inputPane.setText(I18n.get("section.input"));
        advancedPane.setText(I18n.get("section.advanced"));

        FontIcon editIcon = new FontIcon(Material2AL.EDIT);
        editIcon.setIconSize(14);
        editInputButton.setGraphic(editIcon);
        editInputButton.setText("");
        editInputButton.setTooltip(new Tooltip(I18n.get("btn.open.in.editor")));
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    @FXML
    private void onEditInput() {
        String path = inputFileField.getText().trim();
        if (path.isBlank()) {
            showError(I18n.get("error.no.input.title"),
                      I18n.get("error.no.input.detail"));
            return;
        }
        if (openInEditor != null)
            openInEditor.accept(Path.of(path));
    }

    @FXML
    private void onBrowseInput() {
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.get("label.input.file"));
        fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("SLF (HTK)",        "*.slf"),
            new FileChooser.ExtensionFilter("CEX (ConExp)",     "*.cex"),
            new FileChooser.ExtensionFilter("CXT (Burmeister)", "*.cxt"),
            new FileChooser.ExtensionFilter("XML (Galicia)",    "*.xml"),
            new FileChooser.ExtensionFilter("CSV",              "*.csv"),
            new FileChooser.ExtensionFilter(I18n.get("filter.all"), "*.*")
        );
        File f = fc.showOpenDialog(inputFileField.getScene().getWindow());
        if (f != null) {
            inputFileField.setText(f.getAbsolutePath());
            if (onInputChanged != null) onInputChanged.accept(f.getAbsolutePath());
            AppPreferences.setLastDirectory(f.getParent());
            autoDetectFormat(f.getName());
        }
    }

    @FXML
    public void onRun() {
        if (inputFileField.getText().isBlank()) {
            showError(I18n.get("error.no.input.title"),
                      I18n.get("error.no.input.detail"));
            return;
        }

        CommandBuilder builder = new CommandBuilder()
            .command("INSPECT")
            .inputFile(inputFileField.getText().trim())
            .verbose(verboseCheckBox.isSelected());

        String fmt = inputFormatCombo.getValue();
        if (!"(auto)".equals(fmt)) builder.inputFormat(fmt);
        if ("CSV".equals(fmt))     builder.separator(separatorCombo.getValue());

        int to = timeoutSpinner.getValue();
        if (to > 0) builder.timeout(to);

        if (onRun != null) onRun.accept(builder);
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private void autoDetectFormat(String filename) {
        String lower = filename.toLowerCase();
        if      (lower.endsWith(".cxt")) inputFormatCombo.setValue("CXT");
        else if (lower.endsWith(".slf")) inputFormatCombo.setValue("SLF");
        else if (lower.endsWith(".xml")) inputFormatCombo.setValue("XML");
        else if (lower.endsWith(".cex")) inputFormatCombo.setValue("CEX");
        else if (lower.endsWith(".csv")) inputFormatCombo.setValue("CSV");
        else                             inputFormatCombo.setValue("(auto)");
    }

    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg);
        a.showAndWait();
    }
    public void setInputFile(String path) {
        if (path == null || path.isBlank()) return;
        inputFileField.setText(path);
        autoDetectFormat(new File(path).getName());
        // Pas d'output pour INSPECT
    }
    public String getInputFile() {
        return inputFileField.getText();
    }
    public String getSeparator() {
        return "CSV".equals(inputFormatCombo.getValue())
            ? separatorCombo.getValue() : null;
    }
    }
