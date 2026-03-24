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
 * Contrôleur du panneau pour la commande IRREDUCIBLE.
 * Liste les objets et/ou attributs irréductibles d'un contexte formel.
 */
public class IrreducibleController implements Initializable {

    // ── TitledPanes et bouton ─────────────────────────────────────────────────
    @FXML private TitledPane       inputPane;
    @FXML private TitledPane       outputPane;
    @FXML private TitledPane       operationPane;
    @FXML private TitledPane       advancedPane;
    @FXML private Button           runButton;

    // ── Bouton édition ────────────────────────────────────────────────────────
    @FXML private Button           editInputButton;
    private Consumer<Path>         openInEditor;

    // ── Entrée ────────────────────────────────────────────────────────────────
    @FXML private TextField        inputFileField;
    @FXML private ComboBox<String> inputFormatCombo;
    @FXML private Label            separatorLabel;
    @FXML private ComboBox<String> separatorCombo;

    // ── Sortie (optionnelle — fichier texte) ──────────────────────────────────
    @FXML private TextField        outputFileField;

    // ── Opération ─────────────────────────────────────────────────────────────
    @FXML private CheckBox         lobjCheckBox;   // -lobj
    @FXML private CheckBox         lattrCheckBox;  // -lattr
    @FXML private CheckBox         groupCheckBox;  // -u

    // ── Options avancées ──────────────────────────────────────────────────────
    @FXML private ComboBox<String> implCombo;
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

        implCombo.getItems().addAll(
            "BITSET", "ROARING_BITMAP", "SPARSE_BITSET",
            "HASHSET", "TREESET", "INT_ARRAY", "ARRAYLIST", "BOOL_ARRAY");
        implCombo.setValue("BITSET");

        timeoutSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 3600, 0, 10));
    }

    public void configure(CommandDescriptor desc, Consumer<CommandBuilder> onRun,
            Consumer<Path> openInEditor, Consumer<String> onInputChanged) {
this.onInputChanged = onInputChanged;
        this.onRun        = onRun;
        this.openInEditor = openInEditor;

        inputPane.setText(I18n.get("section.input"));
        outputPane.setText(I18n.get("section.output"));
        operationPane.setText(I18n.get("section.operation"));
        advancedPane.setText(I18n.get("section.advanced"));
        runButton.setText(I18n.get("button.run"));

        // Bouton "Ouvrir dans l'éditeur"
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
        FileChooser fc = buildContextChooser(I18n.get("label.input.file"));
        File f = fc.showOpenDialog(inputFileField.getScene().getWindow());
        if (f != null) {
            inputFileField.setText(f.getAbsolutePath());
            if (onInputChanged != null) onInputChanged.accept(f.getAbsolutePath());
            AppPreferences.setLastDirectory(f.getParent());
            autoDetectFormat(f.getName());
            if (outputFileField.getText().isBlank()) {
                String base = f.getAbsolutePath().replaceAll("\\.[^.]+$", "");
                outputFileField.setText(base + "-irreducible.txt");
            }
        }
    }

    @FXML
    private void onBrowseOutput() {
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.get("label.output.file"));
        fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Texte", "*.txt"));
        File f = fc.showSaveDialog(outputFileField.getScene().getWindow());
        if (f != null) outputFileField.setText(f.getAbsolutePath());
    }

    @FXML
    private void onRun() {
        if (inputFileField.getText().isBlank()) {
            showError(I18n.get("error.no.input.title"),
                      I18n.get("error.no.input.detail"));
            return;
        }
        if (!lobjCheckBox.isSelected() && !lattrCheckBox.isSelected()) {
            showError(I18n.get("rc.error.no.option.title"),
                      I18n.get("irr.error.no.option.detail"));
            return;
        }

        CommandBuilder builder = new CommandBuilder()
            .command("IRREDUCIBLE")
            .inputFile(inputFileField.getText().trim())
            .listObjects(lobjCheckBox.isSelected())
            .listAttributes(lattrCheckBox.isSelected())
            .groupByClasses(groupCheckBox.isSelected())
            .verbose(verboseCheckBox.isSelected());

        if (!outputFileField.getText().isBlank())
            builder.outputFile(Utilities.resolveOutput(outputFileField.getText().trim(),inputFileField));

        String fmt = inputFormatCombo.getValue();
        if (!"(auto)".equals(fmt)) builder.inputFormat(fmt);
        if ("CSV".equals(fmt))     builder.separator(separatorCombo.getValue());

        String impl = implCombo.getValue();
        if (!"BITSET".equals(impl)) builder.implementation(impl);

        int to = timeoutSpinner.getValue();
        if (to > 0) builder.timeout(to);

        if (onRun != null) onRun.accept(builder);
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private FileChooser buildContextChooser(String title) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("SLF (HTK)",              "*.slf"),
            new FileChooser.ExtensionFilter("CEX (ConExp)",     "*.cex"),
            new FileChooser.ExtensionFilter("CXT (Burmeister)", "*.cxt"),
            new FileChooser.ExtensionFilter("XML (Galicia)",    "*.xml"),
            new FileChooser.ExtensionFilter("CSV",              "*.csv"),
            new FileChooser.ExtensionFilter(I18n.get("filter.all"), "*.*")
        );
        return fc;
    }

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
        if (path!=null && !path.isBlank()) inputFileField.setText(path);
    }
    public String getInputFile() {
        return inputFileField.getText();
    }
    }
