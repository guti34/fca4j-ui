package fr.lirmm.fca4j.ui.controller;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.RCAFamily;
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
import javafx.scene.control.Menu;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
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
	@FXML
	private Button btnHelp;
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
	@FXML
	private Label dotFileLabel;
	@FXML
	private Button btnOpenDot;
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
	private Tab contextEditorTab;
	@FXML
	private Tab conceptStructureTab;

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

	@FXML
	private Tab rulesViewerTab;
	@FXML
	private RulesViewerController rulesViewerController;

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

	private final BrowserLauncher browserLauncher = new BrowserLauncher(this::showAlert);

	private String lastGraphInputFile = ""; // input qui a produit le graphe courant
	private String lastRulesInputFile = ""; // input qui a produit les règles courantes

	private HelpDialogs helpDialogs;
	private RecentFilesManager recentFilesManager;
	private GraphExporter graphExporter;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		renderer = new GraphRenderer(graphWebView.getEngine());
		renderer.setOnNodeClick(this::onNodeSelected);
		renderer.setOnConsoleMessage(this::appendConsole);
		setTabGraphic(conceptStructureTab, Material2MZ.VISIBILITY, I18n.get("tab.graph"), "#3B6D11");
		commandCombo.getItems().addAll("LATTICE", "AOCPOSET", "RULEBASIS", "DBASIS", "CLARIFY", "REDUCE", "IRREDUCIBLE",
				"INSPECT");
		String lastCommand = AppPreferences.loadString("lastCommand", "LATTICE");
		if (!commandCombo.getItems().contains(lastCommand))
			lastCommand = "LATTICE";
		commandCombo.setValue(lastCommand);
		commandCombo.valueProperty().addListener((obs, old, val) -> {
			loadCommandPanel(val);
			AppPreferences.saveString("lastCommand", val);
		});

		selectedNodeLabel.setText(I18n.get("panel.node.none"));
		loadCommandPanel(lastCommand);
		contextRunButton.setText(I18n.get("button.run"));
		rcaRunButton.setText(I18n.get("button.run"));

		if (commandTabPane != null) {
			contextTab.setText(I18n.get("tab.context.commands"));
			rcaTab.setText(I18n.get("tab.rca.family"));
		}

		// ── Overlays — à construire AVANT tout appel à showOverlayDelayed ────────
		buildLoadingOverlay();

		// ── Menus récents ─────────────────────────────────────────────────────────
		recentFilesManager = new RecentFilesManager(recentContextMenu, recentFamilyMenu, recentModelMenu,
				entry -> openInEditor(entry),
				entry -> openInFamilyEditor(Path.of(AppPreferences.recentEntryPath(entry))),
				entry -> openInModelEditor(Path.of(AppPreferences.recentEntryPath(entry))));
		recentFilesManager.refresh();
		// ── Panneaux RCA et Import ────────────────────────────────────────────────
		loadRcaPanel();
		importTab.setText(I18n.get("tab.import.commands"));
		importRunButton.setText(I18n.get("button.run"));
		loadImportPanel();

		// ── Éditeur de famille ────────────────────────────────────────────────────
		if (familyEditorTab != null)
			setTabGraphic(familyEditorTab, Material2AL.EDIT, I18n.get("tab.family"), "#185FA5");
		if (familyEditorController != null) {
			familyEditorController.setOpenInContextEditor(this::openContextInEditor);
			familyEditorController.setOnFileOpened(path -> {
				AppPreferences.addRecentFamily(path.toString());
				recentFilesManager.refresh();
				selectCommandTabFor(path.toString());
			});
		}

		// ── Synchronisation famille → panneau RCA ─────────────────────────────────
		if (commandTabPane != null) {
			rcaTab.selectedProperty().addListener((obs, old, selected) -> {
				if (selected && rcaCommandController != null && familyEditorController != null
						&& familyEditorController.getCurrentFile() != null)
					rcaCommandController.setFamilyFile(familyEditorController.getCurrentFile());
			});
		}

		// ── Éditeur de contexte ───────────────────────────────────────────────────
		setTabGraphic(contextEditorTab, Material2AL.EDIT, I18n.get("tab.context"), "#185FA5");
		contextEditorController.setOnFileLoaded(path -> {
			onNewInputContext(path);
			propagateInputFile(path);
			AppPreferences.addRecentContext(path);
			recentFilesManager.refresh();
			selectCommandTabFor(path);
		});
		contextEditorController.setOnLoadCallbacks(this::showLoadingOverlay, this::hideLoadingOverlay);

		// ── Éditeur de modèle ─────────────────────────────────────────────────────
		if (modelEditorTab != null)
			setTabGraphic(modelEditorTab, Material2AL.EDIT, I18n.get("tab.model"), "#185FA5");
		if (modelEditorController != null) {
			modelEditorController.setOnFileOpened(path -> {
				AppPreferences.addRecentModel(path.toString());
				recentFilesManager.refresh();
				selectCommandTabFor(path.toString());
			});
		}
		// rule viewer
		setTabGraphic(rulesViewerTab, Material2MZ.VISIBILITY, I18n.get("tab.rules"), "#3B6D11");
		if (rulesViewerController != null) {
			rulesViewerController.setOpenInFcavizir(this::openInFcavizir);
		}
		// ── Toolbar graphe + status bar ───────────────────────────────────────────
		graphExporter = new GraphExporter(renderer, btnOpenDot, btnSaveDot, btnExportSvg, btnExportPng, btnExportPdf,
				btnMagnifier, dotFileLabel, this::appendConsole, () -> graphWebView.getScene().getWindow());
		graphExporter.setupToolbar();
		updateStatusBar();
		if (btnHelp != null) {
			FontIcon helpIcon = new FontIcon(Material2AL.HELP_OUTLINE);
			helpIcon.setIconSize(16);
			helpIcon.setIconColor(Color.valueOf("#0047B3"));
			btnHelp.setGraphic(helpIcon);
			btnHelp.setText("");
			btnHelp.setTooltip(new Tooltip(I18n.get("button.help.command")));
		}
		// ── Raccourcis clavier globaux ────────────────────────────────────────────
		// Branchés sur la Scene après que le layout soit stable
		javafx.application.Platform.runLater(() -> {
			setupKeyboardShortcuts();
		});
	}

	// ── Raccourcis clavier ────────────────────────────────────────────────────

	private void setupKeyboardShortcuts() {
		javafx.scene.Scene scene = mainTabPane.getScene();
		if (scene == null)
			return;
		// EventFilter : capture les touches AVANT les controles focuses
		// (getAccelerators ne fonctionne pas quand Canvas ou TextField a le focus)
		scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
			javafx.scene.input.KeyCode code = event.getCode();
			boolean ctrl = event.isControlDown();
			if (event.isShiftDown() || event.isAltDown())
				return;

			if (ctrl && code == javafx.scene.input.KeyCode.Z) {
				if (contextEditorController != null)
					contextEditorController.undo();
				event.consume();
				return;
			}
			if (ctrl && code == javafx.scene.input.KeyCode.S) {
				if (mainTabPane.getSelectionModel().getSelectedItem() == contextEditorTab
						&& contextEditorController != null && !contextEditorController.isFromFamily())
					contextEditorController.onSave();
				event.consume();
				return;
			}
			if (ctrl && code == javafx.scene.input.KeyCode.O) {
			    if (contextEditorController != null) {
			        contextEditorController.onOpen();
			        mainTabPane.getSelectionModel().select(contextEditorTab);
			    }
			    event.consume(); return;
			}
			if (ctrl && code == javafx.scene.input.KeyCode.L) {
				onClearConsole();
				event.consume();
				return;
			}
			if (!ctrl && code == javafx.scene.input.KeyCode.F5) {
				Tab left = commandTabPane.getSelectionModel().getSelectedItem();
				if (left == contextTab)
					onContextRun();
				else if (left == rcaTab)
					onRcaRun();
				else if (left == importTab)
					onImportRun();
				event.consume();
				return;
			}
			if (!ctrl && code == javafx.scene.input.KeyCode.F1) {
				onShowShortcuts();
				event.consume();
			}
			if (!ctrl && code == javafx.scene.input.KeyCode.F2) {
				onCommandHelp();
				event.consume();
			}
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

	public void openInRcaviz(Path jsonFile) {
		browserLauncher.openInRcaviz(jsonFile);
	}

	public void openInFcavizir(Path txtFile) {
		browserLauncher.openInFcavizir(txtFile);
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
		System.out.println("[DEBUG] hasEmbeddedJar = " + Fca4jRunner.hasEmbeddedJar());
		System.out.println("[DEBUG] isUseExternal = " + AppPreferences.isUseExternalFca4j());
		System.out.println("[DEBUG] resource URL = " + Fca4jRunner.class.getResource("/fr/lirmm/fca4j/ui/bin/fca4j.jar"));		// FCA4J jar
		if (Fca4jRunner.hasEmbeddedJar() && !AppPreferences.isUseExternalFca4j()) {
		    statusFca4jLabel.setText(I18n.get("status.embedded"));
		    statusFca4jLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #2a7a2a;");
		} else if (AppPreferences.isUseExternalFca4j() && AppPreferences.getFca4jJarPath() != null
		        && !AppPreferences.getFca4jJarPath().isBlank()) {
		    String jarName = Path.of(AppPreferences.getFca4jJarPath()).getFileName().toString();
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
		    statusGraphvizLabel.setCursor(javafx.scene.Cursor.DEFAULT);
		    statusGraphvizLabel.setOnMouseClicked(null);
		    statusGraphvizLabel.setTooltip(null);
		} else {
		    statusGraphvizLabel.setText(I18n.get("status.graphviz.install"));
		    statusGraphvizLabel.setStyle(
		        "-fx-font-size: 11px; -fx-text-fill: #0047B3; -fx-underline: true; -fx-cursor: hand;");
		    statusGraphvizLabel.setCursor(javafx.scene.Cursor.HAND);
		    statusGraphvizLabel.setTooltip(new Tooltip(I18n.get("status.graphviz.install.tooltip")));
		    statusGraphvizLabel.setOnMouseClicked(e ->
		        browserLauncher.openUrlWithFallback("https://graphviz.org/download/"));
		}
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
				ctrl.configure(desc, this::executeCommand, this::openInEditor, onInputFileChanged());
				ctrl.setInputFile(lastInputFile);
				currentCommandController = ctrl;
			}
			case RULE_BASIS -> {
				RuleBasisController ctrl = loader.getController();
				ctrl.configure(desc, this::executeCommand, this::openInEditor, onInputFileChanged());
				ctrl.setInputFile(lastInputFile);
				currentCommandController = ctrl;
			}
			case REDUCE_CLARIFY -> {
				ReduceClarifyController ctrl = loader.getController();
				ctrl.configure(desc, this::executeCommand, this::openInEditor, onInputFileChanged());
				ctrl.setInputFile(lastInputFile);
				currentCommandController = ctrl;
			}
			case IRREDUCIBLE -> {
				IrreducibleController ctrl = loader.getController();
				ctrl.configure(desc, this::executeCommand, this::openInEditor, onInputFileChanged());
				ctrl.setInputFile(lastInputFile);
				currentCommandController = ctrl;
			}
			case INSPECT -> {
				InspectController ctrl = loader.getController();
				ctrl.configure(desc, this::executeCommand, this::openInEditor, onInputFileChanged());
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

	private Consumer<String> onInputFileChanged() {
		return path -> {
			onNewInputContext(path);
			if (contextEditorController != null && contextEditorController.confirmDiscardChanges()) {
				contextEditorController.openFile(Path.of(path));
				mainTabPane.getSelectionModel().select(contextEditorTab);
			}
			AppPreferences.addRecentContext(path, null);
			recentFilesManager.refresh();
			selectCommandTabFor(path);
			propagateInputFile(path);
		};
	}

	private void onNewInputContext(String path) {
		if (path == null || path.equals(lastInputFile))
			return;
		lastInputFile = path;
		lastGraphInputFile = "";
		lastRulesInputFile = "";
		graphExporter.clearGraph();
		if (rulesViewerController != null)
			rulesViewerController.clearRules();
	}

	private void setTabGraphic(Tab tab, Ikon icon, String text, String colorHex) {
		FontIcon fi = new FontIcon(icon);
		fi.setIconSize(14);
		fi.setIconColor(Color.valueOf(colorHex));
		Label lbl = new Label(text);
		lbl.setStyle("-fx-font-size: 12px;");
		HBox box = new HBox(5, fi, lbl);
		box.setAlignment(javafx.geometry.Pos.CENTER);
		tab.setGraphic(box);
		tab.setText("");
	}
	// ── Chargement du panneau RCA ─────────────────────────────────────────────

	private void loadRcaPanel() {
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/fr/lirmm/fca4j/ui/fxml/rca_command.fxml"),
					I18n.getBundle());
			Node panel = loader.load();
			rcaCommandController = loader.getController();
			rcaCommandController.configure(this::executeRcaCommand, this::openInFamilyEditor, this::openDotInGraph,
					this::openInRcaviz, path -> {
						AppPreferences.addRecentFamily(path.toString());
						recentFilesManager.refresh();
						// Charger dans l'éditeur famille
						if (familyEditorController != null && familyEditorController.confirmDiscard()) {
							familyEditorController.openFile(path);
							mainTabPane.getSelectionModel().select(familyEditorTab);
						}
						selectCommandTabFor(path.toString());
					});
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

	@FXML
	private void onAbout() {
		getHelpDialogs().showAbout();
	}
	// ── Ouverture dans les éditeurs ───────────────────────────────────────────

	@FXML
	private void onOpenDot() {
		graphExporter.openDot(() -> {
			showOverlayDelayed();
			mainTabPane.getSelectionModel().select(conceptStructureTab);
		}, this::hideOverlay);
	}

	@FXML
	private void onSaveDot() {
		graphExporter.saveDot();
	}

	@FXML
	private void onExportSvg() {
		graphExporter.exportSvg();
	}

	@FXML
	private void onExportPng() {
		graphExporter.exportPng();
	}

	@FXML
	private void onExportPdf() {
		graphExporter.exportPdf();
	}

	@FXML
	private void onToggleMagnifier() {
		graphExporter.toggleMagnifier();
	}

	private void openContextInEditor(IBinaryContext ctx) {
		if (contextEditorController != null) {
			mainTabPane.getSelectionModel().select(2);
			contextEditorController.loadContextFromFamily(ctx, modifiedCtx -> {
				Platform.runLater(() -> {
					RCAFamily currentFamily = familyEditorController.getFamily();
					// Remplacer le contexte dans la famille par la version éditée
					RCAFamily.FormalContext fc = currentFamily.getFormalContext(modifiedCtx.getName());
					if (fc != null)
						fc.setContext(modifiedCtx);
					rcfIntegrityService.synchronize(currentFamily, modifiedCtx.getName());
					familyEditorController.reloadFamily(currentFamily);
					familyEditorController.markModified();
					mainTabPane.getSelectionModel().select(3);
				});
			});
		}
	}

	public void openInEditor(Path filePath) {
		openInEditor(filePath, "COMMA");
	}

	public void openInEditor(String entry) {
		String path = AppPreferences.recentEntryPath(entry);
		String sep = AppPreferences.recentEntrySeparator(entry);
		openInEditor(Path.of(path), sep);
	}

	private void openInEditor(Path filePath, String separator) {
		onNewInputContext(filePath.toString());
		if (contextEditorController != null) {
			if (!contextEditorController.confirmDiscardChanges())
				return;
			AppPreferences.addRecentContext(filePath.toString(), separator);
			recentFilesManager.refresh();
			contextEditorController.openFile(filePath, separator);
			mainTabPane.getSelectionModel().select(2);
			selectCommandTabFor(filePath.toString()); // ← ajouter
		}
	}

	public void openInFamilyEditor(Path filePath) {
		if (familyEditorController != null) {
			if (!familyEditorController.confirmDiscard())
				return;
			AppPreferences.addRecentFamily(filePath.toString());
			recentFilesManager.refresh();
			familyEditorController.openFile(filePath);
			mainTabPane.getSelectionModel().select(3);
			selectCommandTabFor(filePath.toString()); // ← ajouter
		}
	}

	private void openDotInGraph(Path dotFile) {
	    mainTabPane.getSelectionModel().select(0);
	    graphExporter.setDotFileLabel(dotFile.getFileName().toString());
	    appendConsole(I18n.get("console.graphviz.render", dotFile));
	    renderer.render(dotFile).thenRun(() -> Platform.runLater(() -> {
	        hideOverlay();
	        graphExporter.enableButtons();
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
	 * Retourne true si l'utilisateur accepte de perdre les modifications (ou s'il
	 * n'y en a pas). Interroge les trois éditeurs.
	 */
	public boolean confirmDiscardAll() {
		if (contextEditorController != null && !contextEditorController.confirmDiscardChanges())
			return false;
		if (familyEditorController != null && !familyEditorController.confirmDiscard())
			return false;
		if (modelEditorController != null && !modelEditorController.confirmDiscard())
			return false;
		return true;
	}

	private void onStopCommand() {
		runner.cancel();
		renderer.cancel(); // ← ajouter
		hideOverlay();
		appendConsole("\n" + I18n.get("console.cancelled"));
	}

	public void shutdown() {
		browserLauncher.stopServers();
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
		final String commandName = args.get(0);
		final String inputFile = args.size() > 1 ? args.get(1) : "";
		final boolean isRules = "RULEBASIS".equals(commandName) || "DBASIS".equals(commandName);
		final boolean isGraph = !isRules && !"BINARIZE".equals(commandName) && !"FAMILY_IMPORT".equals(commandName);

		// ── Console ───────────────────────────────────────────────────────────────
		consoleArea.clear();
		appendConsole("$ " + builder.toDisplayString());

		// ── Basculer sur le bon viewer + effacer seulement si nouvel input ────────
		if (isRules) {
			if (!inputFile.equals(lastRulesInputFile)) {
				rulesViewerController.clearRules();
			}
			mainTabPane.getSelectionModel().select(rulesViewerTab);
		} else if (isGraph) {
			if (!inputFile.equals(lastGraphInputFile)) {
				graphExporter.clearGraph();
			}
			mainTabPane.getSelectionModel().select(conceptStructureTab);
		}

		showOverlayDelayed();

		// ── Limiter l'affichage console pour les commandes à sortie volumineuse ───
		final int MAX_CONSOLE_LINES = 500;
		final int[] lineCount = { 0 };
		final boolean[] truncated = { false };
		final long startTime = System.currentTimeMillis();

		runner.run(args, line -> Platform.runLater(() -> {
			// Console graphe — toujours alimentée
			if (!truncated[0]) {
				if (lineCount[0]++ < MAX_CONSOLE_LINES) {
					appendConsole(line);
				} else {
					truncated[0] = true;
					appendConsole("... [" + I18n.get("console.output.truncated") + "]");
				}
			}
			// Console rules — aussi alimentée si commande rules

		})).thenAccept(result -> Platform.runLater(() -> {
			hideOverlay();
			long duration = System.currentTimeMillis() - startTime;
			setLastCommandStatus(commandName, result.isSuccess(), duration);

			if (result.isSuccess()) {
				appendConsole("\n" + I18n.get("console.ok"));

				// Mémoriser l'input qui a produit ce résultat
				if (isRules) {
					lastRulesInputFile = inputFile;
					tryOpenRules(builder);
				} else if (isGraph) {
					lastGraphInputFile = inputFile;
					tryRenderDot(builder);
				}

				// Mettre à jour les récents
				if (args.size() > 1 && !"BINARIZE".equals(commandName) && !"FAMILY_IMPORT".equals(commandName)) {
					String sep = null;
					int sIdx = args.indexOf("-s");
					if (sIdx >= 0 && sIdx + 1 < args.size())
						sep = args.get(sIdx + 1);
					AppPreferences.addRecentContext(args.get(1), sep);
					recentFilesManager.refresh();
				}

			} else {
				appendConsole("\n" + I18n.get("console.error") + "\n" + result.stderr());
			}
		}));
	}

	private void executeRcaCommand(CommandBuilder builder) {
		if (!AppPreferences.isFca4jConfigured()) {
			showAlert(I18n.get("error.not.configured.title"), I18n.get("error.not.configured.detail"));
			return;
		}
		final var args = builder.build();
		consoleArea.clear();
		appendConsole("$ " + builder.toDisplayString());
		showOverlayDelayed();
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
							Platform.runLater(recentFilesManager::refresh);
						}

					} else {
						appendConsole("\n" + I18n.get("console.error") + "\n" + result.stderr());
					}
				}));
	}

	private void tryRenderDot(CommandBuilder builder) {
	    var args = builder.build();
	    int gIdx = args.indexOf("-g");
	    if (gIdx == -1 || gIdx + 1 >= args.size()) return;

	    Path dotFile = Path.of(args.get(gIdx + 1));
	    if (!dotFile.toFile().exists()) {
	        appendConsole(I18n.get("error.graphviz.not.found", dotFile));
	        return;
	    }
	    graphExporter.setDotFileLabel(dotFile.getFileName().toString());
	    appendConsole(I18n.get("console.graphviz.render", dotFile));
	    renderer.render(dotFile).thenRun(() -> Platform.runLater(() -> {
	        hideOverlay();
	        graphExporter.enableButtons();
	    })).exceptionally(ex -> {
	        Platform.runLater(() -> {
	            hideOverlay();
	            appendConsole("[GraphViz] " + ex.getCause().getMessage());
	        });
	        return null;
	    });
	    showOverlayDelayed();
	}
	
	private void tryOpenRules(CommandBuilder builder) {
		if (!"RULEBASIS".equals(builder.getCommand()) && !"DBASIS".equals(builder.getCommand()))
			return;

		// Cas 1 : fichier unique
		String outputFile = builder.getOutputFile();
		if (outputFile != null && !outputFile.isBlank()) {
			Path p = Path.of(outputFile);
			if (p.toFile().exists()) {
				rulesViewerController.loadFile(p);
				mainTabPane.getSelectionModel().select(rulesViewerTab);
				return;
			}
		}

		// Cas 2 : implFolder (fichiers par support)
		// → déléguer à RuleBasisController comme les .dot pour RCA
		// (la ListView de résultats déclenchera loadFile() sur sélection)
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

	@FXML
	private void onQuit() {
		if (!confirmDiscardAll())
			return;
		shutdown();
	}

	@FXML
	public void onShowShortcuts() {
		getHelpDialogs().showShortcuts();
	}

	private HelpDialogs getHelpDialogs() {
		if (helpDialogs == null) {
			helpDialogs = new HelpDialogs(mainTabPane.getScene().getWindow(), browserLauncher::openUrlWithFallback);
		}
		return helpDialogs;
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
		onNewInputContext("");
		contextEditorController.onNewContext();
		mainTabPane.getSelectionModel().select(2);
	}

	@FXML
	private void onNewFamily() {
		familyEditorController.onNew();
		mainTabPane.getSelectionModel().select(3);
	}

	@FXML
	private void onNewModel() {
		modelEditorController.onNew();
		mainTabPane.getSelectionModel().select(4);
	}

	@FXML
	private void onOpenModel() {
		modelEditorController.onOpen();
		mainTabPane.getSelectionModel().select(4);
	}

	public void openInModelEditor(Path path) {
		if (modelEditorController != null) {
			if (!modelEditorController.confirmDiscard())
				return;
			AppPreferences.addRecentModel(path.toString());
			recentFilesManager.refresh();
			modelEditorController.openFile(path);
			mainTabPane.getSelectionModel().select(4);
			selectCommandTabFor(path.toString());
		}
	}

	@FXML
	private void onOpenContext() {
		contextEditorController.onOpen();
		mainTabPane.getSelectionModel().select(2);
	}

	@FXML
	private void onOpenFamily() {
		familyEditorController.onOpen();
		mainTabPane.getSelectionModel().select(3);
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

	/** Sélectionne le bon onglet de commande selon le type de fichier ouvert. */
	private void selectCommandTabFor(String filePath) {
		if (filePath == null || commandTabPane == null)
			return;
		String lower = filePath.toLowerCase();
		if (lower.endsWith(".rcft") || lower.endsWith(".rcfgz") || lower.endsWith(".rcfal")) {
			commandTabPane.getSelectionModel().select(rcaTab);
			if (rcaCommandController != null)
				rcaCommandController.setFamilyFile(Path.of(filePath));
		} else if (lower.endsWith(".json")) {
			commandTabPane.getSelectionModel().select(importTab);
			// Sélectionner FAMILY_IMPORT et pré-remplir le fichier modèle
			if (importCommandController != null)
				importCommandController.selectFamilyImportAndSetInput(filePath);
		} else {
			commandTabPane.getSelectionModel().select(contextTab);
		}
	}

	// Méthode pour résoudre la commande courante selon l'onglet actif
	private String getCurrentCommandName() {
		Tab selected = commandTabPane.getSelectionModel().getSelectedItem();
		if (selected == rcaTab) {
			return "RCA";
		} else if (selected == importTab) {
			return (importCommandController != null) ? importCommandController.getCurrentCommand() : "BINARIZE";
		} else {
			// Onglet Context : valeur de la ComboBox
			return commandCombo.getValue();
		}
	}

	@FXML
	private void onCommandHelp() {
		String cmd = getCurrentCommandName();
		String pageName = toPageName(cmd);
		browserLauncher.openUrlWithFallback("https://www.lirmm.fr/fca4j/" + pageName + ".html");
	}

	@FXML
	private void onOpenWebsite() {
		browserLauncher.openUrlWithFallback("https://www.lirmm.fr/fca4j/");
	}

	private static String toPageName(String command) {
		if (command.contains("_")) {
			String[] parts = command.toLowerCase().split("_");
			parts[0] = Character.toUpperCase(parts[0].charAt(0)) + parts[0].substring(1);
			return String.join("_", parts);
		}
		return Character.toUpperCase(command.charAt(0)) + command.substring(1).toLowerCase();
	}
}