package fr.lirmm.fca4j.ui.controller;

import fr.lirmm.fca4j.ui.model.CommandBuilder;
import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.I18n;
import fr.lirmm.fca4j.ui.util.Utilities;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class RcaCommandController implements Initializable {
	private static final String P = "RCA.";
	
    @FXML private TitledPane inputPane;
    @FXML private TitledPane outputPane;
    @FXML private TitledPane algoPane;
    @FXML private TitledPane namingPane;
    @FXML private TitledPane graphvizPane;
    @FXML private TitledPane datalogPane;
    @FXML private TitledPane advancedPane;
    @FXML private TitledPane resultsPane;
    @FXML private Button     editFamilyButton;

    // Entrée
    @FXML private TextField        familyFileField;
    @FXML private ComboBox<String> familyFormatCombo;

    // Sortie
    @FXML private TextField        outputFolderField;
    @FXML private CheckBox         storeExtendedFamily;
    @FXML private CheckBox         storeExtendedEachStep;
    @FXML private CheckBox         buildXml;
    @FXML private CheckBox         addFullExtents;
    @FXML private CheckBox         addFullIntents;
    @FXML private Spinner<Integer> limitStepsSpinner;

    // Algorithme
    @FXML private ComboBox<String> algoCombo;
    @FXML private Label            icebergLabel;
    @FXML private Spinner<Integer> icebergSpinner;

    // Renommage
    @FXML private CheckBox renameRA;
    @FXML private CheckBox renameRAI;
    @FXML private CheckBox renameRI;
    @FXML private CheckBox nativeOnly;
    @FXML private CheckBox cleanOption;

    // GraphViz
    @FXML private CheckBox         buildDot;
    @FXML private Label            displayModeLabel;
    @FXML private ComboBox<String> displayModeCombo;
    @FXML private CheckBox         stability;
    @FXML private CheckBox         buildJson;

    // Datalog
    @FXML private TextField datalogFolderField;
    @FXML private TextField datalogFileField;
    @FXML private CheckBox  noDirectSiblings;

    // Avancé
    @FXML private Spinner<Integer> timeoutSpinner;
    @FXML private CheckBox         verboseCheckBox;

    // Résultats
    @FXML private Label rcavizFileLabel;
    @FXML private Label            resultsFolderLabel;
    @FXML private ListView<String> dotFilesList;

    private Consumer<CommandBuilder> onRun;
    private Consumer<Path>           openInFamilyEditor;
    private Consumer<Path>           openInGraph;
    private Path                     outputFolder;
    private Consumer<Path> openInRcaviz;
    private Consumer<Path> onFamilyChanged;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        familyFormatCombo.getItems().addAll("RCFT", "RCFGZ", "RCFAL");
        familyFormatCombo.setValue("RCFT");

        algoCombo.getItems().addAll(
            "HERMES", "ARES", "CERES", "PLUTON",
            "ADD_EXTENT", "ADD_INTENT", "ICEBERG");
        algoCombo.setValue("HERMES");
        algoCombo.valueProperty().addListener((obs, old, val) -> {
            boolean iceberg = "ICEBERG".equals(val);
            icebergLabel.setVisible(iceberg);
            icebergSpinner.setVisible(iceberg);
        });
        icebergLabel.setVisible(false);
        icebergSpinner.setVisible(false);

        displayModeCombo.getItems().addAll("SIMPLIFIED", "FULL", "MINIMAL");
        displayModeCombo.setValue("SIMPLIFIED");

        // Options GraphViz actives seulement si -dot coché
        displayModeLabel.setDisable(true);
        displayModeCombo.setDisable(true);
        stability.setDisable(true);
        buildDot.selectedProperty().addListener((obs, old, val) -> {
            displayModeLabel.setDisable(!val);
            displayModeCombo.setDisable(!val);
            stability.setDisable(!val);
        });

        // -na actif seulement si une option de renommage est cochée
        nativeOnly.setDisable(true);
        renameRA.selectedProperty().addListener((obs, o, v)  -> updateNativeOnly());
        renameRAI.selectedProperty().addListener((obs, o, v) -> updateNativeOnly());
        renameRI.selectedProperty().addListener((obs, o, v)  -> updateNativeOnly());

        // Renommage mutuellement exclusif
        renameRA.selectedProperty().addListener((obs, o, v)  -> {
            if (v) { renameRAI.setSelected(false); renameRI.setSelected(false); } });
        renameRAI.selectedProperty().addListener((obs, o, v) -> {
            if (v) { renameRA.setSelected(false); renameRI.setSelected(false); } });
        renameRI.selectedProperty().addListener((obs, o, v)  -> {
            if (v) { renameRA.setSelected(false); renameRAI.setSelected(false); } });

        limitStepsSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 1000, 0, 1));
        icebergSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 50, 5));
        timeoutSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 3600, 0, 10));

        dotFilesList.getSelectionModel().selectedItemProperty()
            .addListener((obs, old, val) -> {
                if (val != null && outputFolder != null && openInGraph != null)
                    openInGraph.accept(outputFolder.resolve(val));
            });
        algoCombo.valueProperty().addListener((obs, old, val) -> {
            boolean iceberg = "ICEBERG".equals(val);
            icebergLabel.setVisible(iceberg);
            icebergSpinner.setVisible(iceberg);
            updateAlgoTitle(); // ← ajouter
        });

        icebergSpinner.valueProperty().addListener((obs, old, val) ->
            updateAlgoTitle()); // ← ajouter

        // Appel initial pour afficher la valeur au chargement
        Platform.runLater(this::updateAlgoTitle);        
        
        Utilities.bindPathTooltip(familyFileField);
        Utilities.bindPathTooltip(outputFolderField);
    }

    private void updateNativeOnly() {
        boolean any = renameRA.isSelected() || renameRAI.isSelected() || renameRI.isSelected();
        nativeOnly.setDisable(!any);
        if (!any) nativeOnly.setSelected(false);
    }

    public void configure(Consumer<CommandBuilder> onRun,
                          Consumer<Path> openInFamilyEditor,
                          Consumer<Path> openInGraph,
                          Consumer<Path> openInRcaviz, 
                          Consumer<Path> onFamilyChanged) {
        this.onRun              = onRun;
        this.openInFamilyEditor = openInFamilyEditor;
        this.openInGraph        = openInGraph;
        this.openInRcaviz = openInRcaviz;
        this.onFamilyChanged = onFamilyChanged;

        inputPane.setText(I18n.get("section.input"));
        outputPane.setText(I18n.get("rca.section.output"));
        algoPane.setText(I18n.get("section.algorithm"));
        namingPane.setText(I18n.get("rca.section.naming"));
        graphvizPane.setText(I18n.get("section.graphviz"));
        datalogPane.setText(I18n.get("rca.section.datalog"));
        advancedPane.setText(I18n.get("section.advanced"));
        resultsPane.setText(I18n.get("rca.section.results"));

        FontIcon editIcon = new FontIcon(Material2AL.EDIT);
        editIcon.setIconSize(14);
        editFamilyButton.setGraphic(editIcon);
        editFamilyButton.setText("");
        editFamilyButton.setTooltip(new Tooltip(I18n.get("rca.btn.open.family.editor")));
        loadPrefs();
    }
    @FXML private void onOpenInRcaviz() {
        if (outputFolder == null || !buildJson.isSelected()) {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.setHeaderText(null);
            a.setContentText(I18n.get("rcaviz.no.json.file"));
            a.showAndWait();
            return;
        }
        // Chercher le fichier JSON dans outputFolder
        try {
            java.util.Optional<Path> jsonFile = java.nio.file.Files.list(outputFolder)
                .filter(p -> p.toString().endsWith(".json"))
                .sorted()
                .reduce((a, b) -> b); // prendre le dernier
            if (jsonFile.isPresent() && openInRcaviz != null) {
                rcavizFileLabel.setText(jsonFile.get().getFileName().toString());
               openInRcaviz.accept(jsonFile.get());
            }
            else {
                rcavizFileLabel.setText("");
               Alert a = new Alert(Alert.AlertType.WARNING);
                a.setHeaderText(null);
                a.setContentText(I18n.get("rcaviz.no.json.file"));
                a.showAndWait();
            }
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
        }
    }
    @FXML private void onBrowseFamily() {
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.get("rca.browse.family"));
        fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("RCFT", "*.rcft"),
            new FileChooser.ExtensionFilter("RCFGZ", "*.rcfgz"),
            new FileChooser.ExtensionFilter("RCFAL", "*.rcfal"),
            new FileChooser.ExtensionFilter(I18n.get("filter.all"), "*.*"));
        File f = fc.showOpenDialog(familyFileField.getScene().getWindow());
        if (f != null) {
            familyFileField.setText(f.getAbsolutePath());
            AppPreferences.setLastDirectory(f.getParent());
            setFamilyFile(f.toPath());
            if (onFamilyChanged != null) onFamilyChanged.accept(f.toPath());
            if (outputFolderField.getText().isBlank()) {
                Path def = defaultOutputFolder();
                if (def != null) outputFolderField.setText(def.toString());
            }
        }
    }
    private void updateAlgoTitle() {
        String algo = algoCombo.getValue();
        if (algo == null) return;
        String title = I18n.get("section.algorithm") + " : " + algo;
        if ("ICEBERG".equals(algo))
            title += " (" + icebergSpinner.getValue() + "%)";
        algoPane.setText(title);
    }
    @FXML private void onEditFamily() {
        String path = familyFileField.getText().trim();
        if (path.isBlank()) {
            showError(I18n.get("rca.error.no.family.title"),
                      I18n.get("rca.error.no.family.detail")); return;
        }
        if (openInFamilyEditor != null) openInFamilyEditor.accept(Path.of(path));
    }

    @FXML private void onBrowseOutputFolder() {
        if (outputFolderField.getText().isBlank()) {
            Path def = defaultOutputFolder();
            if (def != null) outputFolderField.setText(def.toString());
        }
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle(I18n.get("rca.browse.output"));
        String cur = outputFolderField.getText().trim();
        File init = cur.isBlank() ? new File(AppPreferences.getLastDirectory())
                                  : new File(cur).getParentFile();
        if (init != null && init.exists()) dc.setInitialDirectory(init);
        File f = dc.showDialog(outputFolderField.getScene().getWindow());
        if (f != null) outputFolderField.setText(f.getAbsolutePath());
    }

    @FXML private void onBrowseDatalogFolder() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle(I18n.get("rca.browse.datalog.folder"));
        dc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
        File f = dc.showDialog(datalogFolderField.getScene().getWindow());
        if (f != null) datalogFolderField.setText(f.getAbsolutePath());
    }

    @FXML private void onBrowseDatalogFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.get("rca.browse.datalog.file"));
        fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Datalog", "*.dlgp", "*.dl"));
        File f = fc.showSaveDialog(datalogFileField.getScene().getWindow());
        if (f != null) datalogFileField.setText(f.getAbsolutePath());
    }

    @FXML 
    public void onRun() {
    	savePrefs();
        if (familyFileField.getText().isBlank()) {
            showError(I18n.get("rca.error.no.family.title"),
                      I18n.get("rca.error.no.family.detail")); return;
        }
        if (outputFolderField.getText().isBlank()) {
            Path def = defaultOutputFolder();
            if (def != null) outputFolderField.setText(def.toString());
        }
        outputFolder = outputFolderField.getText().isBlank()
            ? null : Path.of(outputFolderField.getText().trim());

        CommandBuilder builder = new CommandBuilder()
            .command("RCA")
            .inputFile(familyFileField.getText().trim())
            .verbose(verboseCheckBox.isSelected());

        String fmt = familyFormatCombo.getValue();
        if (!"RCFT".equals(fmt)) builder.familyFormat(fmt);

        if (outputFolder != null) builder.outputFile(Utilities.resolveOutput(outputFolder.toString(),familyFileField));

        int limit = limitStepsSpinner.getValue();
        if (limit > 0) builder.rcaLimit(limit);

        String algo = algoCombo.getValue();
        builder.algorithm(algoCombo.getValue()); 
        if ("ICEBERG".equals(algo)) builder.icebergPercent(icebergSpinner.getValue());

        if (renameRA.isSelected())    builder.rcaRenameRA(true);
        if (renameRAI.isSelected())   builder.rcaRenameRAI(true);
        if (renameRI.isSelected())    builder.rcaRenameRI(true);
        if (nativeOnly.isSelected())  builder.rcaNativeOnly(true);
        if (cleanOption.isSelected()) builder.rcaClean(true);

        if (storeExtendedFamily.isSelected())   builder.rcaStoreExtended(true);
        if (storeExtendedEachStep.isSelected()) builder.rcaStoreExtendedEachStep(true);
        if (buildXml.isSelected())              builder.rcaBuildXml(true);
        if (addFullExtents.isSelected())        builder.rcaFullExtents(true);
        if (addFullIntents.isSelected())        builder.rcaFullIntents(true);

        if (buildDot.isSelected()) {
            builder.rcaBuildDot(true);
            String dm = displayModeCombo.getValue();
            if (!"SIMPLIFIED".equals(dm)) builder.rcaDisplayMode(dm);
            if (stability.isSelected())   builder.rcaStability(true);
        }
        if (buildJson.isSelected()) builder.rcaBuildJson(true);

        if (!datalogFolderField.getText().isBlank())
            builder.rcaDatalogFolder(Utilities.resolveOutput(datalogFolderField.getText().trim(),familyFileField));
        if (!datalogFileField.getText().isBlank())
            builder.rcaDatalogFile(Utilities.resolveOutput(datalogFileField.getText().trim(),familyFileField));
        if (noDirectSiblings.isSelected()) builder.rcaNoDirectSiblings(true);

        int to = timeoutSpinner.getValue();
        if (to > 0) builder.timeout(to);
        
        if (!outputFolderField.getText().isBlank())
            AppPreferences.saveOutputForInput("RCA",
                familyFileField.getText().trim(),
                outputFolderField.getText().trim());
        if (onRun != null) onRun.accept(builder);
    }
    public void scanDotFiles() {
        dotFilesList.getItems().clear();
        if (outputFolder == null || !outputFolder.toFile().exists()) return;
        try {
            java.nio.file.Files.list(outputFolder)
                .filter(p -> p.getFileName().toString().matches("step\\d+\\.dot"))
                .sorted((a, b) -> Integer.compare(
                    extractStepNumber(a.getFileName().toString()),
                    extractStepNumber(b.getFileName().toString())))
                .forEach(p -> dotFilesList.getItems().add(p.getFileName().toString()));
            if (!dotFilesList.getItems().isEmpty()) {
                resultsPane.setExpanded(true);
                resultsFolderLabel.setText(outputFolder.toString());
            }
            // Mettre à jour le label JSON si un fichier existe
            java.nio.file.Files.list(outputFolder)
                .filter(p -> p.toString().endsWith(".json"))
                .sorted()
                .reduce((a, b) -> b)
                .ifPresentOrElse(
                    p -> rcavizFileLabel.setText(p.getFileName().toString()),
                    () -> rcavizFileLabel.setText(""));
        } catch (Exception e) {
            resultsFolderLabel.setText(I18n.get("rca.results.error", e.getMessage()));
        }
    }

    private int extractStepNumber(String filename) {
        try { return Integer.parseInt(filename.replace("step","").replace(".dot","")); }
        catch (NumberFormatException e) { return -1; }
    }

    public void setFamilyFile(Path path) {
        if (path == null) return;
        familyFileField.setText(path.toString());
        String name = path.getFileName().toString().toLowerCase();
        if      (name.endsWith(".rcfgz")) familyFormatCombo.setValue("RCFGZ");
        else if (name.endsWith(".rcfal")) familyFormatCombo.setValue("RCFAL");
        else                              familyFormatCombo.setValue("RCFT");
        String savedFolder = AppPreferences.loadOutputForInput("RCA", path.toString());
        if (!savedFolder.isBlank())
            outputFolderField.setText(savedFolder);
        else if (outputFolderField.getText().isBlank()) {
            Path def = defaultOutputFolder();
            if (def != null) outputFolderField.setText(def.toString());
        }
    }

    private Path defaultOutputFolder() {
        String family = familyFileField.getText().trim();
        if (family.isBlank()) return null;
        Path p = Path.of(family);
        String base = p.getFileName().toString().replaceAll("\\.[^.]+$", "");
        return p.getParent().resolve(base + "-rca");
    }

    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
    private void savePrefs() {
        AppPreferences.saveString(P + "algo",          algoCombo.getValue());
        AppPreferences.saveString(P + "familyFormat",  familyFormatCombo.getValue());
        AppPreferences.saveString(P + "displayMode",   displayModeCombo.getValue());
        AppPreferences.saveBool  (P + "dot",           buildDot.isSelected());
        AppPreferences.saveBool  (P + "json",          buildJson.isSelected());
        AppPreferences.saveBool  (P + "xml",           buildXml.isSelected());
        AppPreferences.saveBool  (P + "stability",     stability.isSelected());
        AppPreferences.saveBool  (P + "fe",            addFullExtents.isSelected());
        AppPreferences.saveBool  (P + "fi",            addFullIntents.isSelected());
        AppPreferences.saveBool  (P + "e",             storeExtendedFamily.isSelected());
        AppPreferences.saveBool  (P + "es",            storeExtendedEachStep.isSelected());
        AppPreferences.saveBool  (P + "ra",            renameRA.isSelected());
        AppPreferences.saveBool  (P + "rai",           renameRAI.isSelected());
        AppPreferences.saveBool  (P + "ri",            renameRI.isSelected());
        AppPreferences.saveBool  (P + "na",            nativeOnly.isSelected());
        AppPreferences.saveBool  (P + "clean",         cleanOption.isSelected());
        AppPreferences.saveBool  (P + "nds",           noDirectSiblings.isSelected());
        AppPreferences.saveBool  (P + "verbose",       verboseCheckBox.isSelected());
        AppPreferences.saveInt   (P + "timeout",       timeoutSpinner.getValue());
        AppPreferences.saveInt   (P + "limit",         limitStepsSpinner.getValue());
        AppPreferences.saveInt   (P + "iceberg",       icebergSpinner.getValue());
    }
    private void loadPrefs() {
        String algo = AppPreferences.loadString(P + "algo", "HERMES");
        if (algoCombo.getItems().contains(algo)) algoCombo.setValue(algo);

        String fmt = AppPreferences.loadString(P + "familyFormat", "RCFT");
        if (familyFormatCombo.getItems().contains(fmt)) familyFormatCombo.setValue(fmt);

        String dm = AppPreferences.loadString(P + "displayMode", "SIMPLIFIED");
        if (displayModeCombo.getItems().contains(dm)) displayModeCombo.setValue(dm);

        buildDot.setSelected(AppPreferences.loadBool(P + "dot",       false));
        buildJson.setSelected(AppPreferences.loadBool(P + "json",      false));
        buildXml.setSelected(AppPreferences.loadBool(P + "xml",        false));
        stability.setSelected(AppPreferences.loadBool(P + "stability", false));
        addFullExtents.setSelected(AppPreferences.loadBool(P + "fe",   false));
        addFullIntents.setSelected(AppPreferences.loadBool(P + "fi",   false));
        storeExtendedFamily.setSelected(AppPreferences.loadBool(P + "e",  false));
        storeExtendedEachStep.setSelected(AppPreferences.loadBool(P + "es", false));
        renameRA.setSelected(AppPreferences.loadBool(P + "ra",         false));
        renameRAI.setSelected(AppPreferences.loadBool(P + "rai",       false));
        renameRI.setSelected(AppPreferences.loadBool(P + "ri",         false));
        nativeOnly.setSelected(AppPreferences.loadBool(P + "na",       false));
        cleanOption.setSelected(AppPreferences.loadBool(P + "clean",   false));
        noDirectSiblings.setSelected(AppPreferences.loadBool(P + "nds", false));
        verboseCheckBox.setSelected(AppPreferences.loadBool(P + "verbose", false));
        timeoutSpinner.getValueFactory().setValue(AppPreferences.loadInt(P + "timeout", 0));
        limitStepsSpinner.getValueFactory().setValue(AppPreferences.loadInt(P + "limit", 0));
        icebergSpinner.getValueFactory().setValue(AppPreferences.loadInt(P + "iceberg", 50));
        Platform.runLater(this::updateAlgoTitle);
    }    
}
