package fr.lirmm.fca4j.ui.controller;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.ui.MainApp;
import fr.lirmm.fca4j.ui.model.CommandBuilder;
import fr.lirmm.fca4j.ui.model.CommandDescriptor;
import fr.lirmm.fca4j.ui.service.Fca4jRunner;
import fr.lirmm.fca4j.ui.service.GraphRenderer;
import fr.lirmm.fca4j.ui.service.RcfIntegrityService;
import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.I18n;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class MainController implements Initializable {

	// ── Widgets FXML ──────────────────────────────────────────────────────────
	@FXML
	private ComboBox<String> commandCombo;
	@FXML
	private StackPane commandPanelContainer;
	@FXML
	private WebView graphWebView;
	@FXML
	private TextArea consoleArea;
	@FXML
	private Label statusLabel;
	@FXML
	private Label selectedNodeLabel;
	@FXML
	private TabPane mainTabPane;
	// Onglet Graph
	@FXML
	private Button btnSaveDot;
	@FXML
	private Button btnExportSvg;
	@FXML
	private Button btnExportPng;
	@FXML
	private Button btnExportPdf;
	// ── Onglet RCA Family ─────────────────────────────────────────────────────
	@FXML
	private TabPane commandTabPane;
	@FXML
	private Tab contextTab;
	@FXML
	private Tab rcaTab;
	@FXML
	private StackPane rcaCommandContainer;
	@FXML
	private Tab familyEditorTab;
	@FXML
	private FamilyEditorController familyEditorController;
	private RcaCommandController rcaCommandController;

	// ── Contrôleur de l'éditeur (injecté via fx:include) ─────────────────────
	@FXML
	private ContextEditorController contextEditorController;

	// ── Services ──────────────────────────────────────────────────────────────
	private final Fca4jRunner runner = new Fca4jRunner();
	private GraphRenderer renderer;
	private final RcfIntegrityService rcfIntegrityService = new RcfIntegrityService();

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		renderer = new GraphRenderer(graphWebView.getEngine());
		renderer.setOnNodeClick(this::onNodeSelected);

		commandCombo.getItems().addAll("LATTICE", "AOCPOSET", "RULEBASIS", "DBASIS", "CLARIFY", "REDUCE", "IRREDUCIBLE",
				"INSPECT", "BINARIZE");
		commandCombo.setValue("LATTICE");
		commandCombo.valueProperty().addListener((obs, old, val) -> loadCommandPanel(val));

		selectedNodeLabel.setText(I18n.get("panel.node.none"));
		loadCommandPanel("LATTICE");

		// Titres des onglets commandes
		if (commandTabPane != null) {
			contextTab.setText(I18n.get("tab.context.commands"));
			rcaTab.setText(I18n.get("tab.rca.family"));
		}

		// Charger le panneau RCA
		loadRcaPanel();
		if (familyEditorController != null)
			familyEditorController.setOpenInContextEditor(this::openContextInEditor);
		// Synchroniser le fichier famille courant vers le panneau RCA
		if (commandTabPane != null) {
			rcaTab.selectedProperty().addListener((obs, old, selected) -> {
				if (selected && rcaCommandController != null && familyEditorController != null
						&& familyEditorController.getCurrentFile() != null) {
					rcaCommandController.setFamilyFile(familyEditorController.getCurrentFile());
				}
			});
		}

		// Titre onglet Family Editor dans la zone principale
		if (familyEditorTab != null)
			familyEditorTab.setText(I18n.get("tab.family.editor"));

		statusLabel.setText(
				AppPreferences.isFca4jConfigured() ? I18n.get("status.configured", AppPreferences.getFca4jJarPath())
						: I18n.get("status.not.configured"));
		setupGraphToolbar();
	}

	private void enableGraphButtons() {
		btnSaveDot.setDisable(false);
		btnExportSvg.setDisable(false);
		btnExportPng.setDisable(false);
		btnExportPdf.setDisable(false);
	}

	private void setupGraphToolbar() {
		setGraphToolbarBtn(btnSaveDot, new FontIcon(Material2MZ.SAVE), I18n.get("graph.btn.save.dot"));
		setGraphToolbarBtn(btnExportSvg, new FontIcon(Material2AL.IMAGE), I18n.get("graph.btn.export.svg"));
		setGraphToolbarBtn(btnExportPng, new FontIcon(Material2AL.IMAGE), I18n.get("graph.btn.export.png"));
		setGraphToolbarBtn(btnExportPdf, new FontIcon(Material2AL.INSERT_DRIVE_FILE), I18n.get("graph.btn.export.pdf"));
	}

	private void setGraphToolbarBtn(Button btn, FontIcon icon, String tooltip) {
		if (btn == null)
			return;
		icon.setIconSize(20);
		icon.setIconColor(javafx.scene.paint.Color.valueOf("#444444"));
		btn.setGraphic(icon);
		btn.setText("");
		btn.setTooltip(new Tooltip(tooltip));
	}
	// ── Chargement dynamique du panneau de commande ───────────────────────────

	private void loadCommandPanel(String command) {
		try {
			CommandDescriptor desc = CommandDescriptor.forName(command);
			if (desc == null) {
				commandPanelContainer.getChildren().setAll(new Label(I18n.get("error.panel.load", command)));
				return;
			}

			String fxml = switch (desc.getFamily()) {
			case LATTICE_AOC -> "/fr/lirmm/fca4j/ui/fxml/lattice_aoc.fxml";
			case RULE_BASIS -> "/fr/lirmm/fca4j/ui/fxml/rule_basis.fxml";
			case REDUCE_CLARIFY -> "/fr/lirmm/fca4j/ui/fxml/reduce_clarify.fxml";
			case IRREDUCIBLE -> "/fr/lirmm/fca4j/ui/fxml/irreducible.fxml";
			case INSPECT -> "/fr/lirmm/fca4j/ui/fxml/inspect.fxml";
			case BINARIZE -> "/fr/lirmm/fca4j/ui/fxml/binarize.fxml";
			};

			FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml), I18n.getBundle());
			Node panel = loader.load();

			switch (desc.getFamily()) {
			case LATTICE_AOC -> {
				LatticeAocController ctrl = loader.getController();
				ctrl.configure(desc, this::executeCommand, this::openInEditor);
			}
			case RULE_BASIS -> {
				RuleBasisController ctrl = loader.getController();
				ctrl.configure(desc, this::executeCommand, this::openInEditor);
			}
			case REDUCE_CLARIFY -> {
				ReduceClarifyController ctrl = loader.getController();
				ctrl.configure(desc, this::executeCommand, this::openInEditor);
			}
			case IRREDUCIBLE -> {
				IrreducibleController ctrl = loader.getController();
				ctrl.configure(desc, this::executeCommand, this::openInEditor);
			}
			case INSPECT -> {
				InspectController ctrl = loader.getController();
				ctrl.configure(desc, this::executeCommand, this::openInEditor);
			}
			case BINARIZE -> {
				BinarizeController ctrl = loader.getController();
				ctrl.configure(desc, this::executeCommand, this::openInEditor);
			}
			}
			commandPanelContainer.getChildren().setAll(panel);

		} catch (Exception e) {
			Throwable cause = e;
			while (cause.getCause() != null)
				cause = cause.getCause();
			appendConsole("[Erreur panneau] " + e.getClass().getSimpleName() + ": " + e.getMessage());
			appendConsole("[Cause] " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
			e.printStackTrace();
		}
	}

	// ── Chargement du panneau RCA ─────────────────────────────────────────────

	private void loadRcaPanel() {
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/fr/lirmm/fca4j/ui/fxml/rca_command.fxml"),
					I18n.getBundle());
			Node panel = loader.load();
			rcaCommandController = loader.getController();
			rcaCommandController.configure(this::executeRcaCommand, // ← nouveau handler dédié
					this::openInFamilyEditor, this::openDotInGraph // ← nouveau
			);
			if (rcaCommandContainer != null)
				rcaCommandContainer.getChildren().setAll(panel);
		} catch (Exception e) {
			appendConsole("[RCA] " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void openContextInEditor(IBinaryContext ctx) {
		if (contextEditorController != null) {
			contextEditorController.loadContextFromFamily(ctx, modifiedCtx -> {
				Platform.runLater(() -> {
					RCAFamily currentFamily = familyEditorController.getFamily();
					// Synchroniser l'intégrité référentielle
					rcfIntegrityService.synchronize(currentFamily, modifiedCtx.getName());
					familyEditorController.reloadFamily(currentFamily);
					familyEditorController.markModified();
					mainTabPane.getSelectionModel().select(2);
				});
			});
			mainTabPane.getSelectionModel().select(1);
		}
	}
	// ── Ouverture dans l'éditeur de contexte ─────────────────────────────────

	public void openInEditor(Path filePath) {
		if (contextEditorController != null) {
			contextEditorController.openFile(filePath);
			mainTabPane.getSelectionModel().select(1);
		}
	}

	// ── Ouverture dans le Family Editor ──────────────────────────────────────

	public void openInFamilyEditor(Path filePath) {
		if (familyEditorController != null) {
			familyEditorController.openFile(filePath);
			// L'onglet Family Editor est le 3ème (index 2)
			mainTabPane.getSelectionModel().select(2);
		}
	}

	// ── Exécution ─────────────────────────────────────────────────────────────

	private void executeCommand(CommandBuilder builder) {
		if (!AppPreferences.isFca4jConfigured()) {
			showAlert(I18n.get("error.not.configured.title"), I18n.get("error.not.configured.detail"));
			return;
		}

		var args = builder.build();
		consoleArea.clear();
		statusLabel.setText(I18n.get("status.running", args.get(0)));
		appendConsole("$ " + builder.toDisplayString());

		mainTabPane.getSelectionModel().select(0);

		runner.run(args, line -> Platform.runLater(() -> appendConsole(line)))
				.thenAccept(result -> Platform.runLater(() -> {
					if (result.isSuccess()) {
						statusLabel.setText(I18n.get("status.success"));
						appendConsole("\n" + I18n.get("console.ok"));
						tryRenderDot(builder);
					} else {
						statusLabel.setText(I18n.get("status.error", result.exitCode()));
						appendConsole("\n" + I18n.get("console.error") + "\n" + result.stderr());
					}
				}));
	}

	private void executeRcaCommand(CommandBuilder builder) {
		if (!AppPreferences.isFca4jConfigured()) {
			showAlert(I18n.get("error.not.configured.title"), I18n.get("error.not.configured.detail"));
			return;
		}
		var args = builder.build();
		consoleArea.clear();
		statusLabel.setText(I18n.get("status.running", args.get(0)));
		appendConsole("$ " + builder.toDisplayString());
		mainTabPane.getSelectionModel().select(0);

		runner.run(args, line -> Platform.runLater(() -> appendConsole(line)))
				.thenAccept(result -> Platform.runLater(() -> {
					if (result.isSuccess()) {
						statusLabel.setText(I18n.get("status.success"));
						appendConsole("\n" + I18n.get("console.ok"));
						// Scanner les fichiers DOT produits
						if (rcaCommandController != null)
							rcaCommandController.scanDotFiles();
					} else {
						statusLabel.setText(I18n.get("status.error", result.exitCode()));
						appendConsole("\n" + I18n.get("console.error") + "\n" + result.stderr());
					}
				}));
	}

	private void openDotInGraph(Path dotFile) {
		mainTabPane.getSelectionModel().select(0);
		appendConsole(I18n.get("console.graphviz.render", dotFile));
		renderer.render(dotFile).exceptionally(ex -> {
			Platform.runLater(() -> appendConsole("[GraphViz] " + ex.getCause().getMessage()));
			return null;
		});
		renderer.render(dotFile).thenRun(() -> Platform.runLater(this::enableGraphButtons)).exceptionally(ex -> {
			Platform.runLater(() -> appendConsole("[GraphViz] " + ex.getCause().getMessage()));
			return null;
		});
	}

	private void tryRenderDot(CommandBuilder builder) {
		var args = builder.build();
		int gIdx = args.indexOf("-g");
		if (gIdx == -1 || gIdx + 1 >= args.size())
			return;

		Path dotFile = Path.of(args.get(gIdx + 1));
		if (!dotFile.toFile().exists()) {
			appendConsole(I18n.get("error.graphviz.not.found", dotFile));
			return;
		}
		appendConsole(I18n.get("console.graphviz.render", dotFile));
		renderer.render(dotFile).exceptionally(ex -> {
			Platform.runLater(() -> appendConsole("[GraphViz] " + ex.getCause().getMessage()));
			return null;
		});
		renderer.render(dotFile).thenRun(() -> Platform.runLater(this::enableGraphButtons)).exceptionally(ex -> {
			Platform.runLater(() -> appendConsole("[GraphViz] " + ex.getCause().getMessage()));
			return null;
		});
	}

	private void onNodeSelected(String nodeLabel) {
		selectedNodeLabel.setText(nodeLabel);
		appendConsole(I18n.get("console.node.selected", nodeLabel));
	}

	// ── Actions menu ──────────────────────────────────────────────────────────

	@FXML
	private void onOpenPreferences() throws Exception {
		FXMLLoader loader = new FXMLLoader(getClass().getResource("/fr/lirmm/fca4j/ui/fxml/preferences.fxml"),
				I18n.getBundle());
		Stage dialog = new Stage();
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setTitle(I18n.get("prefs.title"));
		dialog.setScene(new Scene(loader.load()));
		dialog.showAndWait();

		statusLabel.setText(
				AppPreferences.isFca4jConfigured() ? I18n.get("status.configured", AppPreferences.getFca4jJarPath())
						: I18n.get("status.not.configured"));
	}

	@FXML
	private void onQuit() {
		Platform.exit();
	}

	@FXML
	private void onAbout() {
		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		alert.setTitle(I18n.get("menu.help.about"));
		alert.setHeaderText(MainApp.APP_TITLE + " " + MainApp.APP_VERSION);
		alert.setContentText(I18n.get("app.about.content"));

		try {
			java.io.InputStream logoStream = getClass().getResourceAsStream("/fr/lirmm/fca4j/ui/icons/fca4j-logo.png");
			if (logoStream != null) {
				ImageView logo = new ImageView(new Image(logoStream));
				logo.setFitWidth(64);
				logo.setFitHeight(64);
				logo.setPreserveRatio(true);
				logo.setSmooth(true);
				alert.setGraphic(logo);
			}
		} catch (Exception ignored) {
		}

		alert.showAndWait();
	}

	@FXML
	private void onClearConsole() {
		consoleArea.clear();
	}

	// ── Utilitaires ───────────────────────────────────────────────────────────

	private void appendConsole(String text) {
		consoleArea.appendText(text + "\n");
	}

	private void showAlert(String title, String message) {
		Alert alert = new Alert(Alert.AlertType.WARNING);
		alert.setTitle(title);
		alert.setHeaderText(null);
		alert.setContentText(message);
		alert.showAndWait();
	}

	@FXML
	private void onSaveDot() {
		Path src = renderer.getCurrentDotFile();
		if (src == null)
			return;
		FileChooser fc = new FileChooser();
		fc.setTitle(I18n.get("graph.btn.save.dot"));
		fc.setInitialFileName(src.getFileName().toString());
		fc.setInitialDirectory(src.getParent().toFile());
		fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("DOT", "*.dot"));
		File dest = fc.showSaveDialog(graphWebView.getScene().getWindow());
		if (dest == null)
			return;
		try {
			java.nio.file.Files.copy(src, dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			appendConsole(I18n.get("graph.saved.dot", dest.getName()));
		} catch (Exception e) {
			appendConsole("[Error] " + e.getMessage());
		}
	}

	@FXML
	private void onExportSvg() {
		exportViaGraphviz("svg", "*.svg");
	}

	@FXML
	private void onExportPng() {
		exportViaGraphviz("png", "*.png");
	}

	@FXML
	private void onExportPdf() {
		exportViaGraphviz("pdf", "*.pdf");
	}

	private void exportViaGraphviz(String format, String extension) {
		Path src = renderer.getCurrentDotFile();
		if (src == null)
			return;

		FileChooser fc = new FileChooser();
		fc.setTitle(I18n.get("graph.export." + format));
		fc.setInitialFileName(src.getFileName().toString().replace(".dot", "." + format));
		fc.setInitialDirectory(src.getParent().toFile());
		fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(format.toUpperCase(), extension));
		File dest = fc.showSaveDialog(graphWebView.getScene().getWindow());
		if (dest == null)
			return;

		String dotExe = AppPreferences.getDotPath();
		ProcessBuilder pb = new ProcessBuilder(dotExe, "-T" + format, src.toString(), "-o", dest.getAbsolutePath());
		pb.redirectErrorStream(true);

		CompletableFuture.runAsync(() -> {
			try {
				Process proc = pb.start();
				int exit = proc.waitFor();
				Platform.runLater(() -> {
					if (exit == 0)
						appendConsole(I18n.get("graph.exported", format.toUpperCase(), dest.getName()));
					else
						appendConsole("[GraphViz] export " + format + " failed (exit " + exit + ")");
				});
			} catch (Exception e) {
				Platform.runLater(() -> appendConsole("[GraphViz] " + e.getMessage()));
			}
		});
	}
}
