package fr.lirmm.fca4j.ui.controller;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

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
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
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
	private Label statusFca4jLabel;
	@FXML
	private Label statusGraphvizLabel;
	@FXML
	private Label selectedNodeLabel;
	@FXML
	private TabPane mainTabPane;

	// ── Toolbar Graph ─────────────────────────────────────────────────────────
	@FXML
	private Button btnMagnifier;
	@FXML
	private Button btnSaveDot;
	@FXML
	private Button btnExportSvg;
	@FXML
	private Button btnExportPng;
	@FXML
	private Button btnExportPdf;
	// Menu
	@FXML
	private Menu recentContextMenu;
	@FXML
	private Menu recentFamilyMenu;
	@FXML
	private Menu recentModelMenu;
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

	// ── Onglet Import ─────────────────────────────────────────────────────
	@FXML
	private Tab importTab;
	@FXML
	private StackPane importCommandContainer;
	@FXML
	private Button importRunButton;

	// ── Contrôleur de l'éditeur de contexte ──────────────────────────────────
	@FXML
	private ContextEditorController contextEditorController;
	@FXML
	private Button contextRunButton;
	@FXML
	private Button rcaRunButton;
	// ── Services ──────────────────────────────────────────────────────────────
	private final Fca4jRunner runner = new Fca4jRunner();
	private GraphRenderer renderer;
	private final RcfIntegrityService rcfIntegrityService = new RcfIntegrityService();

	// ── État ──────────────────────────────────────────────────────────────────
	private String lastInputFile = "";
	private Object currentCommandController = null;
	private ImportCommandController importCommandController;

	private javafx.scene.layout.VBox loadingOverlay;
	private javafx.scene.layout.VBox loadingOverlayNoStop;
	private javafx.scene.control.Button btnStop;
	private java.util.concurrent.ScheduledFuture<?> overlayTimer;
	private final java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors
			.newSingleThreadScheduledExecutor(r -> {
				Thread t = new Thread(r, "overlay-timer");
				t.setDaemon(true);
				return t;
			});
	@FXML
	private StackPane centerStack; // wrappera le SplitPane
	@FXML
	private Tab modelEditorTab;
	@FXML
	private ModelEditorController modelEditorController;
	@FXML
	private Label lastCommandLabel;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		renderer = new GraphRenderer(graphWebView.getEngine());
		renderer.setOnNodeClick(this::onNodeSelected);

		commandCombo.getItems().addAll("LATTICE", "AOCPOSET", "RULEBASIS", "DBASIS", "CLARIFY", "REDUCE", "IRREDUCIBLE",
				"INSPECT");
		commandCombo.setValue("LATTICE");
		commandCombo.valueProperty().addListener((obs, old, val) -> loadCommandPanel(val));

		selectedNodeLabel.setText(I18n.get("panel.node.none"));
		loadCommandPanel("LATTICE");
		contextRunButton.setText(I18n.get("button.run"));
		rcaRunButton.setText(I18n.get("button.run"));
		// Titres des onglets commandes
		if (commandTabPane != null) {
			contextTab.setText(I18n.get("tab.context.commands"));
			rcaTab.setText(I18n.get("tab.rca.family"));
		}
		// menu
		refreshRecentMenus();
		// Charger le panneau RCA
		loadRcaPanel();

		// Charger le panneau Import
		importTab.setText(I18n.get("tab.import.commands"));
		importRunButton.setText(I18n.get("button.run"));
		loadImportPanel();

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

		// Titre onglet Family Editor
		if (familyEditorTab != null)
			familyEditorTab.setText(I18n.get("tab.family.editor"));

		// Populer le champ input depuis l'éditeur de contexte
		contextEditorController.setOnFileLoaded(path -> {
			lastInputFile = path;
			propagateInputFile(path);
			AppPreferences.addRecentContext(path);
			refreshRecentMenus();
		});
		contextEditorController.setOnLoadCallbacks(this::showLoadingOverlay, this::hideLoadingOverlay);

		// Titre onglet Family Editor
		if (familyEditorTab != null)
			familyEditorTab.setText(I18n.get("tab.family.editor"));
		familyEditorController.setOnFileOpened(path -> {
			AppPreferences.addRecentFamily(path.toString());
			refreshRecentMenus();
		});

		// Titre onglet Model Editor
		if (modelEditorTab != null)
			modelEditorTab.setText(I18n.get("tab.model.editor"));
		modelEditorController.setOnFileOpened(path -> {
			AppPreferences.addRecentModel(path.toString());
			refreshRecentMenus();
		});
		contextEditorController.setOnLoadCallbacks(this::showLoadingOverlay, this::hideLoadingOverlay);
		updateStatusBar();
		setupGraphToolbar();
		buildLoadingOverlay();
		familyEditorController.setOnFileOpened(path -> {
			AppPreferences.addRecentFamily(path.toString());
			refreshRecentMenus();
		});
	}

	private void setLastCommandStatus(String command, boolean success, long durationMs) {
		String text;
		String color;
		if (success) {
			text = command + " — " + formatDuration(durationMs);
			color = "#2a7a2a"; // vert
		} else {
			text = I18n.get("status.command.failed", command);
			color = "#cc3333"; // rouge
		}
		Platform.runLater(() -> {
			lastCommandLabel.setText(text);
			lastCommandLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
		});
	}

	private String formatDuration(long ms) {
		if (ms < 1000)
			return ms + " ms";
		long s = ms / 1000;
		if (s < 60)
			return s + " s";
		return (s / 60) + " min " + (s % 60) + " s";
	}

	private void buildLoadingOverlay() {
		// Fond semi-transparent
		javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(16);
		box.setAlignment(javafx.geometry.Pos.CENTER);
		box.setStyle("-fx-background-color: rgba(0,0,0,0.35);");
		javafx.scene.layout.VBox.setVgrow(box, javafx.scene.layout.Priority.ALWAYS);

		// Spinner
		javafx.scene.control.ProgressIndicator spinner = new javafx.scene.control.ProgressIndicator(-1);
		spinner.setMaxSize(60, 60);

		// Label
		javafx.scene.control.Label lbl = new javafx.scene.control.Label(I18n.get("status.running.wait"));
		lbl.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");

		// Bouton Stop
		btnStop = new javafx.scene.control.Button(I18n.get("button.stop"));
		btnStop.setStyle("-fx-background-color: #cc3333; -fx-text-fill: white;"
				+ "-fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 24;");
		btnStop.setOnAction(e -> onStopCommand());

		box.getChildren().addAll(spinner, lbl, btnStop);
		box.setVisible(false);
		box.setMouseTransparent(false);
		this.loadingOverlay = box;

		// Overlay sans bouton Stop (pour chargements internes)
		javafx.scene.layout.VBox box2 = new javafx.scene.layout.VBox(16);
		box2.setAlignment(javafx.geometry.Pos.CENTER);
		box2.setStyle("-fx-background-color: rgba(0,0,0,0.35);");

		javafx.scene.control.ProgressIndicator spinner2 = new javafx.scene.control.ProgressIndicator(-1);
		spinner2.setMaxSize(60, 60);

		javafx.scene.control.Label lbl2 = new javafx.scene.control.Label(I18n.get("status.loading.wait"));
		lbl2.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");

		box2.getChildren().addAll(spinner2, lbl2);
		box2.setVisible(false);
		box2.setMouseTransparent(false);
		this.loadingOverlayNoStop = box2;
	}

	private void showOverlayDelayed() {
		overlayTimer = scheduler.schedule(() -> Platform.runLater(() -> {
			if (runner.isRunning() || renderer.isRendering()) { // ← ajouter renderer
				if (!centerStack.getChildren().contains(loadingOverlay))
					centerStack.getChildren().add(loadingOverlay);
				loadingOverlay.setVisible(true);
			}
		}), 4, java.util.concurrent.TimeUnit.SECONDS);
	}

	private void hideOverlay() {
		if (overlayTimer != null)
			overlayTimer.cancel(false);
		Platform.runLater(() -> loadingOverlay.setVisible(false));
	}

	private void showLoadingOverlay() {
		// Appel direct si on est sur le thread JavaFX, sinon runLater
		if (Platform.isFxApplicationThread()) {
			if (!centerStack.getChildren().contains(loadingOverlayNoStop))
				centerStack.getChildren().add(loadingOverlayNoStop);
			loadingOverlayNoStop.setVisible(true);
		} else {
			Platform.runLater(() -> {
				if (!centerStack.getChildren().contains(loadingOverlayNoStop))
					centerStack.getChildren().add(loadingOverlayNoStop);
				loadingOverlayNoStop.setVisible(true);
			});
		}
	}

	private void hideLoadingOverlay() {
		Platform.runLater(() -> loadingOverlayNoStop.setVisible(false));
	}

	private void updateStatusBar() {
		// FCA4J jar
		if (AppPreferences.isFca4jConfigured()) {
			String jarPath = AppPreferences.getFca4jJarPath();
			String jarName = Path.of(jarPath).getFileName().toString();
			statusFca4jLabel.setText(jarName);
			statusFca4jLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #2a7a2a;");
		} else {
			statusFca4jLabel.setText(I18n.get("status.not.configured"));
			statusFca4jLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #cc3333;");
		}

		// GraphViz
		String dotPath = AppPreferences.getDotPath();
		File dotFile = new File(dotPath);
		if (dotFile.exists() && dotFile.canExecute()) {
			statusGraphvizLabel.setText(dotFile.getName());
			statusGraphvizLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #2a7a2a;");
		} else {
			statusGraphvizLabel.setText(I18n.get("status.graphviz.absent"));
			statusGraphvizLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #cc3333;");
		}
	}
	// ── Toolbar Graph ─────────────────────────────────────────────────────────

	private void setupGraphToolbar() {
		setGraphToolbarBtn(btnSaveDot, new FontIcon(Material2MZ.SAVE), I18n.get("graph.btn.save.dot"));
		setGraphToolbarBtn(btnExportSvg, new FontIcon(Material2AL.IMAGE), I18n.get("graph.btn.export.svg"));
		setGraphToolbarBtn(btnExportPng, new FontIcon(Material2AL.IMAGE), I18n.get("graph.btn.export.png"));
		setGraphToolbarBtn(btnExportPdf, new FontIcon(Material2AL.INSERT_DRIVE_FILE), I18n.get("graph.btn.export.pdf"));
		setGraphToolbarBtn(btnMagnifier, new FontIcon(Material2MZ.SEARCH), I18n.get("graph.btn.magnifier"));
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

	private void enableGraphButtons() {
		btnSaveDot.setDisable(false);
		btnExportSvg.setDisable(false);
		btnExportPng.setDisable(false);
		btnExportPdf.setDisable(false);
		btnMagnifier.setDisable(false);
	}

	// ── Gestion de l'input persistant entre commandes ─────────────────────────

	private void propagateInputFile(String path) {
		lastInputFile = path;
		if (currentCommandController instanceof LatticeAocController c)
			c.setInputFile(path);
		else if (currentCommandController instanceof RuleBasisController c)
			c.setInputFile(path);
		else if (currentCommandController instanceof ReduceClarifyController c)
			c.setInputFile(path);
		else if (currentCommandController instanceof IrreducibleController c)
			c.setInputFile(path);
		else if (currentCommandController instanceof InspectController c)
			c.setInputFile(path);
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
				ctrl.configure(desc, this::executeCommand, this::openInEditor, path -> {
					lastInputFile = path;
				});
				ctrl.setInputFile(lastInputFile);
				currentCommandController = ctrl;
			}
			case RULE_BASIS -> {
				RuleBasisController ctrl = loader.getController();
				ctrl.configure(desc, this::executeCommand, this::openInEditor, path -> {
					lastInputFile = path;
				});
				ctrl.setInputFile(lastInputFile);
				currentCommandController = ctrl;
			}
			case REDUCE_CLARIFY -> {
				ReduceClarifyController ctrl = loader.getController();
				ctrl.configure(desc, this::executeCommand, this::openInEditor, path -> {
					lastInputFile = path;
				});
				ctrl.setInputFile(lastInputFile);
				currentCommandController = ctrl;
			}
			case IRREDUCIBLE -> {
				IrreducibleController ctrl = loader.getController();
				ctrl.configure(desc, this::executeCommand, this::openInEditor, path -> {
					lastInputFile = path;
				});
				ctrl.setInputFile(lastInputFile);
				currentCommandController = ctrl;
			}
			case INSPECT -> {
				InspectController ctrl = loader.getController();
				ctrl.configure(desc, this::executeCommand, this::openInEditor, path -> {
					lastInputFile = path;
				});
				ctrl.setInputFile(lastInputFile);
				currentCommandController = ctrl;
			}
			case BINARIZE -> {
				BinarizeController ctrl = loader.getController();
				ctrl.configure(desc, this::executeCommand, this::openInEditor);
				// Pas de setInputFile pour BinarizeController
				currentCommandController = ctrl;
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
			rcaCommandController.configure(this::executeRcaCommand, this::openInFamilyEditor, this::openDotInGraph);
			if (rcaCommandContainer != null)
				rcaCommandContainer.getChildren().setAll(panel);
		} catch (Exception e) {
			appendConsole("[RCA] " + e.getMessage());
			e.printStackTrace();
		}
	}
	// ── Chargement du panneau Import ─────────────────────────────────────────────

	private void loadImportPanel() {
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/fr/lirmm/fca4j/ui/fxml/import_command.fxml"),
					I18n.getBundle());
			Node panel = loader.load();
			ImportCommandController ctrl = loader.getController();
			ctrl.configure(this::executeCommand);
			ctrl.setOpenInModelEditor(this::openInModelEditor);
			importCommandContainer.getChildren().setAll(panel);
			importCommandController = ctrl;
		} catch (Exception e) {
			appendConsole("[Import] " + e.getMessage());
		}
	}

	@FXML
	private void onImportRun() {
		if (importCommandController != null)
			importCommandController.onRun();
	}
	// ── Ouverture dans les éditeurs ───────────────────────────────────────────

	private void openContextInEditor(IBinaryContext ctx) {
		if (contextEditorController != null) {
			mainTabPane.getSelectionModel().select(1);
			contextEditorController.loadContextFromFamily(ctx, modifiedCtx -> {
				Platform.runLater(() -> {
					RCAFamily currentFamily = familyEditorController.getFamily();
					rcfIntegrityService.synchronize(currentFamily, modifiedCtx.getName());
					familyEditorController.reloadFamily(currentFamily);
					familyEditorController.markModified();
					mainTabPane.getSelectionModel().select(2);
				});
			});
		}
	}

	public void openInEditor(Path filePath) {
	    if (contextEditorController != null) {
	        if (!contextEditorController.confirmDiscardChanges()) return; // ← ajouter
	        AppPreferences.addRecentContext(filePath.toString());
	        refreshRecentMenus();
	        contextEditorController.openFile(filePath);
	        mainTabPane.getSelectionModel().select(1);
	    }
	}
	public void openInFamilyEditor(Path filePath) {
	    if (familyEditorController != null) {
	        if (!familyEditorController.confirmDiscard()) return; // ← ajouter
	        AppPreferences.addRecentFamily(filePath.toString());
	        refreshRecentMenus();
	        familyEditorController.openFile(filePath);
	        mainTabPane.getSelectionModel().select(2);
	    }
	}
	private void openDotInGraph(Path dotFile) {
		mainTabPane.getSelectionModel().select(0);
		appendConsole(I18n.get("console.graphviz.render", dotFile));
		renderer.render(dotFile).thenRun(() -> Platform.runLater(() -> {
			hideOverlay();
			enableGraphButtons();
		})).exceptionally(ex -> {
			Platform.runLater(() -> {
				hideOverlay();
				appendConsole("[GraphViz] " + ex.getCause().getMessage());
			});
			return null;
		});
		showOverlayDelayed();
	}
	/**
	 * Retourne true si l'utilisateur accepte de perdre les modifications
	 * (ou s'il n'y en a pas). Interroge les trois éditeurs.
	 */
	public boolean confirmDiscardAll() {
	    if (contextEditorController != null
	            && !contextEditorController.confirmDiscardChanges())
	        return false;
	    if (familyEditorController != null
	            && !familyEditorController.confirmDiscard())
	        return false;
	    if (modelEditorController != null
	            && !modelEditorController.confirmDiscard())
	        return false;
	    return true;
	}
	private void onStopCommand() {
		runner.cancel();
		renderer.cancel(); // ← ajouter
		hideOverlay();
		appendConsole("\n" + I18n.get("console.cancelled"));
	}
	@FXML private void onToggleMagnifier() {
	    if (renderer.getCurrentDotFile() == null) return; // pas de graphe chargé
	    renderer.toggleMagnifier();
	    btnMagnifier.setStyle(renderer.isMagnifierActive()
	        ? "-fx-background-color: #e0e0e0;"
	        : "");
	}
	public void shutdown() {
		// Arrêter le timer overlay
		if (overlayTimer != null)
			overlayTimer.cancel(false);
		scheduler.shutdownNow();
		// Tuer les processus en cours
		runner.cancel();
		renderer.cancel();
		Platform.exit();
	}
	// ── Exécution des commandes ───────────────────────────────────────────────

	private void executeCommand(CommandBuilder builder) {
		if (!AppPreferences.isFca4jConfigured()) {
			showAlert(I18n.get("error.not.configured.title"), I18n.get("error.not.configured.detail"));
			return;
		}
		final var args = builder.build();
		consoleArea.clear();
		appendConsole("$ " + builder.toDisplayString());
		mainTabPane.getSelectionModel().select(0);
		clearGraph();
		showOverlayDelayed();

		// Limiter l'affichage console pour les commandes à sortie volumineuse
		final int MAX_CONSOLE_LINES = 500;
		final int[] lineCount = { 0 };
		final boolean[] truncated = { false };
		final long startTime = System.currentTimeMillis();
		final String commandName = args.get(0);

		runner.run(args, line -> Platform.runLater(() -> {
			if (truncated[0])
				return;
			if (lineCount[0]++ < MAX_CONSOLE_LINES) {
				appendConsole(line);
			} else {
				truncated[0] = true;
				appendConsole("... [" + I18n.get("console.output.truncated") + "]");
			}
		})).thenAccept(result -> Platform.runLater(() -> {
			hideOverlay();
			long duration = System.currentTimeMillis() - startTime;
			setLastCommandStatus(commandName, result.isSuccess(), duration);
			if (result.isSuccess()) {
				appendConsole("\n" + I18n.get("console.ok"));
				if (args.size() > 1 && !"BINARIZE".equals(args.get(0)) && !"FAMILY_IMPORT".equals(args.get(0))) {
					AppPreferences.addRecentContext(args.get(1));
					refreshRecentMenus();
				}
				tryRenderDot(builder);
			} else {
				appendConsole("\n" + I18n.get("console.error") + "\n" + result.stderr());
			}
		}));
	}

	private void clearGraph() {
		renderer.clear();
		btnSaveDot.setDisable(true);
		btnExportSvg.setDisable(true);
		btnExportPng.setDisable(true);
		btnExportPdf.setDisable(true);
	    btnMagnifier.setDisable(true); 
	    // Désactiver aussi la loupe si elle était active
	    if (renderer.isMagnifierActive()) renderer.toggleMagnifier();
	}

	private void executeRcaCommand(CommandBuilder builder) {
		if (!AppPreferences.isFca4jConfigured()) {
			showAlert(I18n.get("error.not.configured.title"), I18n.get("error.not.configured.detail"));
			return;
		}
		final var args = builder.build();
		consoleArea.clear();
		appendConsole("$ " + builder.toDisplayString());
		mainTabPane.getSelectionModel().select(0);
		final long startTime = System.currentTimeMillis();
		final String commandName = args.get(0);

		runner.run(args, line -> Platform.runLater(() -> appendConsole(line)))
				.thenAccept(result -> Platform.runLater(() -> {
					hideOverlay();
					long duration = System.currentTimeMillis() - startTime;
					setLastCommandStatus(commandName, result.isSuccess(), duration);
					if (result.isSuccess()) {
						appendConsole("\n" + I18n.get("console.ok"));
						if (rcaCommandController != null)
							rcaCommandController.scanDotFiles();
						// Enregistrer le fichier famille dans les récents
						if (args.size() > 1) {
							String inputPath = args.get(1);
							AppPreferences.addRecentFamily(inputPath);
							Platform.runLater(this::refreshRecentMenus);
						}

					} else {
						appendConsole("\n" + I18n.get("console.error") + "\n" + result.stderr());
					}
				}));
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
		renderer.render(dotFile).thenRun(() -> Platform.runLater(() -> {
			hideOverlay();
			enableGraphButtons();
		})).exceptionally(ex -> {
			Platform.runLater(() -> {
				hideOverlay();
				appendConsole("[GraphViz] " + ex.getCause().getMessage());
			});
			return null;
		});
		showOverlayDelayed();
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

		updateStatusBar();
	}

	@FXML private void onQuit() {
	    if (!confirmDiscardAll()) return;
	    shutdown();
	}
	
	@FXML
	private void onAbout() {
		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		alert.setTitle(I18n.get("menu.help.about"));
		alert.setHeaderText(MainApp.APP_TITLE + " " + MainApp.APP_VERSION);

		// Contenu personnalisé avec hyperliens
		javafx.scene.text.TextFlow content = new javafx.scene.text.TextFlow();
		content.setLineSpacing(4);
		content.setPrefWidth(380);

		java.util.List<javafx.scene.Node> nodes = new java.util.ArrayList<>();

		// Organisation
		nodes.add(text(MainApp.APP_ORG_NAME + "\n\n", true));

		// Développeurs
		nodes.add(text(I18n.get("about.developers") + " :\n", true));
		for (String dev : MainApp.APP_DEVELOPERS)
			nodes.add(text("  " + dev + "\n", false));

		nodes.add(text("\n"));

		// Licence / Depuis
		nodes.add(text(I18n.get("about.license") + " : " + MainApp.APP_LICENSE + "\n", false));
		nodes.add(text(I18n.get("about.since") + " : " + MainApp.APP_INCEPTION + "\n\n", false));

		// Site web
		nodes.add(text(I18n.get("about.website") + " : ", false));
		nodes.add(hyperlink(MainApp.APP_URL));
		nodes.add(text("\n", false));

		// Source
		nodes.add(text(I18n.get("about.source") + " : ", false));
		nodes.add(hyperlink(MainApp.APP_SCM));

		content.getChildren().addAll(nodes);

		// Icône
		try {
			InputStream logoStream = getClass().getResourceAsStream("/fr/lirmm/fca4j/ui/icons/fca4j-logo.png");
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

		alert.getDialogPane().setContent(content);
		alert.showAndWait();
	}

	@FXML
	private void onContextRun() {
		// Déléguer au contrôleur courant
		if (currentCommandController instanceof LatticeAocController c)
			c.onRun();
		else if (currentCommandController instanceof RuleBasisController c)
			c.onRun();
		else if (currentCommandController instanceof ReduceClarifyController c)
			c.onRun();
		else if (currentCommandController instanceof IrreducibleController c)
			c.onRun();
		else if (currentCommandController instanceof InspectController c)
			c.onRun();
		else if (currentCommandController instanceof BinarizeController c)
			c.onRun();
	}

	@FXML
	private void onRcaRun() {
		if (rcaCommandController != null)
			rcaCommandController.onRun();
	}

	@FXML
	private void onNewContext() {
		contextEditorController.onNewContext();
		mainTabPane.getSelectionModel().select(1);
	}

	@FXML
	private void onNewFamily() {
		familyEditorController.onNew();
		mainTabPane.getSelectionModel().select(2);
	}

	@FXML
	private void onNewModel() {
		modelEditorController.onNew();
		mainTabPane.getSelectionModel().select(3); 
	}

	@FXML
	private void onOpenModel() {
		modelEditorController.onOpen();
		mainTabPane.getSelectionModel().select(3);
	}

	public void openInModelEditor(Path path) {
	    if (modelEditorController != null) {
	        if (!modelEditorController.confirmDiscard()) return; // ← ajouter
	        AppPreferences.addRecentModel(path.toString());
	        refreshRecentMenus();
	        modelEditorController.openFile(path);
	        mainTabPane.getSelectionModel().select(3);
	    }
	}
	@FXML
	private void onOpenContext() {
		contextEditorController.onOpen();
		mainTabPane.getSelectionModel().select(1);
	}

	@FXML
	private void onOpenFamily() {
		familyEditorController.onOpen();
		mainTabPane.getSelectionModel().select(2);
	}

	private javafx.scene.text.Text text(String s) {
		return new javafx.scene.text.Text(s);
	}

	private javafx.scene.text.Text text(String s, boolean bold) {
		javafx.scene.text.Text t = new javafx.scene.text.Text(s);
		if (bold)
			t.setStyle("-fx-font-weight: bold;");
		return t;
	}

	private Hyperlink hyperlink(String url) {
		Hyperlink h = new Hyperlink(url);
		h.setOnAction(e -> {
			try {
				java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		});
		// Supprimer le padding par défaut du Hyperlink pour l'aligner avec le texte
		h.setPadding(new javafx.geometry.Insets(0));
		return h;
	}

	@FXML
	private void onClearConsole() {
		consoleArea.clear();
	}

	// ── Export Graph ──────────────────────────────────────────────────────────

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

	private void refreshRecentMenus() {
		buildRecentMenu(recentContextMenu, AppPreferences.getRecentContexts(), path -> openInEditor(Path.of(path)));
		buildRecentMenu(recentFamilyMenu, AppPreferences.getRecentFamilies(),
				path -> openInFamilyEditor(Path.of(path)));
		buildRecentMenu(recentModelMenu, AppPreferences.getRecentModels(), path -> openInModelEditor(Path.of(path)));
	}

	private void buildRecentMenu(javafx.scene.control.Menu menu, java.util.List<String> paths,
			Consumer<String> action) {
		menu.getItems().clear();
		if (paths.isEmpty()) {
			javafx.scene.control.MenuItem empty = new javafx.scene.control.MenuItem(I18n.get("menu.file.recent.empty"));
			empty.setDisable(true);
			menu.getItems().add(empty);
			return;
		}
		for (String path : paths) {
			java.io.File f = new java.io.File(path);
			String parentShort = shortenPath(f.getParent(), 40);
			String label = f.getName() + "   —   [" + parentShort + "]";
			javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(label);
			item.setDisable(!f.exists());
			item.setOnAction(e -> {
				action.accept(path);
				AppPreferences.setLastDirectory(f.getParent());
			});
			menu.getItems().add(item);
		}
	}

	private String shortenPath(String path, int maxLen) {
		if (path == null)
			return "";
		if (path.length() <= maxLen)
			return path;
		return "..." + path.substring(path.length() - maxLen);
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
}
