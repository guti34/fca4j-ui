/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
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
 * Contrôleur du panneau de paramètres pour CLARIFY et REDUCE. Ces deux
 * commandes prennent un contexte formel en entrée et produisent un contexte
 * formel en sortie.
 */
public class ReduceClarifyController extends AbstractCommandController implements Initializable {

	// ── Bouton édition ────────────────────────────────────────────────────────
	@FXML
	private Button editInputButton;

	// ── TitledPanes et bouton Run ─────────────────────────────────────────────
	@FXML
	private TitledPane inputPane;
	@FXML
	private TitledPane outputPane;
	@FXML
	private TitledPane operationPane;
	@FXML
	private TitledPane advancedPane;

	// ── Entrée ────────────────────────────────────────────────────────────────
	@FXML
	private TextField inputFileField;
	@FXML
	private ComboBox<String> inputFormatCombo;

	// ── Sortie ────────────────────────────────────────────────────────────────
	@FXML
	private TextField outputFileField;
	@FXML
	private ComboBox<String> outputFormatCombo;
	@FXML
	private Label outSeparatorLabel;
	@FXML
	private ComboBox<String> outSeparatorCombo;

	// ── Opération ─────────────────────────────────────────────────────────────
	@FXML
	private CheckBox xoCheckBox; // -xo : objets
	@FXML
	private CheckBox xaCheckBox; // -xa : attributs
	@FXML
	private CheckBox groupCheckBox; // -u : REDUCE seulement

	// ── Options avancées ──────────────────────────────────────────────────────
	@FXML
	private Spinner<Integer> timeoutSpinner;
	@FXML
	private CheckBox verboseCheckBox;


	// Formats communs aux deux commandes (contexte → contexte)
	private static final java.util.List<String> CONTEXT_FORMATS = java.util.List.of("(auto)", "CXT", "SLF", "XML",
			"CEX", "CSV");

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		inputFormatCombo.getItems().addAll(CONTEXT_FORMATS);
		inputFormatCombo.setValue("(auto)");

		outputFormatCombo.getItems().addAll(CONTEXT_FORMATS.subList(1, CONTEXT_FORMATS.size()));
		outputFormatCombo.setValue("CXT");

		outSeparatorCombo.getItems().addAll("COMMA", "SEMICOLON", "TAB");
		outSeparatorCombo.setValue("COMMA");

		// Afficher séparateur uniquement si format CSV
		outSeparatorLabel.setVisible(false);
		outSeparatorCombo.setVisible(false);

