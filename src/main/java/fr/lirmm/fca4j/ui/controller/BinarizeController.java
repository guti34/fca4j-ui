package fr.lirmm.fca4j.ui.controller;

import fr.lirmm.fca4j.ui.model.CommandBuilder;
import fr.lirmm.fca4j.ui.model.CommandDescriptor;
import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.I18n;
import fr.lirmm.fca4j.ui.util.Utilities;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Contrôleur du panneau pour la commande BINARIZE.
 * Transforme une table multivaluée CSV en contexte formel binaire.
 * Les options -excl et -incl peuvent être répétées (listes d'attributs).
 */
public class BinarizeController implements Initializable {
	private static final String P = "BINARIZE.";

    // ── TitledPanes et bouton ─────────────────────────────────────────────────
    @FXML private TitledPane       inputPane;
    @FXML private TitledPane       outputPane;
    @FXML private TitledPane       filterPane;
    @FXML private TitledPane       advancedPane;

    // ── Entrée CSV ────────────────────────────────────────────────────────────
    @FXML private TextField        inputFileField;
    @FXML private ComboBox<String> separatorCombo;

    // ── Sortie ────────────────────────────────────────────────────────────────
    @FXML private TextField        outputFileField;
    @FXML private ComboBox<String> outputFormatCombo;
    @FXML private Label            outSeparatorLabel;
    @FXML private ComboBox<String> outSeparatorCombo;

    // ── Filtres attributs ─────────────────────────────────────────────────────
    @FXML private ListView<String> excludeList;
    @FXML private ListView<String> includeList;
    @FXML private TextField        excludeField;
    @FXML private TextField        includeField;
    @FXML private Button           btnAddExclude;
    @FXML private Button           btnRemoveExclude;
    @FXML private Button           btnAddInclude;
    @FXML private Button           btnRemoveInclude;
    @FXML private Label            excludeLabel;
    @FXML private Label            includeLabel;
    @FXML private Label            filterHintLabel;

    // ── Options avancées ──────────────────────────────────────────────────────
    @FXML private Spinner<Integer> timeoutSpinner;
    @FXML private CheckBox         verboseCheckBox;

    private Consumer<CommandBuilder> onRun;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        separatorCombo.getItems().addAll("COMMA", "SEMICOLON", "TAB");
        separatorCombo.setValue("COMMA");

        outputFormatCombo.getItems().addAll("CXT (Burmeister)", "SLF (HTK)", "CEX (ConExp)","XML (Galicia)",  "CSV");
        outputFormatCombo.setValue("CXT");

        outSeparatorCombo.getItems().addAll("COMMA", "SEMICOLON", "TAB");
        outSeparatorCombo.setValue("COMMA");
        outSeparatorLabel.setVisible(false);
        outSeparatorCombo.setVisible(false);
        outputFormatCombo.valueProperty().addListener((obs, old, val) -> {
            boolean csv = "CSV".equals(val);
            outSeparatorLabel.setVisible(csv);
            outSeparatorCombo.setVisible(csv);
        });

        timeoutSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 3600, 0, 10));

        // Supprimer avec la touche Delete sur les listes
        excludeList.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.DELETE) removeExclude();
        });
        includeList.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.DELETE) removeInclude();
        });

        // Ajouter avec Entrée dans les champs texte
        excludeField.setOnAction(e -> addExclude());
        includeField.setOnAction(e -> addInclude());
        Utilities.bindPathTooltip(inputFileField);
        Utilities.bindPathTooltip(outputFileField);
        }

    public void configure(CommandDescriptor desc, Consumer<CommandBuilder> onRun,
                          Consumer<Path> openInEditor) {
        this.onRun = onRun;

        inputPane.setText(I18n.get("section.input"));
        outputPane.setText(I18n.get("section.output"));
        filterPane.setText(I18n.get("binarize.section.filter"));
        advancedPane.setText(I18n.get("section.advanced"));
         excludeLabel.setText(I18n.get("binarize.exclude.label"));
        includeLabel.setText(I18n.get("binarize.include.label"));
        filterHintLabel.setText(I18n.get("binarize.filter.hint"));

        // Icônes pour les boutons d'ajout/suppression
        setIcon(btnAddExclude,    new FontIcon(Material2AL.ADD),    I18n.get("binarize.btn.add"));
        setIcon(btnRemoveExclude, new FontIcon(Material2MZ.REMOVE), I18n.get("binarize.btn.remove"));
        setIcon(btnAddInclude,    new FontIcon(Material2AL.ADD),    I18n.get("binarize.btn.add"));
        setIcon(btnRemoveInclude, new FontIcon(Material2MZ.REMOVE), I18n.get("binarize.btn.remove"));
        loadPrefs();
    }

    // ── Actions fichiers ──────────────────────────────────────────────────────

    @FXML
    private void onBrowseInput() {
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.get("label.input.file"));
        fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("CSV", "*.csv"),
            new FileChooser.ExtensionFilter(I18n.get("filter.all"), "*.*")
        );
        File f = fc.showOpenDialog(inputFileField.getScene().getWindow());
        if (f != null) {
            inputFileField.setText(f.getAbsolutePath());
            AppPreferences.setLastDirectory(f.getParent());
            if (outputFileField.getText().isBlank()) {
                String base = f.getAbsolutePath().replaceAll("\\.[^.]+$", "");
                outputFileField.setText(base + "-binarized.cxt");
            }
        }
    }

    @FXML
    private void onBrowseOutput() {
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.get("label.output.file"));
        fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("CXT (Burmeister)", "*.cxt"),
           new FileChooser.ExtensionFilter("SLF (HTK)",        "*.slf"),
            new FileChooser.ExtensionFilter("CEX (ConExp)",     "*.cex"),
             new FileChooser.ExtensionFilter("XML (Galicia)",    "*.xml"),
            new FileChooser.ExtensionFilter("CSV",              "*.csv")
        );
        File f = fc.showSaveDialog(outputFileField.getScene().getWindow());
        if (f != null) {
            outputFileField.setText(f.getAbsolutePath());
            String name = f.getName().toLowerCase();
            if      (name.endsWith(".slf")) outputFormatCombo.setValue("SLF");
            else if (name.endsWith(".xml")) outputFormatCombo.setValue("XML");
            else if (name.endsWith(".cex")) outputFormatCombo.setValue("CEX");
            else if (name.endsWith(".csv")) outputFormatCombo.setValue("CSV");
            else                            outputFormatCombo.setValue("CXT");
        }
    }

    // ── Actions listes exclude/include ────────────────────────────────────────

    @FXML private void onAddExclude()    { addExclude(); }
    @FXML private void onRemoveExclude() { removeExclude(); }
    @FXML private void onAddInclude()    { addInclude(); }
    @FXML private void onRemoveInclude() { removeInclude(); }

    private void addExclude() {
        String val = excludeField.getText().trim();
        if (!val.isBlank() && !excludeList.getItems().contains(val)) {
            excludeList.getItems().add(val);
            excludeField.clear();
        }
    }

    private void removeExclude() {
        int sel = excludeList.getSelectionModel().getSelectedIndex();
        if (sel >= 0) excludeList.getItems().remove(sel);
    }

    private void addInclude() {
        String val = includeField.getText().trim();
        if (!val.isBlank() && !includeList.getItems().contains(val)) {
            includeList.getItems().add(val);
            includeField.clear();
        }
    }

    private void removeInclude() {
        int sel = includeList.getSelectionModel().getSelectedIndex();
        if (sel >= 0) includeList.getItems().remove(sel);
    }

    // ── Exécution ─────────────────────────────────────────────────────────────

    @FXML
    public void onRun() {
    	savePrefs();
        if (inputFileField.getText().isBlank()) {
            showError(I18n.get("error.no.input.title"),
                      I18n.get("error.no.input.detail"));
            return;
        }

        CommandBuilder builder = new CommandBuilder()
            .command("BINARIZE")
            .inputFile(inputFileField.getText().trim())
            .inputFormat("CSV")
            .separator(separatorCombo.getValue())
            .verbose(verboseCheckBox.isSelected());

        if (!outputFileField.getText().isBlank()) {
            builder.outputFile(Utilities.resolveOutput(outputFileField.getText().trim(),inputFileField));
            String fmt = outputFormatCombo.getValue();
            if (!"CXT".equals(fmt)) builder.outputFormat(fmt);
            if ("CSV".equals(fmt))  builder.separator(outSeparatorCombo.getValue());
        }

        if (!excludeList.getItems().isEmpty())
            builder.excludeAttrs(new ArrayList<>(excludeList.getItems()));
        if (!includeList.getItems().isEmpty())
            builder.includeAttrs(new ArrayList<>(includeList.getItems()));

        int to = timeoutSpinner.getValue();
        if (to > 0) builder.timeout(to);

        if (onRun != null) onRun.accept(builder);
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private void setIcon(Button btn, FontIcon icon, String tooltip) {
        icon.setIconSize(14);
        btn.setGraphic(icon);
        btn.setText("");
        btn.setTooltip(new Tooltip(tooltip));
    }

    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg);
        a.showAndWait();
    }
    private void savePrefs() {
        AppPreferences.saveString(P + "separator",    separatorCombo.getValue());
        AppPreferences.saveString(P + "outputFormat", outputFormatCombo.getValue());
        AppPreferences.saveString(P + "outSeparator", outSeparatorCombo.getValue());
        AppPreferences.saveBool  (P + "verbose",      verboseCheckBox.isSelected());
        AppPreferences.saveInt   (P + "timeout",      timeoutSpinner.getValue());
        // Listes exclude/include — sérialisées en chaîne séparée par |
        AppPreferences.saveString(P + "excludeList",
            String.join("|", excludeList.getItems()));
        AppPreferences.saveString(P + "includeList",
            String.join("|", includeList.getItems()));
    }

    private void loadPrefs() {
        String sep = AppPreferences.loadString(P + "separator", "COMMA");
        if (separatorCombo.getItems().contains(sep)) separatorCombo.setValue(sep);

        String fmt = AppPreferences.loadString(P + "outputFormat", "CXT");
        if (outputFormatCombo.getItems().contains(fmt)) outputFormatCombo.setValue(fmt);

        String outSep = AppPreferences.loadString(P + "outSeparator", "COMMA");
        if (outSeparatorCombo.getItems().contains(outSep)) outSeparatorCombo.setValue(outSep);

        verboseCheckBox.setSelected(AppPreferences.loadBool(P + "verbose", false));
        timeoutSpinner.getValueFactory().setValue(AppPreferences.loadInt(P + "timeout", 0));

        // Restaurer les listes
        String excl = AppPreferences.loadString(P + "excludeList", "");
        excludeList.getItems().clear();
        if (!excl.isBlank())
            excludeList.getItems().addAll(excl.split("\\|"));

        String incl = AppPreferences.loadString(P + "includeList", "");
        includeList.getItems().clear();
        if (!incl.isBlank())
            includeList.getItems().addAll(incl.split("\\|"));
    }
    public String getSeparator() {
        return separatorCombo.getValue();
    }
}
