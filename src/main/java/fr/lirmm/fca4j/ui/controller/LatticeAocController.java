package fr.lirmm.fca4j.ui.controller;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

import fr.lirmm.fca4j.ui.model.CommandBuilder;
import fr.lirmm.fca4j.ui.model.CommandDescriptor;
import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.I18n;
import fr.lirmm.fca4j.ui.util.Utilities;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.stage.FileChooser;

public class LatticeAocController extends AbstractCommandController implements Initializable {

	// ── Bouton édition ────────────────────────────────────────────────────────
	@FXML
	private Button editInputButton;

	// ── TitledPanes et bouton Run (titres gérés par I18n) ─────────────────────
	@FXML
	private TitledPane inputPane;
	@FXML
	private TitledPane outputPane;
	@FXML
	private TitledPane algoPane;
	@FXML
	private TitledPane graphvizPane;
	@FXML
	private TitledPane advancedPane;

	// ── Fichiers ──────────────────────────────────────────────────────────────
	@FXML
	private TextField inputFileField;
	@FXML
	private ComboBox<String> inputFormatCombo;
	@FXML
	private TextField outputFileField;
	@FXML
	private ComboBox<String> outputFormatCombo;

	// ── Algorithme ────────────────────────────────────────────────────────────
	@FXML
	private ComboBox<String> algoCombo;
	@FXML
	private Label icebergLabel;
	@FXML
	private Spinner<Integer> icebergSpinner;

	// ── GraphViz ──────────────────────────────────────────────────────────────
	@FXML
	private CheckBox dotCheckBox;
	@FXML
	private TextField dotFileField;
	@FXML
	private Button dotBrowseButton;
	@FXML
	private ComboBox<String> displayModeCombo;
	@FXML
	private CheckBox stabilityCheckBox;

	// Datalog
	@FXML
	private TitledPane datalogPane;
	@FXML
	private TextField datalogFolderField;
	@FXML
	private TextField datalogFileField;
	@FXML
	private CheckBox noDirectSiblings;

	// ── Options avancées ──────────────────────────────────────────────────────
	@FXML
	private ComboBox<String> implCombo;
	@FXML
	private Spinner<Integer> timeoutSpinner;
	@FXML
	private CheckBox verboseCheckBox;


