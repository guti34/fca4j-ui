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

import fr.lirmm.fca4j.ui.control.DurationField;
import fr.lirmm.fca4j.ui.model.CommandBuilder;
import fr.lirmm.fca4j.ui.model.CommandDescriptor;
import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.I18n;
import fr.lirmm.fca4j.ui.util.Utilities;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

/**
 * Contrôleur du panneau de paramètres pour DG_BASIS et DBASIS.
 */
public class RuleBasisController extends AbstractCommandController implements Initializable {

	@FXML
	private TitledPane inputPane;
	@FXML
	private TitledPane outputPane;
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

	// ── Algorithme ────────────────────────────────────────────────────────────
	// La ligne algorithme (algoRow), la fermeture et la clarification sont
	// propres à DG_BASIS ; l'implémentation est commune ; le code natif est
	// propre à DBASIS. Ces éléments vivent désormais dans une unique section
	// "Algorithme" toujours visible (cf. rule_basis.fxml).
	@FXML
	private HBox algoRow;
	@FXML
	private ComboBox<String> algoCombo;
	@FXML
	private Label closureLabel;
	@FXML
	private ComboBox<String> closureCombo;
	@FXML
	private CheckBox clarifyCheckBox;

	// ── Multithreading (DBASIS uniquement — DG_BASIS n'a plus cette option,
	// cf. RuleBasisBuilder : -t/FORKJOINPOOL a été supprimé côté moteur) ──────
	@FXML
	private HBox poolModeRow;
	@FXML
	private ComboBox<String> poolModeCombo;

	// ── Options DBASIS ────────────────────────────────────────────────────────
	@FXML
	private TitledPane dbasisPane;
	@FXML
	private Spinner<Integer> minSupportSpinner;
	@FXML
	private CheckBox enableNativeCodeCheckBox;

	// ── Options communes ──────────────────────────────────────────────────────
	// sortBySupportCheckBox (-b, DG_BASIS seulement) et implFolderField
	// (-folder, commun aux deux) vivent dans la section "Sortie" (cf.
	// rule_basis.fxml) ; leur affectation par @FXML ne dépend pas de leur
	// emplacement dans le FXML.
	@FXML
	private CheckBox sortBySupportCheckBox;
	@FXML
	private TextField reportFileField;
	@FXML
	private TextField implFolderField;
	@FXML
	private ComboBox<String> implCombo;
	 @FXML private DurationField timeoutField;
	 @FXML
	private CheckBox verboseCheckBox;

