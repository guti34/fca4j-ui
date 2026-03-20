package fr.lirmm.fca4j.ui.controller;

import fr.lirmm.fca4j.ui.MainApp;
import fr.lirmm.fca4j.ui.model.CommandBuilder;
import fr.lirmm.fca4j.ui.model.CommandDescriptor;
import fr.lirmm.fca4j.ui.service.Fca4jRunner;
import fr.lirmm.fca4j.ui.service.GraphRenderer;
import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.I18n;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    // ── Widgets FXML ──────────────────────────────────────────────────────────
    @FXML private ComboBox<String>  commandCombo;
    @FXML private StackPane         commandPanelContainer;
    @FXML private WebView           graphWebView;
    @FXML private TextArea          consoleArea;
    @FXML private Label             statusLabel;
    @FXML private Label             selectedNodeLabel;
    @FXML private TabPane           mainTabPane;

    // ── Contrôleur de l'éditeur (injecté via fx:include) ─────────────────────
    @FXML private ContextEditorController contextEditorController;

    // ── Services ──────────────────────────────────────────────────────────────
    private final Fca4jRunner runner = new Fca4jRunner();
    private GraphRenderer renderer;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        renderer = new GraphRenderer(graphWebView.getEngine());
        renderer.setOnNodeClick(this::onNodeSelected);

        commandCombo.getItems().addAll("LATTICE", "AOCPOSET", "RULEBASIS", "DBASIS", "CLARIFY", "REDUCE", "IRREDUCIBLE", "INSPECT", "BINARIZE");
        commandCombo.setValue("LATTICE");
        commandCombo.valueProperty().addListener((obs, old, val) -> loadCommandPanel(val));

        selectedNodeLabel.setText(I18n.get("panel.node.none"));
        loadCommandPanel("LATTICE");

        statusLabel.setText(AppPreferences.isFca4jConfigured()
            ? I18n.get("status.configured", AppPreferences.getFca4jJarPath())
            : I18n.get("status.not.configured"));
    }

    // ── Chargement dynamique du panneau de commande ───────────────────────────

    private void loadCommandPanel(String command) {
        try {
            CommandDescriptor desc = CommandDescriptor.forName(command);
            if (desc == null) {
                commandPanelContainer.getChildren().setAll(
                    new Label(I18n.get("error.panel.load", command)));
                return;
            }

            String fxml = switch (desc.getFamily()) {
                case LATTICE_AOC    -> "/fr/lirmm/fca4j/ui/fxml/lattice_aoc.fxml";
                case RULE_BASIS     -> "/fr/lirmm/fca4j/ui/fxml/rule_basis.fxml";
                case REDUCE_CLARIFY -> "/fr/lirmm/fca4j/ui/fxml/reduce_clarify.fxml";
                case IRREDUCIBLE    -> "/fr/lirmm/fca4j/ui/fxml/irreducible.fxml";
                case INSPECT        -> "/fr/lirmm/fca4j/ui/fxml/inspect.fxml";
                case BINARIZE       -> "/fr/lirmm/fca4j/ui/fxml/binarize.fxml";
            };

            FXMLLoader loader = new FXMLLoader(
                getClass().getResource(fxml), I18n.getBundle());
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
            while (cause.getCause() != null) cause = cause.getCause();
            appendConsole("[Erreur panneau] " + e.getClass().getSimpleName()
                + ": " + e.getMessage());
            appendConsole("[Cause] " + cause.getClass().getSimpleName()
                + ": " + cause.getMessage());
            e.printStackTrace();
        }
    }


    // ── Ouverture dans l'éditeur ──────────────────────────────────────────────

    /**
     * Ouvre un fichier dans le ContextEditor et bascule sur l'onglet éditeur.
     * Appelé depuis les panneaux de commande via le bouton "Éditer".
     */
    public void openInEditor(Path filePath) {
        if (contextEditorController != null) {
            contextEditorController.openFile(filePath);
            // Basculer sur l'onglet éditeur (index 1)
            mainTabPane.getSelectionModel().select(1);
        }
    }

    // ── Exécution ─────────────────────────────────────────────────────────────

    private void executeCommand(CommandBuilder builder) {
        if (!AppPreferences.isFca4jConfigured()) {
            showAlert(I18n.get("error.not.configured.title"),
                      I18n.get("error.not.configured.detail"));
            return;
        }

        var args = builder.build();
        consoleArea.clear();
        statusLabel.setText(I18n.get("status.running", args.get(0)));
        appendConsole("$ " + builder.toDisplayString());

        // Basculer sur l'onglet graphe pour voir la console
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

    private void tryRenderDot(CommandBuilder builder) {
        var args = builder.build();
        int gIdx = args.indexOf("-g");
        if (gIdx == -1 || gIdx + 1 >= args.size()) return;

        Path dotFile = Path.of(args.get(gIdx + 1));
        if (!dotFile.toFile().exists()) {
            appendConsole(I18n.get("error.graphviz.not.found", dotFile));
            return;
        }
        appendConsole(I18n.get("console.graphviz.render", dotFile));
        renderer.render(dotFile)
            .exceptionally(ex -> {
                Platform.runLater(() ->
                    appendConsole("[GraphViz] " + ex.getCause().getMessage()));
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
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/fr/lirmm/fca4j/ui/fxml/preferences.fxml"),
            I18n.getBundle());
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(I18n.get("prefs.title"));
        dialog.setScene(new Scene(loader.load()));
        dialog.showAndWait();

        statusLabel.setText(AppPreferences.isFca4jConfigured()
            ? I18n.get("status.configured", AppPreferences.getFca4jJarPath())
            : I18n.get("status.not.configured"));
    }

    @FXML private void onQuit() { Platform.exit(); }

    @FXML
    private void onAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(I18n.get("menu.help.about"));
        alert.setHeaderText(MainApp.APP_TITLE + " " + MainApp.APP_VERSION);
        alert.setContentText(I18n.get("app.about.content"));
 
        // Logo dans le dialogue About
        try {
            java.io.InputStream logoStream = getClass()
                .getResourceAsStream("/fr/lirmm/fca4j/ui/icons/fca4j-logo.png");
            if (logoStream != null) {
                ImageView logo = new ImageView(new Image(logoStream));
                logo.setFitWidth(64);
                logo.setFitHeight(64);
                logo.setPreserveRatio(true);
                logo.setSmooth(true);
                alert.setGraphic(logo);
            }
        } catch (Exception ignored) {}
 
        alert.showAndWait();
    }
 
    @FXML private void onClearConsole() { consoleArea.clear(); }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private void appendConsole(String text) { consoleArea.appendText(text + "\n"); }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(message);
        alert.showAndWait();
    }
}