	@Override
	public void initialize(URL location, ResourceBundle resources) {
		inputFormatCombo.getItems().addAll(I18n.get("format.auto"), "CXT", "SLF", "XML", "CEX", "CSV");
		inputFormatCombo.setValue(I18n.get("format.auto"));

		outputFormatCombo.getItems().addAll("XML", "JSON");
		outputFormatCombo.setValue("XML");

		displayModeCombo.getItems().addAll("SIMPLIFIED", "FULL", "MINIMAL");
		displayModeCombo.setValue("SIMPLIFIED");

		implCombo.getItems().addAll("BITSET", "ROARING_BITMAP", "SPARSE_BITSET", "HASHSET", "TREESET", "INT_ARRAY",
				"ARRAYLIST", "BOOL_ARRAY");
		implCombo.setValue("BITSET");

		timeoutSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 3600, 0, 10));
		icebergSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 50, 5));

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
			if (val && dotFileField.getText().isBlank()) {
				// Proposer un nom basé sur le fichier d'entrée
				String input = inputFileField.getText().trim();
				if (!input.isBlank()) {
					String base = input.replaceAll("\\.[^.]+$", "");
					dotFileField.setText(base + ".dot");
				} else if (!outputFileField.getText().isBlank()) {
					// Fallback sur le fichier de sortie
					dotFileField.setText(outputFileField.getText().replaceAll("\\.[^.]+$", "") + ".dot");
				}
			}
		});
		// Proposer le .dot automatiquement quand le fichier d'entrée change
		inputFileField.textProperty().addListener((obs, old, val) -> {
			if (dotCheckBox.isSelected() && !val.isBlank()) {
				// Toujours recalculer le nom du .dot depuis le nouveau fichier d'entrée
				String base = val.trim().replaceAll("\\.[^.]+$", "");
				dotFileField.setText(base + ".dot");
			}
		});
		// Zone Iceberg visible seulement si algo = ICEBERG
		algoCombo.valueProperty().addListener((obs, old, val) -> {
			boolean isIceberg = "ICEBERG".equals(val);
			icebergLabel.setVisible(isIceberg);
			icebergSpinner.setVisible(isIceberg);
			updateAlgoTitle();
		});
		icebergSpinner.valueProperty().addListener((obs, old, val) -> updateAlgoTitle());
		Utilities.bindPathTooltip(inputFileField);
		Utilities.bindPathTooltip(outputFileField);
		Utilities.bindPathTooltip(dotFileField);
		Utilities.bindPathTooltip(datalogFolderField);
		Utilities.bindPathTooltip(datalogFileField);
	}

	private void updateAlgoTitle() {
		String algo = algoCombo.getValue();
		if (algo == null || algoPane == null)
			return;
		String title = I18n.get("section.algorithm") + " : " + algo;
		if ("ICEBERG".equals(algo))
			title += " (" + icebergSpinner.getValue() + "%)";
		algoPane.setText(title);
	}

	/**
	 * Configure le panneau pour LATTICE ou AOCPOSET. Appelé depuis MainController
	 * après chargement du FXML.
	 */
	public void configure(CommandDescriptor desc, Consumer<CommandBuilder> onRun, Consumer<Path> openInEditor,
			Consumer<String> onInputChanged) {

		configureBase(desc, onRun, openInEditor, onInputChanged, editInputButton);

		// Titres des sections via I18n
		inputPane.setText(I18n.get("section.input"));
		outputPane.setText(I18n.get("section.output"));
		algoPane.setText(I18n.get("section.algorithm"));
		graphvizPane.setText(I18n.get("section.graphviz"));
		datalogPane.setText(I18n.get("rca.section.datalog"));
		advancedPane.setText(I18n.get("section.advanced"));

		// Algorithmes spécifiques à la commande
		algoCombo.getItems().setAll(desc.getAlgorithms());
		algoCombo.setValue(desc.getDefaultAlgorithm());

		// Zone Iceberg : LATTICE seulement
		icebergLabel.setVisible(false);
		icebergSpinner.setVisible(false);
		loadPrefs();
		Platform.runLater(this::updateAlgoTitle);
	}

	@FXML
	private void onEditInput() {
			editInput(inputFileField);
	}

	// ── Actions ───────────────────────────────────────────────────────────────

	@FXML
	private void onBrowseInput() {
		FileChooser fc = buildContextChooser(I18n.get("label.input.file"));
		File f = fc.showOpenDialog(inputFileField.getScene().getWindow());
		if (f != null) {
			inputFileField.setText(f.getAbsolutePath());
			if (onInputChanged != null)
				onInputChanged.accept(f.getAbsolutePath());
			AppPreferences.setLastDirectory(f.getParent());
			autoDetectFormat(f.getName(),inputFormatCombo);
			String base = f.getAbsolutePath().replaceAll("\\.[^.]+$", "");
			if (outputFileField.getText().isBlank())
				outputFileField.setText(base + "-result.xml");
			// Proposer le .dot si la case est cochée et le champ est vide
			if (dotCheckBox.isSelected() && dotFileField.getText().isBlank())
				dotFileField.setText(base + ".dot");
		}
	}

	@FXML
	private void onBrowseOutput() {
		FileChooser fc = new FileChooser();
		fc.setTitle(I18n.get("label.output.file"));
		fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
		fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("XML", "*.xml"),
				new FileChooser.ExtensionFilter("JSON", "*.json"));
		File f = fc.showSaveDialog(outputFileField.getScene().getWindow());
		if (f != null) {
			outputFileField.setText(f.getAbsolutePath());
			String name = f.getName().toLowerCase();
			if (name.endsWith(".json"))
				outputFormatCombo.setValue("JSON");
			else
				outputFormatCombo.setValue("XML");
		}
	}

	@FXML
	private void onBrowseDot() {
		FileChooser fc = new FileChooser();
		fc.setTitle(I18n.get("label.dot.file"));
		fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
		fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("GraphViz DOT", "*.dot"));
		File f = fc.showSaveDialog(dotFileField.getScene().getWindow());
		if (f != null)
			dotFileField.setText(f.getAbsolutePath());
	}

	@FXML
	private void onBrowseDatalogFolder() {
		javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
		dc.setTitle(I18n.get("rca.browse.datalog.folder"));
		dc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
		File f = dc.showDialog(datalogFolderField.getScene().getWindow());
		if (f != null)
			datalogFolderField.setText(f.getAbsolutePath());
	}

	@FXML
	private void onBrowseDatalogFile() {
		FileChooser fc = new FileChooser();
		fc.setTitle(I18n.get("rca.browse.datalog.file"));
		fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
		fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Datalog", "*.dlgp", "*.dl"));
		File f = fc.showSaveDialog(datalogFileField.getScene().getWindow());
		if (f != null)
			datalogFileField.setText(f.getAbsolutePath());
	}

	@FXML
	public void onRun() {
		savePrefs();
		if(!validateInput(inputFileField)) return;
		if (dotCheckBox.isSelected() && dotFileField.getText().isBlank()) {
			showError(I18n.get("error.dot.missing.title"), I18n.get("error.dot.missing.detail"));
			return;
		}

		CommandBuilder builder = new CommandBuilder().command(descriptor.getName())
				.inputFile(inputFileField.getText().trim()).algorithm(algoCombo.getValue())
				.outputFormat(outputFormatCombo.getValue()).verbose(verboseCheckBox.isSelected())
				.implementation(implCombo.getValue());

		if (dotCheckBox.isSelected())
			builder.dotFile(Utilities.resolveOutput(dotFileField.getText().trim(), inputFileField))
					.displayMode(displayModeCombo.getValue()).stability(stabilityCheckBox.isSelected());

		if (!datalogFolderField.getText().isBlank())
			builder.datalogFolder(Utilities.resolveOutput(datalogFolderField.getText().trim(), inputFileField));
		if (!datalogFileField.getText().isBlank())
			builder.datalogFile(Utilities.resolveOutput(datalogFileField.getText().trim(), inputFileField));
		if (!outputFileField.getText().isBlank())
			builder.outputFile(Utilities.resolveOutput(outputFileField.getText().trim(), inputFileField));

		String fmt = inputFormatCombo.getValue();
		if (!I18n.get("format.auto").equals(fmt))
			builder.inputFormat(fmt);
		if ("ICEBERG".equals(algoCombo.getValue()))
			builder.icebergPercent(icebergSpinner.getValue());

		int to = timeoutSpinner.getValue();
		if (to > 0)
			builder.timeout(to);
		if (!outputFileField.getText().isBlank())
			AppPreferences.saveOutputForInput(descriptor.getName(), inputFileField.getText().trim(),
					outputFileField.getText().trim());

		if (noDirectSiblings.isSelected())
			builder.noDirectSiblings(true);
		if (onRun != null)
			onRun.accept(builder);
	}

	// ── Utilitaires ───────────────────────────────────────────────────────────



	public void setInputFile(String path) {
		if (path == null || path.isBlank())
			return;
		inputFileField.setText(path);
		autoDetectFormat(new File(path).getName(),inputFormatCombo);

		String cmd = descriptor != null ? descriptor.getName() : "LATTICE";
		String base = path.replaceAll("\\.[^.]+$", "");
		String ext = "XML".equals(AppPreferences.loadString(cmd + ".outputFormat", "XML")) ? ".xml" : ".json";

		String savedOutput = AppPreferences.loadOutputForInput(cmd, path);
		outputFileField.setText(savedOutput.isBlank() ? base + "-result" + ext : savedOutput);

		// DOT : toujours recalculer depuis le nouveau fichier d'entrée
		if (dotCheckBox.isSelected())
			dotFileField.setText(base + ".dot");
	}

	public String getInputFile() {
		return inputFileField.getText();
	}

	public String getDotFile() {
		return dotCheckBox.isSelected() ? dotFileField.getText() : null;
	}

	protected void savePrefs() {
		String cmd = descriptor.getName(); 
		AppPreferences.saveString(cmd + ".algo", algoCombo.getValue());
		AppPreferences.saveString(cmd + ".outputFormat", outputFormatCombo.getValue());
		AppPreferences.saveString(cmd + ".displayMode", displayModeCombo.getValue());
		AppPreferences.saveString(cmd + ".impl", implCombo.getValue());
		AppPreferences.saveBool(cmd + ".dot", dotCheckBox.isSelected());
		AppPreferences.saveBool(cmd + ".stability", stabilityCheckBox.isSelected());
		AppPreferences.saveBool(cmd + ".verbose", verboseCheckBox.isSelected());
		AppPreferences.saveInt(cmd + ".timeout", timeoutSpinner.getValue());
		AppPreferences.saveInt(cmd + ".iceberg", icebergSpinner.getValue());
		// Datalog
		AppPreferences.saveBool(cmd + ".nds", noDirectSiblings.isSelected());
	}

	protected void loadPrefs() {
		String cmd = descriptor.getName();
		String algo = AppPreferences.loadString(cmd + ".algo", descriptor.getDefaultAlgorithm());
		if (algoCombo.getItems().contains(algo))
			algoCombo.setValue(algo);

		String fmt = AppPreferences.loadString(cmd + ".outputFormat", "XML");
		if (outputFormatCombo.getItems().contains(fmt))
			outputFormatCombo.setValue(fmt);

		String dm = AppPreferences.loadString(cmd + ".displayMode", "SIMPLIFIED");
		if (displayModeCombo.getItems().contains(dm))
			displayModeCombo.setValue(dm);

		String impl = AppPreferences.loadString(cmd + ".impl", "BITSET");
		if (implCombo.getItems().contains(impl))
			implCombo.setValue(impl);

		dotCheckBox.setSelected(AppPreferences.loadBool(cmd + ".dot", false));
		stabilityCheckBox.setSelected(AppPreferences.loadBool(cmd + ".stability", false));
		verboseCheckBox.setSelected(AppPreferences.loadBool(cmd + ".verbose", false));
		timeoutSpinner.getValueFactory().setValue(AppPreferences.loadInt(cmd + ".timeout", 0));
		icebergSpinner.getValueFactory().setValue(AppPreferences.loadInt(cmd + ".iceberg", 50));
		noDirectSiblings.setSelected(AppPreferences.loadBool(cmd + ".nds", false));
		Platform.runLater(this::updateAlgoTitle);
	}

}
