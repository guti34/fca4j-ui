package fr.lirmm.fca4j.ui.controller;

import fr.lirmm.fca4j.ui.model.CommandBuilder;
import fr.lirmm.fca4j.ui.model.CommandDescriptor;
import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.I18n;
import fr.lirmm.fca4j.ui.util.Utilities;

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
 * Contrôleur du panneau pour la commande INSPECT. Inspecte un contexte formel
 * et affiche ses statistiques dans la console.
 */
public class InspectController extends AbstractCommandController implements Initializable {

	// ── TitledPanes et bouton ─────────────────────────────────────────────────
	@FXML
	private TitledPane inputPane;
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

	// ── Options avancées ──────────────────────────────────────────────────────
	@FXML
	private Spinner<Integer> timeoutSpinner;
	@FXML
	private CheckBox verboseCheckBox;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		inputFormatCombo.getItems().addAll("(auto)", "CXT", "SLF", "XML", "CEX", "CSV");
		inputFormatCombo.setValue("(auto)");

		timeoutSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 3600, 0, 10));
		Utilities.bindPathTooltip(inputFileField);
	}

	public void configure(CommandDescriptor desc, Consumer<CommandBuilder> onRun, Consumer<Path> openInEditor,
			Consumer<String> onInputChanged) {

		configureBase(desc, onRun, openInEditor, onInputChanged, editInputButton);

		inputPane.setText(I18n.get("section.input"));
		advancedPane.setText(I18n.get("section.advanced"));
	}

	// ── Actions ───────────────────────────────────────────────────────────────

	@FXML
	private void onEditInput() {
			editInput(inputFileField);
	}

	@FXML
	private void onBrowseInput() {
		FileChooser fc = new FileChooser();
		fc.setTitle(I18n.get("label.input.file"));
		fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
		fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter(I18n.get("filter.context.all"), "*.cxt",
				"*.slf", "*.cex", "*.xml", "*.csv"), new FileChooser.ExtensionFilter(I18n.get("filter.all"), "*.*"));
		File f = fc.showOpenDialog(inputFileField.getScene().getWindow());
		if (f != null) {
			inputFileField.setText(f.getAbsolutePath());
			if (onInputChanged != null)
				onInputChanged.accept(f.getAbsolutePath());
			AppPreferences.setLastDirectory(f.getParent());
			autoDetectFormat(f.getName(),inputFormatCombo);
		}
	}

	@FXML
	public void onRun() {
		if(!validateInput(inputFileField)) return;

		CommandBuilder builder = new CommandBuilder().command("INSPECT").inputFile(inputFileField.getText().trim())
				.verbose(verboseCheckBox.isSelected());

		String fmt = inputFormatCombo.getValue();
		if (!"(auto)".equals(fmt))
			builder.inputFormat(fmt);

		int to = timeoutSpinner.getValue();
		if (to > 0)
			builder.timeout(to);

		if (onRun != null)
			onRun.accept(builder);
	}

	// ── Utilitaires ───────────────────────────────────────────────────────────



	public void setInputFile(String path) {
		if (path == null || path.isBlank())
			return;
		inputFileField.setText(path);
		autoDetectFormat(new File(path).getName(),inputFormatCombo);
		// Pas d'output pour INSPECT
	}

	public String getInputFile() {
		return inputFileField.getText();
	}

	@Override
	protected void savePrefs() {
		String cmd = descriptor.getName(); // "LATTICE" ou "AOCPOSET"
		AppPreferences.saveBool(cmd + ".verbose", verboseCheckBox.isSelected());
		AppPreferences.saveInt(cmd + ".timeout", timeoutSpinner.getValue());
		
	}

	@Override
	protected void loadPrefs() {
		String cmd = descriptor.getName(); // "LATTICE" ou "AOCPOSET"
		verboseCheckBox.setSelected(AppPreferences.loadBool(cmd + ".verbose", false));
		timeoutSpinner.getValueFactory().setValue(AppPreferences.loadInt(cmd + ".timeout", 0));
		
	}

}
