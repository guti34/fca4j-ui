package fr.lirmm.fca4j.ui.controller;

import fr.lirmm.fca4j.ui.model.CommandBuilder;
import fr.lirmm.fca4j.ui.model.CommandDescriptor;
import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.I18n;
import fr.lirmm.fca4j.ui.util.Utilities;

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
 * Contrôleur du panneau de paramètres pour RULEBASIS et DBASIS.
 */
public class RuleBasisController extends AbstractCommandController implements Initializable {

	@FXML
	private TitledPane inputPane;
	@FXML
	private TitledPane outputPane;
	@FXML
	private TitledPane threadPane;
	@FXML
	private TitledPane advancedPane;
	@FXML
	private Button runButton;

	// ── Bouton édition ────────────────────────────────────────────────────────
	@FXML
	private Button editInputButton;

	// ── Fichiers ──────────────────────────────────────────────────────────────
	@FXML
	private TextField inputFileField;
	@FXML
	private ComboBox<String> inputFormatCombo;
	@FXML
	private TextField outputFileField;
	@FXML
	private ComboBox<String> outputFormatCombo;

	// ── Algorithme (RULEBASIS seulement) ──────────────────────────────────────
	@FXML
	private TitledPane algoPane;
	@FXML
	private ComboBox<String> algoCombo;
	@FXML
	private Label closureLabel;
	@FXML
	private ComboBox<String> closureCombo;
	@FXML
	private CheckBox clarifyCheckBox;

	// ── Multithreading ────────────────────────────────────────────────────────
	@FXML
	private ComboBox<String> poolModeCombo;
	@FXML
	private Label thresholdLabel;
	@FXML
	private Spinner<Integer> thresholdSpinner;

	// ── Options DBASIS ────────────────────────────────────────────────────────
	@FXML
	private TitledPane dbasisPane;
	@FXML
	private Spinner<Integer> minSupportSpinner;

	// ── Options communes ──────────────────────────────────────────────────────
	@FXML
	private CheckBox sortBySupportCheckBox;
	@FXML
	private TextField reportFileField;
	@FXML
	private TextField implFolderField;
	@FXML
	private ComboBox<String> implCombo;
	@FXML
	private Spinner<Integer> timeoutSpinner;
	@FXML
	private CheckBox verboseCheckBox;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		inputFormatCombo.getItems().addAll("(auto)", "CXT", "SLF", "CEX", "XML", "CSV");
		inputFormatCombo.setValue("(auto)");


		outputFormatCombo.getItems().addAll("TXT", "JSON", "XML", "DATALOG");
		outputFormatCombo.setValue("TXT");

		closureCombo.getItems().addAll("BASIC", "WITH_HISTORY");
		closureCombo.setValue("BASIC");

		thresholdSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10000, 50, 10));
		thresholdLabel.setVisible(false);
		thresholdSpinner.setVisible(false);
		poolModeCombo.valueProperty().addListener((obs, old, val) -> {
			boolean mt = !"MONO".equals(val);
			boolean rb = descriptor != null && "RULEBASIS".equals(descriptor.getName());
			thresholdLabel.setVisible(mt && rb);
			thresholdSpinner.setVisible(mt && rb);
		});

		minSupportSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100000, 0, 1));

		implCombo.getItems().addAll("BITSET", "ROARING_BITMAP", "SPARSE_BITSET", "TREESET", "INT_ARRAY", "ARRAYLIST",
				"BOOL_ARRAY");
		implCombo.setValue("BITSET");

		timeoutSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 3600, 0, 10));
		Utilities.bindPathTooltip(inputFileField);
		Utilities.bindPathTooltip(outputFileField);
		Utilities.bindPathTooltip(reportFileField);
		Utilities.bindPathTooltip(implFolderField);
		}

	public void configure(CommandDescriptor desc, Consumer<CommandBuilder> onRun, Consumer<Path> openInEditor,
			Consumer<String> onInputChanged) {

		configureBase(desc, onRun, openInEditor, onInputChanged, editInputButton);

		inputPane.setText(I18n.get("section.input"));
		outputPane.setText(I18n.get("section.output"));
		threadPane.setText(I18n.get("section.multithreading"));
		advancedPane.setText(I18n.get("section.advanced"));
		dbasisPane.setText(I18n.get("section.dbasis"));

		boolean isRuleBasis = "RULEBASIS".equals(desc.getName());
		boolean isDbasis = "DBASIS".equals(desc.getName());

		// Panneau algo : RULEBASIS seulement
		algoPane.setVisible(isRuleBasis);
		algoPane.setManaged(isRuleBasis);
		if (isRuleBasis) {
			algoCombo.getItems().setAll(desc.getAlgorithms());
			algoCombo.setValue(desc.getDefaultAlgorithm());
		}

		clarifyCheckBox.setVisible(isRuleBasis);
		clarifyCheckBox.setManaged(isRuleBasis);
		closureLabel.setVisible(isRuleBasis);
		closureLabel.setManaged(isRuleBasis);
		closureCombo.setVisible(isRuleBasis);
		closureCombo.setManaged(isRuleBasis);

		// Pool mode selon la commande
		poolModeCombo.getItems().clear();
		if (isRuleBasis) {
			poolModeCombo.getItems().addAll("MONO", "FORKJOINPOOL");
			poolModeCombo.setValue("MONO");
		} else {
			poolModeCombo.getItems().addAll("MONO", "MULTITHREAD");
			poolModeCombo.setValue("MULTITHREAD");
		}

		// Panneau DBASIS
		dbasisPane.setVisible(isDbasis);
		dbasisPane.setManaged(isDbasis);

		// Tri par support : RULEBASIS seulement
		sortBySupportCheckBox.setVisible(isRuleBasis);
		sortBySupportCheckBox.setManaged(isRuleBasis);
		loadPrefs();
	}

	@FXML
	private void onEditInput() {
			editInput(inputFileField);
	}

	@FXML
	private void onBrowseInput() {
		FileChooser fc = new FileChooser();
		fc.setTitle(I18n.get("browse.input.title"));
		fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
		fc.getExtensionFilters().addAll(
				   new FileChooser.ExtensionFilter(I18n.get("filter.context.all"),
					        "*.cxt", "*.slf", "*.cex", "*.xml", "*.csv"),
					    new FileChooser.ExtensionFilter(I18n.get("filter.all"), "*.*"));
		File f = fc.showOpenDialog(inputFileField.getScene().getWindow());
		if (f != null) {
			inputFileField.setText(f.getAbsolutePath());
			if (onInputChanged != null) onInputChanged.accept(f.getAbsolutePath());
			AppPreferences.setLastDirectory(f.getParent());
			autoDetectFormat(f.getName(),inputFormatCombo);
			if (outputFileField.getText().isBlank()) {
				String base = f.getAbsolutePath().replaceAll("\\.[^.]+$", "");
				outputFileField.setText(base + "-rules.txt");
			}
		}
	}

	@FXML
	private void onBrowseOutput() {
		FileChooser fc = new FileChooser();
		fc.setTitle(I18n.get("browse.output.title"));
		fc.getExtensionFilters().addAll(
		    new FileChooser.ExtensionFilter(I18n.get("filter.text"), "*.txt"),
		    new FileChooser.ExtensionFilter(I18n.get("filter.json"), "*.json"),
		    new FileChooser.ExtensionFilter(I18n.get("filter.xml"), "*.xml"),
		    new FileChooser.ExtensionFilter(I18n.get("filter.datalog"), "*.dlgp"));		
		fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
		File f = fc.showSaveDialog(outputFileField.getScene().getWindow());
		if (f != null) {
			outputFileField.setText(f.getAbsolutePath());
			String name = f.getName().toLowerCase();
			if (name.endsWith(".json"))
				outputFormatCombo.setValue("JSON");
			else if (name.endsWith(".xml"))
				outputFormatCombo.setValue("XML");
			else if (name.endsWith(".dlgp"))
				outputFormatCombo.setValue("DATALOG");
			else
				outputFormatCombo.setValue("TXT");
		}
	}

	@FXML
	private void onBrowseReport() {
		FileChooser fc = new FileChooser();
		fc.setTitle(I18n.get("browse.report.title"));
		fc.getExtensionFilters().add(
		    new FileChooser.ExtensionFilter(I18n.get("filter.text"), "*.txt"));		
		fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
		File f = fc.showSaveDialog(reportFileField.getScene().getWindow());
		if (f != null)
			reportFileField.setText(f.getAbsolutePath());
	}

	@FXML
	private void onBrowseImplFolder() {
		javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
		dc.setTitle(I18n.get("browse.impl.folder.title"));
		dc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
		File f = dc.showDialog(implFolderField.getScene().getWindow());
		if (f != null)
			implFolderField.setText(f.getAbsolutePath());
	}

	@FXML
	public void onRun() {
		savePrefs();
		if(!validateInput(inputFileField)) return;

		CommandBuilder builder = new CommandBuilder().command(descriptor.getName())
				.inputFile(inputFileField.getText().trim()).outputFormat(outputFormatCombo.getValue())
				.implementation(implCombo.getValue()).verbose(verboseCheckBox.isSelected());

		if (!outputFileField.getText().isBlank())
			builder.outputFile(Utilities.resolveOutput(outputFileField.getText().trim(),inputFileField));

		String fmt = inputFormatCombo.getValue();
		if (!"(auto)".equals(fmt))
			builder.inputFormat(fmt);

		builder.poolMode(poolModeCombo.getValue());

		int to = timeoutSpinner.getValue();
		if (to > 0)
			builder.timeout(to);

		if ("RULEBASIS".equals(descriptor.getName())) {
			builder.algorithm(algoCombo.getValue()).clarify(clarifyCheckBox.isSelected())
					.closureMethod(closureCombo.getValue()).sortBySupport(sortBySupportCheckBox.isSelected());
			if (!"MONO".equals(poolModeCombo.getValue()))
				builder.threadThreshold(thresholdSpinner.getValue());
			if (!reportFileField.getText().isBlank())
				builder.reportFile(Utilities.resolveOutput(reportFileField.getText().trim(),inputFileField));
			if (!implFolderField.getText().isBlank())
				builder.implFolder(implFolderField.getText().trim());
		}

		if ("DBASIS".equals(descriptor.getName())) {
			int ms = minSupportSpinner.getValue();
			if (ms > 0)
				builder.minimalSupport(ms);
			if (!reportFileField.getText().isBlank())
				builder.reportFile(Utilities.resolveOutput(reportFileField.getText().trim(),inputFileField));
		}
    	if (!outputFileField.getText().isBlank())
    	    AppPreferences.saveOutputForInput(
    	        descriptor.getName(),
    	        inputFileField.getText().trim(),
    	        outputFileField.getText().trim());

		if (onRun != null)
			onRun.accept(builder);
	}

	public void setInputFile(String path) {
	    if (path == null || path.isBlank()) return;
	    inputFileField.setText(path);
	    autoDetectFormat(new File(path).getName(),inputFormatCombo);

	    String cmd  = descriptor != null ? descriptor.getName() : "RULEBASIS";
	    String base = path.replaceAll("\\.[^.]+$", "");

	    String savedOutput = AppPreferences.loadOutputForInput(cmd, path);
	    outputFileField.setText(savedOutput.isBlank()
	        ? base + "-rules.txt" : savedOutput);
	}

	public String getInputFile() {
		return inputFileField.getText();
	}
	protected void savePrefs() {
	    String cmd = descriptor.getName(); // "RULEBASIS" ou "DBASIS"
	    AppPreferences.saveString(cmd + ".outputFormat", outputFormatCombo.getValue());
	    AppPreferences.saveString(cmd + ".impl",         implCombo.getValue());
	    AppPreferences.saveString(cmd + ".poolMode",     poolModeCombo.getValue());
	    AppPreferences.saveBool  (cmd + ".verbose",      verboseCheckBox.isSelected());
	    AppPreferences.saveInt   (cmd + ".timeout",      timeoutSpinner.getValue());

	    if ("RULEBASIS".equals(cmd)) {
	        AppPreferences.saveString(cmd + ".algo",     algoCombo.getValue());
	        AppPreferences.saveString(cmd + ".closure",  closureCombo.getValue());
	        AppPreferences.saveBool  (cmd + ".clarify",  clarifyCheckBox.isSelected());
	        AppPreferences.saveBool  (cmd + ".sort",     sortBySupportCheckBox.isSelected());
	        AppPreferences.saveInt   (cmd + ".threshold",thresholdSpinner.getValue());
	    }
	    if ("DBASIS".equals(cmd)) {
	        AppPreferences.saveInt(cmd + ".minSupport", minSupportSpinner.getValue());
	    }
	}

	protected void loadPrefs() {
	    String cmd = descriptor.getName();
	    String fmt = AppPreferences.loadString(cmd + ".outputFormat", "TXT");
	    if (outputFormatCombo.getItems().contains(fmt)) outputFormatCombo.setValue(fmt);

	    String impl = AppPreferences.loadString(cmd + ".impl", "BITSET");
	    if (implCombo.getItems().contains(impl)) implCombo.setValue(impl);

	    String pool = AppPreferences.loadString(cmd + ".poolMode",
	        "RULEBASIS".equals(cmd) ? "MONO" : "MULTITHREAD");
	    if (poolModeCombo.getItems().contains(pool)) poolModeCombo.setValue(pool);

	    verboseCheckBox.setSelected(AppPreferences.loadBool(cmd + ".verbose", false));
	    timeoutSpinner.getValueFactory().setValue(AppPreferences.loadInt(cmd + ".timeout", 0));

	    if ("RULEBASIS".equals(cmd)) {
	        String algo = AppPreferences.loadString(cmd + ".algo",
	            descriptor.getDefaultAlgorithm());
	        if (algoCombo.getItems().contains(algo)) algoCombo.setValue(algo);

	        String closure = AppPreferences.loadString(cmd + ".closure", "BASIC");
	        if (closureCombo.getItems().contains(closure)) closureCombo.setValue(closure);

	        clarifyCheckBox.setSelected(AppPreferences.loadBool(cmd + ".clarify",   false));
	        sortBySupportCheckBox.setSelected(AppPreferences.loadBool(cmd + ".sort", false));
	        thresholdSpinner.getValueFactory().setValue(
	            AppPreferences.loadInt(cmd + ".threshold", 50));
	    }
	    if ("DBASIS".equals(cmd)) {
	        minSupportSpinner.getValueFactory().setValue(
	            AppPreferences.loadInt(cmd + ".minSupport", 0));
	    }
	}
	}