		outputFormatCombo.valueProperty().addListener((obs, old, val) -> {
			// Afficher/masquer le séparateur CSV
			boolean csv = "CSV".equals(val);
			outSeparatorLabel.setVisible(csv);
			outSeparatorCombo.setVisible(csv);

			// Mettre à jour l'extension du fichier de sortie
			String current = outputFileField.getText().trim();
			if (!current.isBlank()) {
				String base = current.replaceAll("\\.[^.]+$", "");
				outputFileField.setText(base + extForFormat(val));
			}
		});
		timeoutSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 3600, 0, 10));
		Utilities.bindPathTooltip(inputFileField);
		Utilities.bindPathTooltip(outputFileField);
	}

	public void configure(CommandDescriptor desc, Consumer<CommandBuilder> onRun, Consumer<Path> openInEditor,
			Consumer<String> onInputChanged) {
        configureBase(desc, onRun, openInEditor, onInputChanged, editInputButton);

		boolean isReduce = "REDUCE".equals(desc.getName());

		// Titres via I18n
		inputPane.setText(I18n.get("section.input"));
		outputPane.setText(I18n.get("section.output"));
		operationPane.setText(I18n.get("section.operation"));
		advancedPane.setText(I18n.get("section.advanced"));

		// Labels de l'opération selon la commande
		xoCheckBox.setText(isReduce ? I18n.get("rc.reduce.objects") : I18n.get("rc.clarify.objects"));
		xaCheckBox.setText(isReduce ? I18n.get("rc.reduce.attributes") : I18n.get("rc.clarify.attributes"));

		// Option -u : REDUCE seulement
		groupCheckBox.setVisible(isReduce);
		groupCheckBox.setManaged(isReduce);
		loadPrefs();
	}

	/** Retourne l'extension correspondant à un format de sortie. */
	private static String extForFormat(String fmt) {
		return switch (fmt) {
		case "SLF" -> ".slf";
		case "XML" -> ".xml";
		case "CEX" -> ".cex";
		case "CSV" -> ".csv";
		default -> ".cxt"; // CXT et tout autre
		};
	}
	// ── Actions ───────────────────────────────────────────────────────────────

	@FXML
	private void onEditInput() {
			editInput(inputFileField);
	}

	@FXML
	private void onBrowseInput() {
		FileChooser fc = buildContextChooserForSave(I18n.get("label.input.file"), true);
		File f = fc.showOpenDialog(inputFileField.getScene().getWindow());
		if (f != null) {
			inputFileField.setText(f.getAbsolutePath());
			if (onInputChanged != null)
				onInputChanged.accept(f.getAbsolutePath());
			AppPreferences.setLastDirectory(f.getParent());
			autoDetectFormat(f.getName(), inputFormatCombo);
			if (outputFileField.getText().isBlank()) {
				String base = f.getAbsolutePath().replaceAll("\\.[^.]+$", "");
				String cmd = descriptor.getName().toLowerCase();
				outputFileField.setText(base + "-" + cmd + extForFormat(outputFormatCombo.getValue()));
			}
		}
	}

	@FXML
	private void onBrowseOutput() {
		FileChooser fc = buildContextChooserForSave(I18n.get("label.output.file"), false);
		File f = fc.showSaveDialog(outputFileField.getScene().getWindow());
		if (f != null) {
			outputFileField.setText(f.getAbsolutePath());
			AppPreferences.setLastDirectory(f.getParent());
			autoDetectFormat(f.getName(), outputFormatCombo);
		}
	}

	@FXML
	public void onRun() {
		savePrefs();
		if(!validateInput(inputFileField)) return;
		if (!xoCheckBox.isSelected() && !xaCheckBox.isSelected()) {
			showError(I18n.get("rc.error.no.option.title"), I18n.get("rc.error.no.option.detail"));
			return;
		}

		CommandBuilder builder = new CommandBuilder().command(descriptor.getName())
				.inputFile(inputFileField.getText().trim()).clarifyObjects(xoCheckBox.isSelected())
				.clarifyAttributes(xaCheckBox.isSelected()).verbose(verboseCheckBox.isSelected());

		// Format d'entrée
		String inFmt = inputFormatCombo.getValue();
		if (!"(auto)".equals(inFmt))
			builder.inputFormat(inFmt);

		// Fichier et format de sortie
		if (!outputFileField.getText().isBlank()) {
			builder.outputFile(Utilities.resolveOutput(outputFileField.getText().trim(), inputFileField));
			String outFmt = outputFormatCombo.getValue();
			if (!"XML".equals(outFmt))
				builder.outputFormat(outFmt);
			if ("CSV".equals(outFmt))
				builder.separator(outSeparatorCombo.getValue());
		}

		// Option -u (REDUCE uniquement)
		if ("REDUCE".equals(descriptor.getName()) && groupCheckBox.isSelected())
			builder.groupByClasses(true);

		// Timeout
		int to = timeoutSpinner.getValue();
		if (to > 0)
			builder.timeout(to);

		if (!outputFileField.getText().isBlank())
			AppPreferences.saveOutputForInput(descriptor.getName(), inputFileField.getText().trim(),
					outputFileField.getText().trim());
		if (onRun != null)
			onRun.accept(builder);
	}

	// ── Utilitaires ───────────────────────────────────────────────────────────

	private FileChooser buildContextChooserForSave(String title, boolean forOpen) {
		FileChooser fc = new FileChooser();
		fc.setTitle(title);
		fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
		if (forOpen) {
			fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter(I18n.get("filter.context.all"), "*.cxt",
					"*.slf", "*.cex", "*.xml", "*.csv"),
					new FileChooser.ExtensionFilter(I18n.get("filter.all"), "*.*"));
		} else {
			fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("CXT (Burmeister)", "*.cxt"),
					new FileChooser.ExtensionFilter("SLF (HTK)", "*.slf"),
					new FileChooser.ExtensionFilter("CEX (ConExp)", "*.cex"),
					new FileChooser.ExtensionFilter("XML (Galicia)", "*.xml"),
					new FileChooser.ExtensionFilter("CSV", "*.csv"),
					new FileChooser.ExtensionFilter(I18n.get("filter.all"), "*.*"));
		}
		return fc;
	}


	public void setInputFile(String path) {
		if (path == null || path.isBlank())
			return;
		inputFileField.setText(path);
		autoDetectFormat(new File(path).getName(), inputFormatCombo);

		String cmd = descriptor != null ? descriptor.getName() : "CLARIFY";
		String base = path.replaceAll("\\.[^.]+$", "");

		String savedOutput = AppPreferences.loadOutputForInput(cmd, path);
		outputFileField.setText(
				savedOutput.isBlank() ? base + "-" + cmd.toLowerCase() + extForFormat(outputFormatCombo.getValue())
						: savedOutput);
	}

	public String getInputFile() {
		return inputFileField.getText();
	}

	protected void savePrefs() {
		String cmd = descriptor.getName(); // "CLARIFY" ou "REDUCE"
		AppPreferences.saveString(cmd + ".inputFormat", inputFormatCombo.getValue());
		AppPreferences.saveString(cmd + ".outputFormat", outputFormatCombo.getValue());
		AppPreferences.saveString(cmd + ".outSeparator", outSeparatorCombo.getValue());
		AppPreferences.saveBool(cmd + ".xo", xoCheckBox.isSelected());
		AppPreferences.saveBool(cmd + ".xa", xaCheckBox.isSelected());
		AppPreferences.saveBool(cmd + ".verbose", verboseCheckBox.isSelected());
		AppPreferences.saveInt(cmd + ".timeout", timeoutSpinner.getValue());
		if ("REDUCE".equals(cmd))
			AppPreferences.saveBool(cmd + ".group", groupCheckBox.isSelected());
	}

	protected void loadPrefs() {
		String cmd = descriptor.getName();

		String inFmt = AppPreferences.loadString(cmd + ".inputFormat", "(auto)");
		if (inputFormatCombo.getItems().contains(inFmt))
			inputFormatCombo.setValue(inFmt);

		String outFmt = AppPreferences.loadString(cmd + ".outputFormat", "CXT");
		if (outputFormatCombo.getItems().contains(outFmt))
			outputFormatCombo.setValue(outFmt);

		String outSep = AppPreferences.loadString(cmd + ".outSeparator", "COMMA");
		if (outSeparatorCombo.getItems().contains(outSep))
			outSeparatorCombo.setValue(outSep);

		xoCheckBox.setSelected(AppPreferences.loadBool(cmd + ".xo", false));
		xaCheckBox.setSelected(AppPreferences.loadBool(cmd + ".xa", false));
		verboseCheckBox.setSelected(AppPreferences.loadBool(cmd + ".verbose", false));
		timeoutSpinner.getValueFactory().setValue(AppPreferences.loadInt(cmd + ".timeout", 0));

		if ("REDUCE".equals(cmd))
			groupCheckBox.setSelected(AppPreferences.loadBool(cmd + ".group", false));
	}
}