	/** Extension correspondant au format de sortie sélectionné. */
	private static String extForFormat(String fmt) {
		return switch (fmt) {
			case "JSON"    -> ".json";
			case "XML"     -> ".xml";
			case "DATALOG" -> ".dlgp";
			default        -> ".txt"; // TXT
		};
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		inputFormatCombo.getItems().addAll("(auto)", "CXT", "SLF", "CEX", "XML", "CSV");
		inputFormatCombo.setValue("(auto)");


		outputFormatCombo.getItems().addAll("TXT", "JSON", "XML", "DATALOG");
		outputFormatCombo.setValue("TXT");
		// Détail 1 : l'extension du fichier de sortie suit le format sélectionné
		bindOutputExtension(outputFormatCombo, outputFileField, RuleBasisController::extForFormat);

		closureCombo.getItems().addAll("BASIC", "WITH_HISTORY");
		closureCombo.setValue("BASIC");

		minSupportSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100000, 0, 1));

		implCombo.getItems().addAll("BITSET", "ROARING_BITMAP", "BITSET_PACKED", "SPARSE_BITSET", "TREESET", "INT_ARRAY", "ARRAYLIST",
				"BOOL_ARRAY");
		implCombo.setValue("BITSET");

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
		advancedPane.setText(I18n.get("section.advanced"));
		dbasisPane.setText(I18n.get("section.dbasis"));

		boolean isRuleBasis = "DG_BASIS".equals(desc.getName());
		boolean isDbasis = "DBASIS".equals(desc.getName());

		// Section "Algorithme" (toujours visible) :
		//  - ligne algorithme + fermeture + clarification : DG_BASIS seulement
		//  - implémentation : toujours visible
		//  - code natif : toujours visible
		algoRow.setVisible(isRuleBasis);
		algoRow.setManaged(isRuleBasis);
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

		enableNativeCodeCheckBox.setVisible(true);
		enableNativeCodeCheckBox.setManaged(true);

		// Mode de parallélisme : DBASIS uniquement. DG_BASIS n'a plus cette
		// option côté moteur (RuleBasisBuilder n'accepte plus -t depuis la
		// suppression de FORKJOINPOOL, qui dégradait les performances au lieu
		// de les améliorer) : le contrôle est masqué pour DG_BASIS plutôt que
		// de proposer un choix qui ferait échouer la commande.
		poolModeRow.setVisible(isDbasis);
		poolModeRow.setManaged(isDbasis);
		if (isDbasis) {
			poolModeCombo.getItems().setAll("MONO", "MULTITHREAD");
			poolModeCombo.setValue("MULTITHREAD");
		}

		// Panneau DBASIS
		dbasisPane.setVisible(isDbasis);
		dbasisPane.setManaged(isDbasis);

		// Tri par support (-b) : DG_BASIS seulement. DBASIS n'a plus cette
		// option : l'ordre des implications y est contraint (binaires triées
		// en tête), donc un tri global par support n'a pas de sens.
		// Dossier de résultats par support (-folder) reste commun aux deux.
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
        Utilities.setSafeInitialDirectory(fc, AppPreferences.getLastDirectory());
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
        Utilities.setSafeInitialDirectory(fc, AppPreferences.getLastDirectory());
		File f = fc.showSaveDialog(outputFileField.getScene().getWindow());
		if (f != null) {
			outputFileField.setText(Utilities.relativizeForDisplay(f.getAbsolutePath(),
					inputFileField.getText().trim()));
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
        Utilities.setSafeInitialDirectory(fc, AppPreferences.getLastDirectory());
		File f = fc.showSaveDialog(reportFileField.getScene().getWindow());
		if (f != null)
			reportFileField.setText(f.getAbsolutePath());
	}

	@FXML
	private void onBrowseImplFolder() {
		javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
		dc.setTitle(I18n.get("browse.impl.folder.title"));
        Utilities.setSafeInitialDirectory(dc, AppPreferences.getLastDirectory());
		File f = dc.showDialog(implFolderField.getScene().getWindow());
		if (f != null)
			// Comme pour le fichier de sortie : afficher un chemin relatif au
			// contexte courant quand c'est possible, résolu en absolu à l'exécution
			// (cf. onRun / Utilities.resolveOutput).
			implFolderField.setText(Utilities.relativizeForDisplay(f.getAbsolutePath(),
					inputFileField.getText().trim()));
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

		int to = timeoutField.getSeconds(); if (to > 0) builder.timeout(to);

		// Options communes à DG_BASIS et DBASIS : rapport d'exécution (-r)
		// et dossier de résultats par support (-folder).
		if (!reportFileField.getText().isBlank())
			builder.reportFile(Utilities.resolveOutput(reportFileField.getText().trim(),inputFileField));
		if (!implFolderField.getText().isBlank())
			builder.implFolder(Utilities.resolveOutput(implFolderField.getText().trim(),inputFileField));

		if ("DG_BASIS".equals(descriptor.getName())) {
			builder.algorithm(algoCombo.getValue()).clarify(clarifyCheckBox.isSelected())
					.closureMethod(closureCombo.getValue())
					.sortBySupport(sortBySupportCheckBox.isSelected()); // -b : DG_BASIS seulement
		}

		if ("DBASIS".equals(descriptor.getName())) {
			int ms = minSupportSpinner.getValue();
			if (ms > 0)
				builder.minimalSupport(ms);
			if (enableNativeCodeCheckBox.isSelected())
				builder.enableNativeCode(true);
			builder.poolMode(poolModeCombo.getValue());
		}

		if (onRun != null)
			onRun.accept(builder);
	}

	public void setInputFile(String path) {
	    if (path == null || path.isBlank()) return;
	    applyInputWithOutput(inputFileField, outputFileField, path, "-rules",
	            () -> extForFormat(outputFormatCombo.getValue()));
	    autoDetectFormat(new File(path).getName(), inputFormatCombo);
	}

	public String getInputFile() {
		return inputFileField.getText();
	}
	public void savePrefs() {
	    String cmd = descriptor.getName(); // "DG_BASIS" ou "DBASIS"
	    AppPreferences.saveString(cmd + ".outputFormat", outputFormatCombo.getValue());
	    AppPreferences.saveString(cmd + ".impl",         implCombo.getValue());
	    AppPreferences.saveBool  (cmd + ".verbose",      verboseCheckBox.isSelected());
        AppPreferences.saveInt(cmd + ".timeout", timeoutField.getSeconds());
	    persistOutputForInput(inputFileField, outputFileField);
	    AppPreferences.saveString(cmd + ".reportFile",   reportFileField.getText().trim());
	    AppPreferences.saveString(cmd + ".implFolder",   implFolderField.getText().trim());
	    AppPreferences.saveBool(cmd + ".enableNativeCode", enableNativeCodeCheckBox.isSelected());

	    if ("DG_BASIS".equals(cmd)) {
	        AppPreferences.saveString(cmd + ".algo",     algoCombo.getValue());
	        AppPreferences.saveString(cmd + ".closure",  closureCombo.getValue());
	        AppPreferences.saveBool  (cmd + ".clarify",  clarifyCheckBox.isSelected());
	        AppPreferences.saveBool  (cmd + ".sort",     sortBySupportCheckBox.isSelected());
	    }
	    if ("DBASIS".equals(cmd)) {
	        AppPreferences.saveInt(cmd + ".minSupport", minSupportSpinner.getValue());
	        AppPreferences.saveString(cmd + ".poolMode", poolModeCombo.getValue());
	    }
	}

	public void loadPrefs() {
	    String cmd = descriptor.getName();
	    String fmt = AppPreferences.loadString(cmd + ".outputFormat", "TXT");
	    if (outputFormatCombo.getItems().contains(fmt)) outputFormatCombo.setValue(fmt);

	    String impl = AppPreferences.loadString(cmd + ".impl", "BITSET");
	    if (implCombo.getItems().contains(impl)) implCombo.setValue(impl);

	    verboseCheckBox.setSelected(AppPreferences.loadBool(cmd + ".verbose", false));
	    timeoutField.setSeconds(AppPreferences.loadInt(cmd + ".timeout", 0));

	    if ("DG_BASIS".equals(cmd)) {
	        String algo = AppPreferences.loadString(cmd + ".algo",
	            descriptor.getDefaultAlgorithm());
	        if (algoCombo.getItems().contains(algo)) algoCombo.setValue(algo);

	        String closure = AppPreferences.loadString(cmd + ".closure", "BASIC");
	        if (closureCombo.getItems().contains(closure)) closureCombo.setValue(closure);

	        clarifyCheckBox.setSelected(AppPreferences.loadBool(cmd + ".clarify",   false));
	        sortBySupportCheckBox.setSelected(AppPreferences.loadBool(cmd + ".sort", false));
	    }
	    if ("DBASIS".equals(cmd)) {
	        minSupportSpinner.getValueFactory().setValue(
	            AppPreferences.loadInt(cmd + ".minSupport", 0));
	        String pool = AppPreferences.loadString(cmd + ".poolMode", "MULTITHREAD");
	        if (poolModeCombo.getItems().contains(pool)) poolModeCombo.setValue(pool);
	    }
	    enableNativeCodeCheckBox.setSelected(
	            AppPreferences.loadBool(cmd + ".enableNativeCode", false));
	    String savedReport = AppPreferences.loadString(cmd + ".reportFile", "");
	    if (!savedReport.isBlank()) reportFileField.setText(savedReport);
	    String savedImplFolder = AppPreferences.loadString(cmd + ".implFolder", "");
	    if (!savedImplFolder.isBlank()) implFolderField.setText(savedImplFolder);
	}
	}
