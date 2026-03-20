package fr.lirmm.fca4j.ui.controller;

import fr.lirmm.fca4j.ui.model.CommandBuilder;
import fr.lirmm.fca4j.ui.model.CommandDescriptor;
import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.I18n;
import java.nio.file.Path;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Contrôleur du panneau de paramètres pour CLARIFY et REDUCE.
 * Ces deux commandes prennent un contexte formel en entrée
 * et produisent un contexte formel en sortie.
 */
public class ReduceClarifyController implements Initializable {


    // ── Bouton édition ────────────────────────────────────────────────────────
    @FXML private Button           editInputButton;
    private Consumer<Path>         openInEditor;

    // ── TitledPanes et bouton Run ─────────────────────────────────────────────
    @FXML private TitledPane       inputPane;
    @FXML private TitledPane       outputPane;
    @FXML private TitledPane       operationPane;
    @FXML private TitledPane       advancedPane;
    @FXML private Button           runButton;

    // ── Entrée ────────────────────────────────────────────────────────────────
    @FXML private TextField        inputFileField;
    @FXML private ComboBox<String> inputFormatCombo;
    @FXML private Label            separatorLabel;
    @FXML private ComboBox<String> separatorCombo;

    // ── Sortie ────────────────────────────────────────────────────────────────
    @FXML private TextField        outputFileField;
    @FXML private ComboBox<String> outputFormatCombo;
    @FXML private Label            outSeparatorLabel;
    @FXML private ComboBox<String> outSeparatorCombo;

    // ── Opération ─────────────────────────────────────────────────────────────
    @FXML private CheckBox         xoCheckBox;   // -xo : objets
    @FXML private CheckBox         xaCheckBox;   // -xa : attributs
    @FXML private CheckBox         groupCheckBox; // -u  : REDUCE seulement

    // ── Options avancées ──────────────────────────────────────────────────────
    @FXML private Spinner<Integer> timeoutSpinner;
    @FXML private CheckBox         verboseCheckBox;

    // ── État ──────────────────────────────────────────────────────────────────
    private CommandDescriptor        descriptor;
    private Consumer<CommandBuilder> onRun;

    // Formats communs aux deux commandes (contexte → contexte)
    private static final java.util.List<String> CONTEXT_FORMATS =
        java.util.List.of("(auto)", "CXT", "SLF", "XML", "CEX", "CSV");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        inputFormatCombo.getItems().addAll(CONTEXT_FORMATS);
        inputFormatCombo.setValue("(auto)");

        outputFormatCombo.getItems().addAll(CONTEXT_FORMATS.subList(1, CONTEXT_FORMATS.size()));
        outputFormatCombo.setValue("CXT");

        separatorCombo.getItems().addAll("COMMA", "SEMICOLON", "TAB");
        separatorCombo.setValue("COMMA");
        outSeparatorCombo.getItems().addAll("COMMA", "SEMICOLON", "TAB");
        outSeparatorCombo.setValue("COMMA");

        // Afficher séparateur uniquement si format CSV
        separatorLabel.setVisible(false);
        separatorCombo.setVisible(false);
        outSeparatorLabel.setVisible(false);
        outSeparatorCombo.setVisible(false);

        inputFormatCombo.valueProperty().addListener((obs, old, val) -> {
            boolean csv = "CSV".equals(val);
            separatorLabel.setVisible(csv);
            separatorCombo.setVisible(csv);
        });
        outputFormatCombo.valueProperty().addListener((obs, old, val) -> {
            boolean csv = "CSV".equals(val);
            outSeparatorLabel.setVisible(csv);
            outSeparatorCombo.setVisible(csv);
        });

        timeoutSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 3600, 0, 10));
    }

    public void configure(CommandDescriptor desc, Consumer<CommandBuilder> onRun,
                          Consumer<Path> openInEditor) {
        this.openInEditor = openInEditor;
        this.descriptor = desc;
        this.onRun      = onRun;

        boolean isReduce = "REDUCE".equals(desc.getName());

        // Titres via I18n
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

        // Labels de l'opération selon la commande
        xoCheckBox.setText(isReduce
            ? I18n.get("rc.reduce.objects")
            : I18n.get("rc.clarify.objects"));
        xaCheckBox.setText(isReduce
            ? I18n.get("rc.reduce.attributes")
            : I18n.get("rc.clarify.attributes"));

        // Option -u : REDUCE seulement
        groupCheckBox.setVisible(isReduce);
        groupCheckBox.setManaged(isReduce);
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
            openInEditor.accept(java.nio.file.Path.of(path));
    }

    @FXML
    private void onBrowseInput() {
        FileChooser fc = buildContextChooser(I18n.get("label.input.file"));
        File f = fc.showOpenDialog(inputFileField.getScene().getWindow());
        if (f != null) {
            inputFileField.setText(f.getAbsolutePath());
            AppPreferences.setLastDirectory(f.getParent());
            autoDetectFormat(f.getName(), inputFormatCombo);
            if (outputFileField.getText().isBlank()) {
                String base = f.getAbsolutePath().replaceAll("\\.[^.]+$", "");
                String cmd  = descriptor.getName().toLowerCase();
                outputFileField.setText(base + "-" + cmd + ".cxt");
            }
        }
    }

    @FXML
    private void onBrowseOutput() {
        FileChooser fc = buildContextChooser(I18n.get("label.output.file"));
        File f = fc.showSaveDialog(outputFileField.getScene().getWindow());
        if (f != null) {
            outputFileField.setText(f.getAbsolutePath());
            AppPreferences.setLastDirectory(f.getParent());
            autoDetectFormat(f.getName(), outputFormatCombo);
        }
    }

    @FXML
    private void onRun() {
        if (inputFileField.getText().isBlank()) {
            showError(I18n.get("error.no.input.title"),
                      I18n.get("error.no.input.detail"));
            return;
        }
        if (!xoCheckBox.isSelected() && !xaCheckBox.isSelected()) {
            showError(I18n.get("rc.error.no.option.title"),
                      I18n.get("rc.error.no.option.detail"));
            return;
        }

        CommandBuilder builder = new CommandBuilder()
            .command(descriptor.getName())
            .inputFile(inputFileField.getText().trim())
            .clarifyObjects(xoCheckBox.isSelected())
            .clarifyAttributes(xaCheckBox.isSelected())
            .verbose(verboseCheckBox.isSelected());

        // Format d'entrée
        String inFmt = inputFormatCombo.getValue();
        if (!"(auto)".equals(inFmt)) builder.inputFormat(inFmt);
        if ("CSV".equals(inFmt))     builder.separator(separatorCombo.getValue());

        // Fichier et format de sortie
        if (!outputFileField.getText().isBlank()) {
            builder.outputFile(outputFileField.getText().trim());
            String outFmt = outputFormatCombo.getValue();
            if (!"XML".equals(outFmt)) builder.outputFormat(outFmt);
            if ("CSV".equals(outFmt))  builder.separator(outSeparatorCombo.getValue());
        }

        // Option -u (REDUCE uniquement)
        if ("REDUCE".equals(descriptor.getName()) && groupCheckBox.isSelected())
            builder.groupByClasses(true);

        // Timeout
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

    private void autoDetectFormat(String filename, ComboBox<String> combo) {
        String lower = filename.toLowerCase();
        if      (lower.endsWith(".cxt")) combo.setValue("CXT");
        else if (lower.endsWith(".slf")) combo.setValue("SLF");
        else if (lower.endsWith(".xml")) combo.setValue("XML");
        else if (lower.endsWith(".cex")) combo.setValue("CEX");
        else if (lower.endsWith(".csv")) combo.setValue("CSV");
        else if (combo.getItems().contains("(auto)")) combo.setValue("(auto)");
    }

    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg);
        a.showAndWait();
    }
}
