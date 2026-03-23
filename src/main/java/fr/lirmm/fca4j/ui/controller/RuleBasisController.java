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
 * Contrôleur du panneau de paramètres pour RULEBASIS et DBASIS.
 */
public class RuleBasisController implements Initializable {

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
	private Consumer<Path> openInEditor;

	// ── Fichiers ──────────────────────────────────────────────────────────────
	@FXML
	private TextField inputFileField;
	@FXML
	private ComboBox<String> inputFormatCombo;
	@FXML
	private Label separatorLabel;
	@FXML
	private ComboBox<String> separatorCombo;
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

	private CommandDescriptor descriptor;
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
	}

	public void configure(CommandDescriptor desc, Consumer<CommandBuilder> onRun, Consumer<Path> openInEditor,
			Consumer<String> onInputChanged) {
		this.onInputChanged = onInputChanged;
		this.openInEditor = openInEditor;
		this.descriptor = desc;
		this.onRun = onRun;

		inputPane.setText(I18n.get("section.input"));
		outputPane.setText(I18n.get("section.output"));
		threadPane.setText(I18n.get("section.multithreading"));
		advancedPane.setText(I18n.get("section.advanced"));
		dbasisPane.setText(I18n.get("section.dbasis"));
		runButton.setText(I18n.get("button.run"));

		// Bouton "Ouvrir dans l'éditeur"
		FontIcon editIcon = new FontIcon(Material2AL.EDIT);
		editIcon.setIconSize(14);
		editInputButton.setGraphic(editIcon);
		editInputButton.setText("");
		editInputButton.setTooltip(new Tooltip(I18n.get("btn.open.in.editor")));

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
	}

	@FXML
	private void onEditInput() {
		String path = inputFileField.getText().trim();
		if (path.isBlank()) {
			showError(I18n.get("error.no.input.title"), I18n.get("error.no.input.detail"));
			return;
		}
		if (openInEditor != null)
			openInEditor.accept(java.nio.file.Path.of(path));
	}

	@FXML
	private void onBrowseInput() {
		FileChooser fc = new FileChooser();
		fc.setTitle("Fichier de contexte formel");
		fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
		fc.getExtensionFilters().addAll(
				new FileChooser.ExtensionFilter("Contextes FCA", "*.slf", "*.cex", "*.cxt", "*.xml", "*.csv"),
				new FileChooser.ExtensionFilter("Tous les fichiers", "*.*"));
		File f = fc.showOpenDialog(inputFileField.getScene().getWindow());
		if (f != null) {
			inputFileField.setText(f.getAbsolutePath());
			if (onInputChanged != null) onInputChanged.accept(f.getAbsolutePath());
			AppPreferences.setLastDirectory(f.getParent());
			autoDetectFormat(f.getName());
			if (outputFileField.getText().isBlank()) {
				String base = f.getAbsolutePath().replaceAll("\\.[^.]+$", "");
				outputFileField.setText(base + "-rules.txt");
			}
		}
	}

	@FXML
	private void onBrowseOutput() {
		FileChooser fc = new FileChooser();
		fc.setTitle("Fichier de sortie");
		fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
		fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Texte", "*.txt"),
				new FileChooser.ExtensionFilter("JSON", "*.json"), new FileChooser.ExtensionFilter("XML", "*.xml"),
				new FileChooser.ExtensionFilter("Datalog+", "*.dlgp"));
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
		fc.setTitle("Fichier de rapport");
		fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
		fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Texte", "*.txt"));
		File f = fc.showSaveDialog(reportFileField.getScene().getWindow());
		if (f != null)
			reportFileField.setText(f.getAbsolutePath());
	}

	@FXML
	private void onBrowseImplFolder() {
		javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
		dc.setTitle("Dossier de sortie par support");
		dc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
		File f = dc.showDialog(implFolderField.getScene().getWindow());
		if (f != null)
			implFolderField.setText(f.getAbsolutePath());
	}

	@FXML
	private void onRun() {
		if (inputFileField.getText().isBlank()) {
			showError("Fichier d'entrée manquant", "Veuillez sélectionner un fichier de contexte.");
			return;
		}

		CommandBuilder builder = new CommandBuilder().command(descriptor.getName())
				.inputFile(inputFileField.getText().trim()).outputFormat(outputFormatCombo.getValue())
				.implementation(implCombo.getValue()).verbose(verboseCheckBox.isSelected());

		if (!outputFileField.getText().isBlank())
			builder.outputFile(outputFileField.getText().trim());

		String fmt = inputFormatCombo.getValue();
		if (!"(auto)".equals(fmt))
			builder.inputFormat(fmt);
		if ("CSV".equals(fmt))
			builder.separator(separatorCombo.getValue());

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
				builder.reportFile(reportFileField.getText().trim());
			if (!implFolderField.getText().isBlank())
				builder.implFolder(implFolderField.getText().trim());
		}

		if ("DBASIS".equals(descriptor.getName())) {
			int ms = minSupportSpinner.getValue();
			if (ms > 0)
				builder.minimalSupport(ms);
			if (!reportFileField.getText().isBlank())
				builder.reportFile(reportFileField.getText().trim());
		}

		if (onRun != null)
			onRun.accept(builder);
	}

	private void autoDetectFormat(String filename) {
		String lower = filename.toLowerCase();
		if (lower.endsWith(".cxt"))
			inputFormatCombo.setValue("CXT");
		else if (lower.endsWith(".slf"))
			inputFormatCombo.setValue("SLF");
		else if (lower.endsWith(".xml"))
			inputFormatCombo.setValue("XML");
		else if (lower.endsWith(".cex"))
			inputFormatCombo.setValue("CEX");
		else if (lower.endsWith(".csv"))
			inputFormatCombo.setValue("CSV");
		else
			inputFormatCombo.setValue("(auto)");
	}

	private void showError(String title, String msg) {
		Alert a = new Alert(Alert.AlertType.WARNING);
		a.setTitle(title);
		a.setHeaderText(null);
		a.setContentText(msg);
		a.showAndWait();
	}

	public void setInputFile(String path) {
		if (path != null && !path.isBlank())
			inputFileField.setText(path);
	}

	public String getInputFile() {
		return inputFileField.getText();
	}
}
