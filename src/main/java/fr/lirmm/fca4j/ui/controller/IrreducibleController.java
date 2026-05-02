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
 * Contrôleur du panneau pour la commande IRREDUCIBLE. Liste les objets et/ou
 * attributs irréductibles d'un contexte formel.
 */
public class IrreducibleController extends AbstractCommandController implements Initializable {

	// ── TitledPanes et bouton ─────────────────────────────────────────────────
	@FXML
	private TitledPane inputPane;
	@FXML
	private TitledPane outputPane;
	@FXML
	private TitledPane operationPane;
	@FXML
	private TitledPane advancedPane;

	// ── Bouton édition ────────────────────────────────────────────────────────
	@FXML
	private Button editInputButton;

	// ── Entrée ────────────────────────────────────────────────────────────────
	@FXML
	private TextField inputFileField;
	@FXML
	private ComboBox<String> inputFormatCombo;

	// ── Sortie (optionnelle — fichier texte) ──────────────────────────────────
	@FXML
	private TextField outputFileField;

	// ── Opération ─────────────────────────────────────────────────────────────
	@FXML
	private CheckBox lobjCheckBox; // -lobj
	@FXML
	private CheckBox lattrCheckBox; // -lattr
	@FXML
	private CheckBox groupCheckBox; // -u

	// ── Options avancées ──────────────────────────────────────────────────────
	@FXML
	private ComboBox<String> implCombo;
	@FXML
	private Spinner<Integer> timeoutSpinner;
	@FXML
	private CheckBox verboseCheckBox;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		inputFormatCombo.getItems().addAll("(auto)", "CXT", "SLF", "XML", "CEX", "CSV");
		inputFormatCombo.setValue("(auto)");

		implCombo.getItems().addAll("BITSET", "ROARING_BITMAP", "SPARSE_BITSET", "HASHSET", "TREESET", "INT_ARRAY",
				"ARRAYLIST", "BOOL_ARRAY");
		implCombo.setValue("BITSET");

		timeoutSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 3600, 0, 10));
		Utilities.bindPathTooltip(inputFileField);
		Utilities.bindPathTooltip(outputFileField);

	}

	public void configure(CommandDescriptor desc, Consumer<CommandBuilder> onRun, Consumer<Path> openInEditor,
			Consumer<String> onInputChanged) {
        configureBase(desc, onRun, openInEditor, onInputChanged, editInputButton);

		inputPane.setText(I18n.get("section.input"));
		outputPane.setText(I18n.get("section.output"));
		operationPane.setText(I18n.get("section.operation"));
		advancedPane.setText(I18n.get("section.advanced"));

		loadPrefs();
	}

	// ── Actions ───────────────────────────────────────────────────────────────

	@FXML
	private void onEditInput() {
			editInput(inputFileField);
	}

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
		fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Texte", "*.txt"));
		File f = fc.showSaveDialog(outputFileField.getScene().getWindow());
		if (f != null)
			outputFileField.setText(f.getAbsolutePath());
	}

	@FXML
	public void onRun() {
		savePrefs();
		if(!validateInput(inputFileField)) return;
		if (!lobjCheckBox.isSelected() && !lattrCheckBox.isSelected()) {
			showError(I18n.get("rc.error.no.option.title"), I18n.get("irr.error.no.option.detail"));
			return;
		}

		CommandBuilder builder = new CommandBuilder().command("IRREDUCIBLE").inputFile(inputFileField.getText().trim())
				.listObjects(lobjCheckBox.isSelected()).listAttributes(lattrCheckBox.isSelected())
				.groupByClasses(groupCheckBox.isSelected()).verbose(verboseCheckBox.isSelected());

		if (!outputFileField.getText().isBlank())
			builder.outputFile(Utilities.resolveOutput(outputFileField.getText().trim(), inputFileField));

		String fmt = inputFormatCombo.getValue();
		if (!"(auto)".equals(fmt))
			builder.inputFormat(fmt);

		String impl = implCombo.getValue();
		if (!"BITSET".equals(impl))
			builder.implementation(impl);

		int to = timeoutSpinner.getValue();
		if (to > 0)
			builder.timeout(to);

		if (!outputFileField.getText().isBlank())
			AppPreferences.saveOutputForInput("IRREDUCIBLE", inputFileField.getText().trim(),
					outputFileField.getText().trim());
		if (onRun != null)
			onRun.accept(builder);
	}

	// ── Utilitaires ───────────────────────────────────────────────────────────



	public void setInputFile(String path) {
		if (path == null || path.isBlank())
			return;
		inputFileField.setText(path);
		autoDetectFormat(new File(path).getName(),inputFormatCombo);

		String base = path.replaceAll("\\.[^.]+$", "");

		String savedOutput = AppPreferences.loadOutputForInput("IRREDUCIBLE", path);
		outputFileField.setText(savedOutput.isBlank() ? base + "-irreducible.txt" : savedOutput);
	}

	public String getInputFile() {
		return inputFileField.getText();
	}

	protected void savePrefs() {
		String cmd = descriptor.getName(); 
		AppPreferences.saveString(cmd + ".inputFormat", inputFormatCombo.getValue());
		AppPreferences.saveString(cmd + ".impl", implCombo.getValue());
		AppPreferences.saveBool(cmd + ".lobj", lobjCheckBox.isSelected());
		AppPreferences.saveBool(cmd + ".lattr", lattrCheckBox.isSelected());
		AppPreferences.saveBool(cmd + ".group", groupCheckBox.isSelected());
		AppPreferences.saveBool(cmd + ".verbose", verboseCheckBox.isSelected());
		AppPreferences.saveInt(cmd + ".timeout", timeoutSpinner.getValue());
	}

	protected void loadPrefs() {
		String cmd = descriptor.getName();
		String fmt = AppPreferences.loadString(cmd + ".inputFormat", "(auto)");
		if (inputFormatCombo.getItems().contains(fmt))
			inputFormatCombo.setValue(fmt);


		String impl = AppPreferences.loadString(cmd + ".impl", "BITSET");
		if (implCombo.getItems().contains(impl))
			implCombo.setValue(impl);

		lobjCheckBox.setSelected(AppPreferences.loadBool(cmd + ".lobj", false));
		lattrCheckBox.setSelected(AppPreferences.loadBool(cmd + ".lattr", false));
		groupCheckBox.setSelected(AppPreferences.loadBool(cmd + ".group", false));
		verboseCheckBox.setSelected(AppPreferences.loadBool(cmd + ".verbose", false));
		timeoutSpinner.getValueFactory().setValue(AppPreferences.loadInt(cmd + ".timeout", 0));
	}

}
