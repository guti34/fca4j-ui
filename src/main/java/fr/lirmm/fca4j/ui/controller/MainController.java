package fr.lirmm.fca4j.ui.controller;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.kordamp.ikonli.Ikon;
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
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

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
	// ── Serveur HTTP local pour RCAViz ────────────────────────────────────────
	private com.sun.net.httpserver.HttpServer rcavizServer;
	private int rcavizPort = 0;
	private com.sun.net.httpserver.HttpServer fcavizirServer;
	private int fcavizirPort = 0;
	
	private String lastGraphInputFile  = ""; // input qui a produit le graphe courant
	private String lastRulesInputFile  = ""; // input qui a produit les règles courantes
	@Override
	public void initialize(URL location, ResourceBundle resources) {
		renderer = new GraphRenderer(graphWebView.getEngine());
		renderer.setOnNodeClick(this::onNodeSelected);
		renderer.setOnConsoleMessage(this::appendConsole); 
		setTabGraphic(conceptStructureTab, Material2MZ.VISIBILITY, I18n.get("tab.graph"), "#3B6D11");
		commandCombo.getItems().addAll("LATTICE", "AOCPOSET", "RULEBASIS", "DBASIS", "CLARIFY", "REDUCE", "IRREDUCIBLE",
				"INSPECT");
		commandCombo.setValue("LATTICE");
		commandCombo.valueProperty().addListener((obs, old, val) -> loadCommandPanel(val));

		selectedNodeLabel.setText(I18n.get("panel.node.none"));
		loadCommandPanel("LATTICE");
		contextRunButton.setText(I18n.get("button.run"));
		rcaRunButton.setText(I18n.get("button.run"));

		if (commandTabPane != null) {
			contextTab.setText(I18n.get("tab.context.commands"));
			rcaTab.setText(I18n.get("tab.rca.family"));
		}

		// ── Overlays — à construire AVANT tout appel à showOverlayDelayed ────────
		buildLoadingOverlay();

		// ── Menus récents ─────────────────────────────────────────────────────────
		refreshRecentMenus();

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
				refreshRecentMenus();
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
		    String sep = getSeparatorFromCurrentPanel();
		    AppPreferences.addRecentContext(path, sep);
		    refreshRecentMenus();
		    selectCommandTabFor(path);
		});
		contextEditorController.setOnLoadCallbacks(this::showLoadingOverlay, this::hideLoadingOverlay);

		// ── Éditeur de modèle ─────────────────────────────────────────────────────
		if (modelEditorTab != null)
			setTabGraphic(modelEditorTab, Material2AL.EDIT, I18n.get("tab.model"), "#185FA5");
		if (modelEditorController != null) {
			modelEditorController.setOnFileOpened(path -> {
				AppPreferences.addRecentModel(path.toString());
				refreshRecentMenus();
				selectCommandTabFor(path.toString());
			});
		}
		// rule viewer
		setTabGraphic(rulesViewerTab, Material2MZ.VISIBILITY, I18n.get("tab.rules"), "#3B6D11");
		if (rulesViewerController != null) {
		    rulesViewerController.setOpenInFcavizir(this::openInFcavizir);
		}		
		// ── Toolbar graphe + status bar ───────────────────────────────────────────
		setupGraphToolbar();
		updateStatusBar();

		// ── Raccourcis clavier globaux ────────────────────────────────────────────
		// Branchés sur la Scene après que le layout soit stable
		javafx.application.Platform.runLater(this::setupKeyboardShortcuts);
	}

	// ── Raccourcis clavier ────────────────────────────────────────────────────

	private void setupKeyboardShortcuts() {
	    javafx.scene.Scene scene = mainTabPane.getScene();
	    if (scene == null) return;
	    // EventFilter : capture les touches AVANT les controles focuses
	    // (getAccelerators ne fonctionne pas quand Canvas ou TextField a le focus)
	    scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
	        javafx.scene.input.KeyCode code = event.getCode();
	        boolean ctrl = event.isControlDown();
	        if (event.isShiftDown() || event.isAltDown()) return;

	        if (ctrl && code == javafx.scene.input.KeyCode.Z) {
	            if (contextEditorController != null) contextEditorController.undo();
	            event.consume(); return;
	        }
	        if (ctrl && code == javafx.scene.input.KeyCode.S) {
	            if (mainTabPane.getSelectionModel().getSelectedItem() == contextEditorTab
	                    && contextEditorController != null
	                    && !contextEditorController.isFromFamily())
	                contextEditorController.onSave();
	            event.consume(); return;
	        }
	        if (ctrl && code == javafx.scene.input.KeyCode.O) {
	            if (mainTabPane.getSelectionModel().getSelectedItem() == contextEditorTab
	                    && contextEditorController != null)
	                contextEditorController.onOpen();
	            event.consume(); return;
	        }
	        if (ctrl && code == javafx.scene.input.KeyCode.L) {
	            onClearConsole(); event.consume(); return;
	        }
	        if (!ctrl && code == javafx.scene.input.KeyCode.F5) {
	            Tab left = commandTabPane.getSelectionModel().getSelectedItem();
	            if      (left == contextTab)  onContextRun();
	            else if (left == rcaTab)      onRcaRun();
	            else if (left == importTab)   onImportRun();
	            event.consume(); return;
	        }
	        if (!ctrl && code == javafx.scene.input.KeyCode.F1) {
	            onShowShortcuts(); event.consume();
	        }
	    });
	}

	@FXML
	public void onShowShortcuts() {
	    javafx.stage.Stage dialog = new javafx.stage.Stage();
	    dialog.setTitle(I18n.get("menu.help.shortcuts"));
	    dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
	    dialog.initOwner(mainTabPane.getScene().getWindow());

	    javafx.scene.web.WebView wv = new javafx.scene.web.WebView();
	    wv.getEngine().loadContent(buildShortcutsHtml());
	    wv.setPrefSize(520, 480);

	    dialog.setScene(new javafx.scene.Scene(wv));
	    dialog.setResizable(false);
	    dialog.show();
	}

	private String buildShortcutsHtml() {
	    boolean isFr = "fr".equals(I18n.getLocale().getLanguage());
	    String title   = isFr ? "Raccourcis clavier — FCA4J UI"        : "Keyboard Shortcuts — FCA4J UI";
	    String secGen  = isFr ? "Général"                               : "General";
	    String secEdit = isFr ? "Éditeur de contexte"                   : "Context Editor";
	    return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
	        + "body{font-family:system-ui,sans-serif;font-size:13px;padding:16px;background:#f8f9fa;}"
	        + "h1{font-size:15px;color:#0047B3;margin:0 0 14px}"
	        + "h2{font-size:12px;color:#555;text-transform:uppercase;letter-spacing:.04em;"
	        +   "margin:14px 0 6px;border-bottom:1px solid #dee2e6;padding-bottom:4px}"
	        + "table{width:100%;border-collapse:collapse}"
	        + "td{padding:5px 8px;border-bottom:1px solid #eee;vertical-align:top}"
	        + "td:first-child{width:160px}"
	        + "kbd{background:#e9ecef;border:1px solid #ced4da;border-radius:4px;"
	        +   "padding:2px 7px;font-size:11px;font-family:monospace;white-space:nowrap}"
	        + "</style></head><body>"
	        + "<h1>" + title + "</h1>"
	        + "<h2>" + secGen + "</h2>"
	        + "<table>"
	        + row("Ctrl+O",  isFr ? "Ouvrir un contexte"        : "Open context")
	        + row("Ctrl+S",  isFr ? "Enregistrer"               : "Save")
	        + row("F5",      isFr ? "Relancer la commande"       : "Re-run command")
	        + row("Ctrl+L",  isFr ? "Effacer la console"         : "Clear console")
	        + row("F1",      isFr ? "Afficher cette aide"        : "Show this help")
	        + "</table>"
	        + "<h2>" + secEdit + "</h2>"
	        + "<table>"
	        + row("Ctrl+Z",       isFr ? "Annuler (undo)"                  : "Undo")
	        + row("Double-clic",  isFr ? "Renommer objet / attribut"        : "Rename object / attribute")
	        + row("Clic cellule", isFr ? "Cocher / décocher"                : "Toggle cell")
	        + "</table>"
	        + "</body></html>";
	}

	private static String row(String key, String desc) {
	    return "<tr><td><kbd>" + key + "</kbd></td><td>" + desc + "</td></tr>";
	}


	private void startRcavizServer(Path jsonFile) throws Exception {
		// Arrêter le serveur précédent si actif
		if (rcavizServer != null)
			rcavizServer.stop(0);

		rcavizServer = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(0), 0); // port 0 = port
																										// libre
																										// automatique
		rcavizPort = rcavizServer.getAddress().getPort();

		rcavizServer.createContext("/", exchange -> {
			// Headers CORS pour autoriser rcaviz.lirmm.fr à lire le fichier
			exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			byte[] bytes = java.nio.file.Files.readAllBytes(jsonFile);
			exchange.sendResponseHeaders(200, bytes.length);
			try (var os = exchange.getResponseBody()) {
				os.write(bytes);
			}
		});
		rcavizServer.setExecutor(null);
		rcavizServer.start();
	}

	public void openInRcaviz(Path jsonFile) {
		try {
			startRcavizServer(jsonFile);
			String url = "https://rcaviz.lirmm.fr/?data=http://localhost:" + rcavizPort + "/" + jsonFile.getFileName();
			java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
		} catch (Exception e) {
			showAlert("RCAViz", "Impossible d'ouvrir RCAViz : " + e.getMessage());
		}
	}
	public void openInFcavizir(Path txtFile) {
	    try {
	        // Meme mecanisme que RCAViz : serveur HTTP local + ?data=
	        // Le serveur Java envoie le fichier avec les bons headers CORS
	        if (fcavizirServer != null) fcavizirServer.stop(0);
	        fcavizirServer = com.sun.net.httpserver.HttpServer.create(
	            new java.net.InetSocketAddress(0), 0);
	        fcavizirPort = fcavizirServer.getAddress().getPort();
	        fcavizirServer.createContext("/", exchange -> {
	            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
	            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
	            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
	            if ("OPTIONS".equals(exchange.getRequestMethod())) {
	                exchange.sendResponseHeaders(204, -1);
	                return;
	            }
	            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
	            byte[] bytes = java.nio.file.Files.readAllBytes(txtFile);
	            exchange.sendResponseHeaders(200, bytes.length);
	            try (var os = exchange.getResponseBody()) { os.write(bytes); }
	        });
	        fcavizirServer.setExecutor(null);
	        fcavizirServer.start();

	        String url = "https://fcavizir.lirmm.fr/?data=http://localhost:"
	            + fcavizirPort + "/" + txtFile.getFileName();
	        openUrlInBrowser(url);
	        if (!openUrlInBrowser(url)) {
	            java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
	        }
	    } catch (Exception e) {
	        showAlert("FCAvizIR", "Impossible d'ouvrir FCAvizIR : " + e.getMessage());
	    }
	}

	/**
	 * Lance le navigateur directement via ProcessBuilder pour contourner la limite
	 * de 2048 chars de ShellExecuteW (Desktop.browse sur Windows).
	 * Tente Chrome, Firefox, Edge dans cet ordre.
	 * @return true si un navigateur a été lancé, false sinon
	 */
	private boolean openUrlInBrowser(String url) {
	    String os = System.getProperty("os.name", "").toLowerCase();
	    java.util.List<String[]> candidates = new java.util.ArrayList<>();

	    if (os.contains("win")) {
	        String local = System.getenv("LOCALAPPDATA");
	        candidates.add(new String[]{"C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe", url});
	        candidates.add(new String[]{"C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe", url});
	        if (local != null)
	            candidates.add(new String[]{local + "\\Google\\Chrome\\Application\\chrome.exe", url});
	        candidates.add(new String[]{"C:\\Program Files\\Mozilla Firefox\\firefox.exe", url});
	        candidates.add(new String[]{"C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe", url});
	    } else if (os.contains("mac")) {
	        candidates.add(new String[]{"open", "-a", "Google Chrome", url});
	        candidates.add(new String[]{"open", url});
	    } else {
	        candidates.add(new String[]{"xdg-open", url});
	        candidates.add(new String[]{"google-chrome", url});
	        candidates.add(new String[]{"firefox", url});
	    }

	    for (String[] cmd : candidates) {
	        try {
	            java.io.File exe = new java.io.File(cmd[0]);
	            if (cmd.length == 2 && !exe.exists()) continue; // chemin absolu non trouvé
	            new ProcessBuilder(cmd)
	                .redirectErrorStream(true)
	                .start();
	            return true;
	        } catch (Exception ignored) {}
	    }
	    return false;
	}
	private String getSeparatorFromCurrentPanel() {
		if (currentCommandController instanceof LatticeAocController c)
			return c.getSeparator();
		if (currentCommandController instanceof RuleBasisController c)
			return c.getSeparator();
		if (currentCommandController instanceof ReduceClarifyController c)
			return c.getSeparator();
		if (currentCommandController instanceof IrreducibleController c)
			return c.getSeparator();
		if (currentCommandController instanceof InspectController c)
			return c.getSeparator();
		if (currentCommandController instanceof BinarizeController c)
			return c.getSeparator();
		return null;
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
		setGraphToolbarBtn(btnOpenDot,   new FontIcon(Material2AL.FOLDER_OPEN), I18n.get("graph.btn.open.dot"));
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
				ctrl.configure(desc, this::executeCommand, this::openInEditor,
				            onInputFileChanged()); 				
				ctrl.setInputFile(lastInputFile);
				currentCommandController = ctrl;
			}
			case RULE_BASIS -> {
				RuleBasisController ctrl = loader.getController();
				ctrl.configure(desc, this::executeCommand, this::openInEditor,
			            onInputFileChanged()); 				
				ctrl.setInputFile(lastInputFile);
				currentCommandController = ctrl;
			}
			case REDUCE_CLARIFY -> {
				ReduceClarifyController ctrl = loader.getController();
				ctrl.configure(desc, this::executeCommand, this::openInEditor,
			            onInputFileChanged()); 				
				ctrl.setInputFile(lastInputFile);
				currentCommandController = ctrl;
			}
			case IRREDUCIBLE -> {
				IrreducibleController ctrl = loader.getController();
				ctrl.configure(desc, this::executeCommand, this::openInEditor,
			            onInputFileChanged()); 				
				ctrl.setInputFile(lastInputFile);
				currentCommandController = ctrl;
			}
			case INSPECT -> {
				InspectController ctrl = loader.getController();
				ctrl.configure(desc, this::executeCommand, this::openInEditor,
			            onInputFileChanged()); 				
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
	        if (contextEditorController != null
	                && contextEditorController.confirmDiscardChanges()) {
	            contextEditorController.openFile(Path.of(path));
	            mainTabPane.getSelectionModel().select(contextEditorTab);
	        }
	        AppPreferences.addRecentContext(path, null);
	        refreshRecentMenus();
	        selectCommandTabFor(path);
	        propagateInputFile(path);
	    };	
	    }
	private void onNewInputContext(String path) {
	    if (path == null || path.equals(lastInputFile)) return;
	    lastInputFile      = path;
	    lastGraphInputFile = "";
	    lastRulesInputFile = "";
	    clearGraph();
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
					this::openInRcaviz, 
					path -> {
				        AppPreferences.addRecentFamily(path.toString());
				        refreshRecentMenus();
				        // Charger dans l'éditeur famille
				        if (familyEditorController != null
				                && familyEditorController.confirmDiscard()) {
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
	// ── Ouverture dans les éditeurs ───────────────────────────────────────────

	@FXML
	private void onOpenDot() {
	    FileChooser fc = new FileChooser();
	    fc.setTitle(I18n.get("graph.btn.open.dot"));
	    fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
	    fc.getExtensionFilters().addAll(
	        new FileChooser.ExtensionFilter("GraphViz DOT", "*.dot"),
	        new FileChooser.ExtensionFilter(I18n.get("filter.all"), "*.*")
	    );
	    File f = fc.showOpenDialog(graphWebView.getScene().getWindow());
	    if (f == null) return;
	    AppPreferences.setLastDirectory(f.getParent());
	    Path dot = f.toPath();
	    dotFileLabel.setText(dot.getFileName().toString());
	    appendConsole(I18n.get("console.graphviz.render", dot.getFileName()));
	    clearGraph();
	    dotFileLabel.setText(dot.getFileName().toString()); // restaurer après clearGraph
	    renderer.render(dot).thenRun(() -> Platform.runLater(() -> {
	        enableGraphButtons();
	        hideOverlay();
	    })).exceptionally(ex -> {
	        Platform.runLater(() -> {
	            hideOverlay();
	            appendConsole("[GraphViz] " + ex.getCause().getMessage());
	        });
	        return null;
	    });
	    showOverlayDelayed();
	    mainTabPane.getSelectionModel().select(conceptStructureTab);
	}
	   private void openContextInEditor(IBinaryContext ctx) {
	        if (contextEditorController != null) {
	            mainTabPane.getSelectionModel().select(2);
	            contextEditorController.loadContextFromFamily(ctx, modifiedCtx -> {
	                Platform.runLater(() -> {
	                    RCAFamily currentFamily = familyEditorController.getFamily();
	                    // Remplacer le contexte dans la famille par la version éditée
	                    RCAFamily.FormalContext fc = currentFamily.getFormalContext(modifiedCtx.getName());
	                    if (fc != null) fc.setContext(modifiedCtx);
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
			refreshRecentMenus();
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
			refreshRecentMenus();
			familyEditorController.openFile(filePath);
			mainTabPane.getSelectionModel().select(3);
			selectCommandTabFor(filePath.toString()); // ← ajouter
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

	@FXML
	private void onToggleMagnifier() {
		if (renderer.getCurrentDotFile() == null)
			return; // pas de graphe chargé
		renderer.toggleMagnifier();
		btnMagnifier.setStyle(renderer.isMagnifierActive() ? "-fx-background-color: #e0e0e0;" : "");
	}

	public void shutdown() {
	    if (rcavizServer  != null) rcavizServer.stop(0);
	    if (fcavizirServer != null) fcavizirServer.stop(0);
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

	    final var    args        = builder.build();
	    final String commandName = args.get(0);
	    final String inputFile   = args.size() > 1 ? args.get(1) : "";
	    final boolean isRules    = "RULEBASIS".equals(commandName) || "DBASIS".equals(commandName);
	    final boolean isGraph    = !isRules
                && !"BINARIZE".equals(commandName)
                && !"FAMILY_IMPORT".equals(commandName);

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
	            clearGraph();
	        }
	        mainTabPane.getSelectionModel().select(conceptStructureTab);
	    }

	    showOverlayDelayed();

	    // ── Limiter l'affichage console pour les commandes à sortie volumineuse ───
	    final int     MAX_CONSOLE_LINES = 500;
	    final int[]   lineCount         = { 0 };
	    final boolean[] truncated       = { false };
	    final long    startTime         = System.currentTimeMillis();

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
	            } else if(isGraph){
	                lastGraphInputFile = inputFile;
	                tryRenderDot(builder);
	            }

	            // Mettre à jour les récents
	            if (args.size() > 1
	                    && !"BINARIZE".equals(commandName)
	                    && !"FAMILY_IMPORT".equals(commandName)) {
	                String sep  = null;
	                int    sIdx = args.indexOf("-s");
	                if (sIdx >= 0 && sIdx + 1 < args.size())
	                    sep = args.get(sIdx + 1);
	                AppPreferences.addRecentContext(args.get(1), sep);
	                refreshRecentMenus();
	            }

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
		if (renderer.isMagnifierActive())
			renderer.toggleMagnifier();
		dotFileLabel.setText(""); 
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
		dotFileLabel.setText(dotFile.getFileName().toString()); // ← ajouter
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
			InputStream logoStream = getClass().getResourceAsStream("/fr/lirmm/fca4j/ui/icons/fca4j-ui_128x128.png");
			if (logoStream != null) {
				ImageView logo = new ImageView(new Image(logoStream));
				logo.setFitWidth(128);
				logo.setFitHeight(128);
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
			refreshRecentMenus();
			modelEditorController.openFile(path);
			mainTabPane.getSelectionModel().select(4);
			selectCommandTabFor(path.toString()); // ← ajouter
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
		buildRecentMenu(recentContextMenu, AppPreferences.getRecentContexts(), entry -> openInEditor(entry),
				() -> AppPreferences.clearRecentContexts());
		buildRecentMenu(recentFamilyMenu, AppPreferences.getRecentFamilies(),
				entry -> openInFamilyEditor(Path.of(AppPreferences.recentEntryPath(entry))),
				() -> AppPreferences.clearRecentFamilies());
		buildRecentMenu(recentModelMenu, AppPreferences.getRecentModels(),
				entry -> openInModelEditor(Path.of(AppPreferences.recentEntryPath(entry))),
				() -> AppPreferences.clearRecentModels());
	}

	private void buildRecentMenu(javafx.scene.control.Menu menu, java.util.List<String> entries,
			Consumer<String> action, Runnable onClear) {
		menu.getItems().clear();
		if (entries.isEmpty()) {
			javafx.scene.control.MenuItem empty = new javafx.scene.control.MenuItem(I18n.get("menu.file.recent.empty"));
			empty.setDisable(true);
			menu.getItems().add(empty);
		} else {
			for (String entry : entries) {
				String path = AppPreferences.recentEntryPath(entry);
				java.io.File f = new java.io.File(path);
				String label = f.getName() + "   —   [" + shortenPath(f.getParent(), 40) + "]";
				javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(label);
				item.setDisable(!f.exists());
				item.setOnAction(e -> {
					action.accept(entry);
					AppPreferences.setLastDirectory(f.getParent());
				});
				menu.getItems().add(item);
			}
			// Séparateur + action vider
			menu.getItems().add(new SeparatorMenuItem());
			javafx.scene.control.MenuItem clearItem = new javafx.scene.control.MenuItem(
					I18n.get("menu.file.recent.clear"));
			clearItem.setOnAction(e -> {
				onClear.run();
				refreshRecentMenus();
			});
			menu.getItems().add(clearItem);
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
}