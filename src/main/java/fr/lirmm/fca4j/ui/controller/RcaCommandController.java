package fr.lirmm.fca4j.ui.controller;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.ui.model.CommandBuilder;
import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.I18n;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Panneau de paramètres pour la commande RCA. Reçoit le fichier famille depuis
 * le FamilyEditor ou via un sélecteur.
 */
public class RcaCommandController implements Initializable {

	// ── TitledPanes et bouton ─────────────────────────────────────────────────
	@FXML
	private TitledPane inputPane;
	@FXML
	private TitledPane outputPane;
	@FXML
	private TitledPane algoPane;
	@FXML
	private TitledPane namingPane;
	@FXML
	private TitledPane graphvizPane;
	@FXML
	private TitledPane advancedPane;
	@FXML
	private Button runButton;
	@FXML
	private Button editFamilyButton;

	// ── Entrée ────────────────────────────────────────────────────────────────
	@FXML
	private TextField familyFileField;
	@FXML
	private ComboBox<String> familyFormatCombo;

	// ── Sortie ────────────────────────────────────────────────────────────────
	@FXML
	private TextField outputFolderField;
	@FXML
	private CheckBox storeExtendedFamily;
	@FXML
	private CheckBox storeExtendedEachStep;
	@FXML
	private CheckBox buildXml;
	@FXML
	private CheckBox addFullExtents;
	@FXML
	private CheckBox addFullIntents;
	@FXML
	private Spinner<Integer> limitStepsSpinner;

	// ── Algorithme ────────────────────────────────────────────────────────────
	@FXML
	private ComboBox<String> algoCombo;
	@FXML
	private Label icebergLabel;
	@FXML
	private Spinner<Integer> icebergSpinner;

	// ── Renommage ────────────────────────────────────────────────────────────
	@FXML
	private CheckBox renameRA;
	@FXML
	private CheckBox renameRAI;
	@FXML
	private CheckBox renameRI;
	@FXML
	private CheckBox nativeOnly;
	@FXML
	private CheckBox cleanOption;

	// ── GraphViz ──────────────────────────────────────────────────────────────
	@FXML
	private CheckBox buildDot;
	@FXML
	private CheckBox buildDotEachStep;
	@FXML
	private ComboBox<String> displayModeCombo;
	@FXML
	private CheckBox stability;
	@FXML
	private CheckBox buildJson;
	@FXML
	private CheckBox buildJsonEachStep;

	// ── Options avancées ──────────────────────────────────────────────────────
	@FXML
	private Spinner<Integer> timeoutSpinner;
	@FXML
	private CheckBox verboseCheckBox;

	// Results
	@FXML
	private TitledPane resultsPane;
	@FXML
	private Label resultsFolderLabel;
	@FXML
	private ListView<String> dotFilesList;

	private Path outputFolder;
	
	private Consumer<Path> openInGraph;
	
	// ── Callbacks ─────────────────────────────────────────────────────────────
	private Consumer<CommandBuilder> onRun;
	private Consumer<Path> openInFamilyEditor;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		familyFormatCombo.getItems().addAll("RCFT", "RCFGZ", "RCFAL");
		familyFormatCombo.setValue("RCFT");

		algoCombo.getItems().addAll("HERMES", "ARES", "CERES", "PLUTON", "ADD_EXTENT", "ADD_INTENT", "ICEBERG");
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

		limitStepsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 1000, 0, 1));
		icebergSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 50, 5));
		timeoutSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 3600, 0, 10));

		// Renommage mutuellement exclusif
		renameRA.selectedProperty().addListener((obs, o, v) -> {
			if (v) {
				renameRAI.setSelected(false);
				renameRI.setSelected(false);
			}
		});
		renameRAI.selectedProperty().addListener((obs, o, v) -> {
			if (v) {
				renameRA.setSelected(false);
				renameRI.setSelected(false);
			}
		});
		renameRI.selectedProperty().addListener((obs, o, v) -> {
			if (v) {
				renameRA.setSelected(false);
				renameRAI.setSelected(false);
			}
		});
	}

	public void configure(Consumer<CommandBuilder> onRun, Consumer<Path> openInFamilyEditor,
			Consumer<Path> openInGraph) {
		this.openInGraph = openInFamilyEditor;
		this.onRun = onRun;
		this.openInFamilyEditor = openInFamilyEditor;
		this.openInGraph = openInGraph;

		inputPane.setText(I18n.get("section.input"));
		outputPane.setText(I18n.get("rca.section.output"));
		algoPane.setText(I18n.get("section.algorithm"));
		namingPane.setText(I18n.get("rca.section.naming"));
		graphvizPane.setText(I18n.get("section.graphviz"));
		advancedPane.setText(I18n.get("section.advanced"));
		runButton.setText(I18n.get("button.run"));

		FontIcon editIcon = new FontIcon(Material2AL.EDIT);
		editIcon.setIconSize(14);
		editFamilyButton.setGraphic(editIcon);
		editFamilyButton.setText("");
		editFamilyButton.setTooltip(new Tooltip(I18n.get("rca.btn.open.family.editor")));
		resultsPane.setText(I18n.get("rca.section.results"));

		// Clic sur un fichier DOT → rendu dans l'onglet Graph
		dotFilesList.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> {
			if (val != null && outputFolder != null)
				openInGraph.accept(outputFolder.resolve(val));
		});
	}
	private Path defaultOutputFolder() {
		String family = familyFileField.getText().trim();
		if (family.isBlank())
			return null;
		Path p = Path.of(family);
		String baseName = p.getFileName().toString().replaceAll("\\.[^.]+$", "");
		return p.getParent().resolve(baseName + "-rca");
	}

	/** Pré-remplit le champ famille depuis le FamilyEditor. */
	public void setFamilyFile(Path path) {
		if (path != null) {
			familyFileField.setText(path.toString());
			// Détecter le format
			String name = path.getFileName().toString().toLowerCase();
			if (name.endsWith(".rcfgz"))
				familyFormatCombo.setValue("RCFGZ");
			else if (name.endsWith(".rcfal"))
				familyFormatCombo.setValue("RCFAL");
			else
				familyFormatCombo.setValue("RCFT");
		}
	}

	// ── Actions ───────────────────────────────────────────────────────────────

	@FXML
	private void onBrowseFamily() {
		FileChooser fc = new FileChooser();
		fc.setTitle(I18n.get("rca.browse.family"));
		fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
		fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("RCFT", "*.rcft"),
				new FileChooser.ExtensionFilter("RCFGZ", "*.rcfgz"),
				new FileChooser.ExtensionFilter("RCFAL", "*.rcfal"),
				new FileChooser.ExtensionFilter(I18n.get("filter.all"), "*.*"));
		File f = fc.showOpenDialog(familyFileField.getScene().getWindow());
		if (f != null) {
			familyFileField.setText(f.getAbsolutePath());
			AppPreferences.setLastDirectory(f.getParent());
			setFamilyFile(f.toPath());
		}
	}

	@FXML
	private void onEditFamily() {
		String path = familyFileField.getText().trim();
		if (path.isBlank()) {
			showError(I18n.get("rca.error.no.family.title"), I18n.get("rca.error.no.family.detail"));
			return;
		}
		if (openInFamilyEditor != null)
			openInFamilyEditor.accept(Path.of(path));
	}

	@FXML
	private void onBrowseOutputFolder() {
		if (outputFolderField.getText().isBlank()) {
			Path def = defaultOutputFolder();
			if (def != null)
				outputFolderField.setText(def.toString());
		}
		DirectoryChooser dc = new DirectoryChooser();
		dc.setTitle(I18n.get("rca.browse.output"));
		String current = outputFolderField.getText().trim();
		File init = current.isBlank() ? new File(AppPreferences.getLastDirectory()) : new File(current).getParentFile();
		if (init != null && init.exists())
			dc.setInitialDirectory(init);
		File f = dc.showDialog(outputFolderField.getScene().getWindow());
		if (f != null)
			outputFolderField.setText(f.getAbsolutePath());
	}

	@FXML
	private void onRun() {
		if (familyFileField.getText().isBlank()) {
			showError(I18n.get("rca.error.no.family.title"), I18n.get("rca.error.no.family.detail"));
			return;
		}
		if (outputFolderField.getText().isBlank()) {
			Path def = defaultOutputFolder();
			if (def != null)
				outputFolderField.setText(def.toString());
		}
		outputFolder = outputFolderField.getText().isBlank() ? null : Path.of(outputFolderField.getText().trim());
		CommandBuilder builder = new CommandBuilder().command("RCA").inputFile(familyFileField.getText().trim())
				.algorithm(algoCombo.getValue()).verbose(verboseCheckBox.isSelected());

		// Format famille
		String fmt = familyFormatCombo.getValue();
		if (!"RCFT".equals(fmt))
			builder.familyFormat(fmt);

		// Dossier de sortie
		if (!outputFolderField.getText().isBlank())
			builder.outputFile(outputFolderField.getText().trim());

		// Options de sortie
		if (storeExtendedFamily.isSelected())
			builder.rcaStoreExtended(true);
		if (storeExtendedEachStep.isSelected())
			builder.rcaStoreExtendedEachStep(true);
		if (buildXml.isSelected())
			builder.rcaBuildXml(true);
		if (addFullExtents.isSelected())
			builder.rcaFullExtents(true);
		if (addFullIntents.isSelected())
			builder.rcaFullIntents(true);
		int limit = limitStepsSpinner.getValue();
		if (limit > 0)
			builder.rcaLimit(limit);

		// Algorithme
		if ("ICEBERG".equals(algoCombo.getValue()))
			builder.icebergPercent(icebergSpinner.getValue());

		// Renommage
		if (renameRA.isSelected())
			builder.rcaRenameRA(true);
		if (renameRAI.isSelected())
			builder.rcaRenameRAI(true);
		if (renameRI.isSelected())
			builder.rcaRenameRI(true);
		if (nativeOnly.isSelected())
			builder.rcaNativeOnly(true);
		if (cleanOption.isSelected())
			builder.rcaClean(true);

		// GraphViz
		if (buildDot.isSelected())
			builder.rcaBuildDot(true);
		if (buildDotEachStep.isSelected())
			builder.rcaBuildDotEachStep(true);
		if (!"SIMPLIFIED".equals(displayModeCombo.getValue()))
			builder.displayMode(displayModeCombo.getValue());
		if (stability.isSelected())
			builder.stability(true);
		if (buildJson.isSelected())
			builder.rcaBuildJson(true);
		if (buildJsonEachStep.isSelected())
			builder.rcaBuildJsonEachStep(true);

		int to = timeoutSpinner.getValue();
		if (to > 0)
			builder.timeout(to);

		if (onRun != null)
			onRun.accept(builder);
	}

	public void scanDotFiles() {
		dotFilesList.getItems().clear();
		if (outputFolder == null || !outputFolder.toFile().exists())
			return;

		try {
			java.nio.file.Files.list(outputFolder).filter(p -> p.getFileName().toString().matches("step\\d+\\.dot"))
					.sorted((a, b) -> {
						// Tri numérique : step0 < step1 < ... < step10
						int na = extractStepNumber(a.getFileName().toString());
						int nb = extractStepNumber(b.getFileName().toString());
						return Integer.compare(na, nb);
					}).forEach(p -> dotFilesList.getItems().add(p.getFileName().toString()));

			if (!dotFilesList.getItems().isEmpty()) {
				resultsPane.setExpanded(true);
				resultsFolderLabel.setText(outputFolder.toString());
			}
		} catch (Exception e) {
			resultsFolderLabel.setText(I18n.get("rca.results.error", e.getMessage()));
		}
	}

	private int extractStepNumber(String filename) {
		try {
			return Integer.parseInt(filename.replace("step", "").replace(".dot", ""));
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private void showError(String title, String msg) {
		Alert a = new Alert(Alert.AlertType.ERROR);
		a.setTitle(title);
		a.setHeaderText(null);
		a.setContentText(msg);
		a.showAndWait();
	}
}
