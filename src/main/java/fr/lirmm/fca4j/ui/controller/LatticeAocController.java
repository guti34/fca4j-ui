/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
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
	private javafx.scene.layout.HBox icebergBox;
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
	private CheckBox disableNativeCodeCheckBox;
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

		// Quand le natif est actif (checkbox non cochée) → forcer ROARING_BITMAP
		disableNativeCodeCheckBox.selectedProperty().addListener((obs, old, disabled) -> {
			if (!disabled) {
				implCombo.setValue("ROARING_BITMAP");
				implCombo.setDisable(true);
			} else {
				implCombo.setDisable(false);
			}
		});

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
			icebergBox.setVisible(isIceberg);
			icebergBox.setManaged(isIceberg);
		});
		icebergSpinner.valueProperty().addListener((obs, old, val) -> {});
		Utilities.bindPathTooltip(inputFileField);
		Utilities.bindPathTooltip(outputFileField);
		Utilities.bindPathTooltip(dotFileField);
		Utilities.bindPathTooltip(datalogFolderField);
		Utilities.bindPathTooltip(datalogFileField);
	}

	/**
	 * Indique si le couple (commande, algorithme) dispose d'une implémentation
	 * native C activable via -dnc.
	 */
	private static boolean hasNativeImpl(String cmd, String algo) {
		if (cmd == null || algo == null)
			return false;
		if ("LATTICE".equals(cmd))
			if("ADD_EXTENT".equals(algo) || "PARALLEL_CBO".equals(algo)) return true;
		if ("AOCPOSET".equals(cmd) && "HERMES".equals(algo))     return true;
		return false;
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
		graphvizPane.setText(I18n.get("section.graphviz"));
		datalogPane.setText(I18n.get("rca.section.datalog"));
		advancedPane.setText(I18n.get("section.advanced"));

		// Algorithmes spécifiques à la commande
		algoCombo.getItems().setAll(desc.getAlgorithms());
		algoCombo.setValue(desc.getDefaultAlgorithm());

		// Zone Iceberg : masquée par défaut
		icebergBox.setVisible(false);
		icebergBox.setManaged(false);

		// Checkbox native code : visible si le couple (commande, algo) a un
		// portage natif (LATTICE+ADD_EXTENT ou AOCPOSET+HERMES).
		final String cmdName = desc.getName();
		boolean showNativeInit = hasNativeImpl(cmdName, algoCombo.getValue());
		disableNativeCodeCheckBox.setVisible(showNativeInit);
		disableNativeCodeCheckBox.setManaged(showNativeInit);

		// Mettre à jour la visibilité quand l'algo change
		algoCombo.valueProperty().addListener((obs, old, val) -> {
			boolean showNative = hasNativeImpl(cmdName, val);
			disableNativeCodeCheckBox.setVisible(showNative);
			disableNativeCodeCheckBox.setManaged(showNative);
			if (!showNative) {
				implCombo.setDisable(false);
			} else if (!disableNativeCodeCheckBox.isSelected()) {
				implCombo.setValue("ROARING_BITMAP");
				implCombo.setDisable(true);
			}
		});
		loadPrefs();
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

		// Native code (couples avec portage natif : LATTICE+ADD_EXTENT,  LATTICE+PARALLEL_CBO, AOCPOSET+HERMES)
		if (hasNativeImpl(descriptor.getName(), algoCombo.getValue())
				&& disableNativeCodeCheckBox.isSelected())
			builder.disableNativeCode(true);

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
		autoDetectFormat(new File(path).getName(), inputFormatCombo);

		String cmd = descriptor != null ? descriptor.getName() : "LATTICE";
		String base = path.replaceAll("\\.[^.]+$", "");
		String ext = "XML".equals(AppPreferences.loadString(cmd + ".outputFormat", "XML")) ? ".xml" : ".json";

		if (outputFileField.getText().isBlank()) {
			String savedOutput = AppPreferences.loadOutputForInput(cmd, path);
			outputFileField.setText(savedOutput.isBlank() ? base + "-result" + ext : savedOutput);
		}

		// DOT : recalculer seulement si le champ est vide
		if (dotCheckBox.isSelected() && dotFileField.getText().isBlank())
			dotFileField.setText(base + ".dot");
	}

	public String getInputFile() {
		return inputFileField.getText();
	}

	public String getDotFile() {
		return dotCheckBox.isSelected() ? dotFileField.getText() : null;
	}

	public void savePrefs() {
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
		AppPreferences.saveString(cmd + ".outputFile", outputFileField.getText().trim());
		AppPreferences.saveString(cmd + ".dotFile", dotCheckBox.isSelected() ? dotFileField.getText().trim() : "");
		// Native code (commandes avec portage natif)
		if ("LATTICE".equals(cmd) || "AOCPOSET".equals(cmd))
			AppPreferences.saveBool(cmd + ".disableNativeCode", disableNativeCodeCheckBox.isSelected());
		// Datalog
		AppPreferences.saveBool(cmd + ".nds", noDirectSiblings.isSelected());
		AppPreferences.saveString(cmd + ".datalogFolder", datalogFolderField.getText().trim());
		AppPreferences.saveString(cmd + ".datalogFile", datalogFileField.getText().trim());
	}

	public void loadPrefs() {
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
		// Native code (commandes avec portage natif)
		if ("LATTICE".equals(cmd) || "AOCPOSET".equals(cmd)) {
			boolean dnc = AppPreferences.loadBool(cmd + ".disableNativeCode", false);
			disableNativeCodeCheckBox.setSelected(dnc);
			if (!dnc && hasNativeImpl(cmd, algoCombo.getValue())) {
				implCombo.setValue("ROARING_BITMAP");
				implCombo.setDisable(true);
			}
		}
		noDirectSiblings.setSelected(AppPreferences.loadBool(cmd + ".nds", false));
		String savedOutput = AppPreferences.loadString(cmd + ".outputFile", "");
		if (!savedOutput.isBlank()) outputFileField.setText(savedOutput);
		String savedDot = AppPreferences.loadString(cmd + ".dotFile", "");
		if (!savedDot.isBlank()) dotFileField.setText(savedDot);
		String savedDatalogFolder = AppPreferences.loadString(cmd + ".datalogFolder", "");
		if (!savedDatalogFolder.isBlank()) datalogFolderField.setText(savedDatalogFolder);
		String savedDatalogFile = AppPreferences.loadString(cmd + ".datalogFile", "");
		if (!savedDatalogFile.isBlank()) datalogFileField.setText(savedDatalogFile);
	}

}
