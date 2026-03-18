package fr.lirmm.fca4j.ui.controller;

import fr.lirmm.fca4j.ui.model.CommandBuilder;
import fr.lirmm.fca4j.ui.model.CommandDescriptor;
import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.I18n;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class LatticeAocController implements Initializable {

    // ── TitledPanes et bouton Run (titres gérés par I18n) ─────────────────────
    @FXML private TitledPane       inputPane;
    @FXML private TitledPane       outputPane;
    @FXML private TitledPane       algoPane;
    @FXML private TitledPane       graphvizPane;
    @FXML private TitledPane       advancedPane;
    @FXML private Button           runButton;

    // ── Fichiers ──────────────────────────────────────────────────────────────
    @FXML private TextField        inputFileField;
    @FXML private ComboBox<String> inputFormatCombo;
    @FXML private Label            separatorLabel;
    @FXML private ComboBox<String> separatorCombo;
    @FXML private TextField        outputFileField;
    @FXML private ComboBox<String> outputFormatCombo;

    // ── Algorithme ────────────────────────────────────────────────────────────
    @FXML private ComboBox<String> algoCombo;
    @FXML private Label            icebergLabel;
    @FXML private Spinner<Integer> icebergSpinner;

    // ── GraphViz ──────────────────────────────────────────────────────────────
    @FXML private CheckBox         dotCheckBox;
    @FXML private TextField        dotFileField;
    @FXML private Button           dotBrowseButton;
    @FXML private ComboBox<String> displayModeCombo;
    @FXML private CheckBox         stabilityCheckBox;

    // ── Options avancées ──────────────────────────────────────────────────────
    @FXML private ComboBox<String> implCombo;
    @FXML private Spinner<Integer> timeoutSpinner;
    @FXML private CheckBox         verboseCheckBox;

    // ── État ──────────────────────────────────────────────────────────────────
    private CommandDescriptor        descriptor;
    private Consumer<CommandBuilder> onRun;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        inputFormatCombo.getItems().addAll(
            I18n.get("format.auto"), "CXT", "SLF", "XML", "CEX", "CSV");
        inputFormatCombo.setValue(I18n.get("format.auto"));

        outputFormatCombo.getItems().addAll("XML", "JSON");
        outputFormatCombo.setValue("XML");

        displayModeCombo.getItems().addAll("SIMPLIFIED", "FULL", "MINIMAL");
        displayModeCombo.setValue("SIMPLIFIED");

        implCombo.getItems().addAll(
            "BITSET", "ROARING_BITMAP", "SPARSE_BITSET",
            "HASHSET", "TREESET", "INT_ARRAY", "ARRAYLIST", "BOOL_ARRAY");
        implCombo.setValue("BITSET");

        separatorCombo.getItems().addAll("COMMA", "SEMICOLON", "TAB");
        separatorCombo.setValue("COMMA");
        separatorLabel.setVisible(false);
        separatorCombo.setVisible(false);

        timeoutSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 3600, 0, 10));
        icebergSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 50, 5));

        // Contrôles DOT désactivés jusqu'à ce que la case soit cochée
        dotFileField.setDisable(true);
        dotBrowseButton.setDisable(true);
        displayModeCombo.setDisable(true);
        stabilityCheckBox.setDisable(true);
        dotCheckBox.selectedProperty().addListener((obs, old, val) -> {
            dotFileField.setDisable(!val);
            dotBrowseButton.setDisable(!val);
            displayModeCombo.setDisable(!val);
            stabilityCheckBox.setDisable(!val);
            if (val && dotFileField.getText().isBlank()
                    && !outputFileField.getText().isBlank())
                dotFileField.setText(outputFileField.getText() + ".dot");
        });

        // Zone Iceberg visible seulement si algo = ICEBERG
        algoCombo.valueProperty().addListener((obs, old, val) -> {
            boolean isIceberg = "ICEBERG".equals(val);
            icebergLabel.setVisible(isIceberg);
            icebergSpinner.setVisible(isIceberg);
        });

        // Séparateur visible seulement si format = CSV
        inputFormatCombo.valueProperty().addListener((obs, old, val) -> {
            boolean isCsv = "CSV".equals(val);
            separatorLabel.setVisible(isCsv);
            separatorCombo.setVisible(isCsv);
        });
    }

    /**
     * Configure le panneau pour LATTICE ou AOCPOSET.
     * Appelé depuis MainController après chargement du FXML.
     */
    public void configure(CommandDescriptor desc, Consumer<CommandBuilder> onRun) {
        this.descriptor = desc;
        this.onRun      = onRun;

        // Titres des sections via I18n
        inputPane.setText(I18n.get("section.input"));
        outputPane.setText(I18n.get("section.output"));
        algoPane.setText(I18n.get("section.algorithm"));
        graphvizPane.setText(I18n.get("section.graphviz"));
        advancedPane.setText(I18n.get("section.advanced"));
        runButton.setText(I18n.get("button.run"));

        // Algorithmes spécifiques à la commande
        algoCombo.getItems().setAll(desc.getAlgorithms());
        algoCombo.setValue(desc.getDefaultAlgorithm());

        // Zone Iceberg : LATTICE seulement
        icebergLabel.setVisible(false);
        icebergSpinner.setVisible(false);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    @FXML
    private void onBrowseInput() {
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.get("label.input.file"));
        fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter(
                "FCA contexts", "*.cxt", "*.slf", "*.xml", "*.cex", "*.csv"),
            new FileChooser.ExtensionFilter("*.*", "*.*")
        );
        File f = fc.showOpenDialog(inputFileField.getScene().getWindow());
        if (f != null) {
            inputFileField.setText(f.getAbsolutePath());
            AppPreferences.setLastDirectory(f.getParent());
            autoDetectFormat(f.getName());
            if (outputFileField.getText().isBlank()) {
                String base = f.getAbsolutePath().replaceAll("\\.[^.]+$", "");
                outputFileField.setText(base + "-result.xml");
            }
        }
    }

    @FXML
    private void onBrowseOutput() {
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.get("label.output.file"));
        fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("XML", "*.xml"),
            new FileChooser.ExtensionFilter("JSON", "*.json")
        );
        File f = fc.showSaveDialog(outputFileField.getScene().getWindow());
        if (f != null) {
            outputFileField.setText(f.getAbsolutePath());
            String name = f.getName().toLowerCase();
            if (name.endsWith(".json")) outputFormatCombo.setValue("JSON");
            else                        outputFormatCombo.setValue("XML");
        }
    }

    @FXML
    private void onBrowseDot() {
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.get("label.dot.file"));
        fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("GraphViz DOT", "*.dot"));
        File f = fc.showSaveDialog(dotFileField.getScene().getWindow());
        if (f != null) dotFileField.setText(f.getAbsolutePath());
    }

    @FXML
    private void onRun() {
        if (inputFileField.getText().isBlank()) {
            showError(I18n.get("error.no.input.title"),
                      I18n.get("error.no.input.detail"));
            return;
        }

        CommandBuilder builder = new CommandBuilder()
            .command(descriptor.getName())
            .inputFile(inputFileField.getText().trim())
            .algorithm(algoCombo.getValue())
            .outputFormat(outputFormatCombo.getValue())
            .verbose(verboseCheckBox.isSelected())
            .implementation(implCombo.getValue());

        if (!outputFileField.getText().isBlank())
            builder.outputFile(outputFileField.getText().trim());

        String fmt = inputFormatCombo.getValue();
        if (!I18n.get("format.auto").equals(fmt)) builder.inputFormat(fmt);
        if ("CSV".equals(fmt))                    builder.separator(separatorCombo.getValue());

        if ("ICEBERG".equals(algoCombo.getValue()))
            builder.icebergPercent(icebergSpinner.getValue());

        if (dotCheckBox.isSelected() && !dotFileField.getText().isBlank())
            builder.dotFile(dotFileField.getText().trim())
                   .displayMode(displayModeCombo.getValue())
                   .stability(stabilityCheckBox.isSelected());

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
        else inputFormatCombo.setValue(I18n.get("format.auto"));
    }

    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    public String getInputFile() { return inputFileField.getText(); }
    public String getDotFile() {
        return dotCheckBox.isSelected() ? dotFileField.getText() : null;
    }
}
