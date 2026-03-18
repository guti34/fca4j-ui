package fr.lirmm.fca4j.ui.controller;

import fr.lirmm.fca4j.core.BinaryContext;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.std.BitSetFactory;
import fr.lirmm.fca4j.ui.service.ContextIOService;
import fr.lirmm.fca4j.ui.service.ContextIOService.ContextFormat;
import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.I18n;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Contrôleur de l'éditeur de contexte formel binaire.
 * Affiche IBinaryContext dans une TableView JavaFX éditable.
 */
public class ContextEditorController implements Initializable {

    // ── Widgets FXML ──────────────────────────────────────────────────────────
    @FXML private Label         contextNameLabel;
    @FXML private Label         statsLabel;
    @FXML private TableView<ObservableList<SimpleBooleanProperty>> tableView;
    @FXML private Label         statusLabel;

    // ── Services ──────────────────────────────────────────────────────────────
    private final ContextIOService ioService = new ContextIOService();

    // ── État ──────────────────────────────────────────────────────────────────
    private IBinaryContext context;
    private Path           currentFile;
    private boolean        modified = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        tableView.setEditable(true);
        tableView.getSelectionModel().setCellSelectionEnabled(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        tableView.setPlaceholder(new Label(I18n.get("editor.empty")));

        // Nouveau contexte vide par défaut
        loadContext(ioService.createEmpty(I18n.get("editor.new.context.name")));
    }

    // ── Chargement / sauvegarde ───────────────────────────────────────────────

    /**
     * Charge un IBinaryContext dans la TableView.
     */
    public void loadContext(IBinaryContext ctx) {
        this.context  = ctx;
        this.modified = false;
        rebuildTable();
        updateLabels();
    }

    /**
     * Reconstruit entièrement la TableView depuis le contexte courant.
     * Appelé après tout changement de structure (ajout/suppression ligne/colonne).
     */
    private void rebuildTable() {
        tableView.getColumns().clear();
        tableView.getItems().clear();

        int nbObj  = context.getObjectCount();
        int nbAttr = context.getAttributeCount();

        // ── Colonne "Objet" (en-tête fixe, labels éditables) ─────────────────
        TableColumn<ObservableList<SimpleBooleanProperty>, String> objCol =
            new TableColumn<>(I18n.get("editor.col.objects"));
        objCol.setPrefWidth(130);
        objCol.setEditable(false);
        objCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                int rowIdx = getIndex();
                Label lbl = new Label(context.getObjectName(rowIdx));
                lbl.setMaxWidth(Double.MAX_VALUE);
                // Double-clic pour renommer
                lbl.setOnMouseClicked(e -> {
                    if (e.getClickCount() == 2) promptRenameObject(rowIdx);
                });
                setGraphic(lbl);
            }
        });
        // La valeur n'est pas utilisée directement (on lit depuis context)
        objCol.setCellValueFactory(data -> null);
        tableView.getColumns().add(objCol);

        // ── Colonnes attributs ────────────────────────────────────────────────
        for (int a = 0; a < nbAttr; a++) {
            final int attrIdx = a;
            TableColumn<ObservableList<SimpleBooleanProperty>, Boolean> col =
                new TableColumn<>(context.getAttributeName(a));
            col.setPrefWidth(80);
            col.setEditable(true);

            // Double-clic sur l'en-tête pour renommer
            Label header = new Label(context.getAttributeName(a));
            header.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) promptRenameAttribute(attrIdx);
            });
            col.setGraphic(header);
            col.setText("");

            col.setCellValueFactory(data -> data.getValue().get(attrIdx).asObject());
            col.setCellFactory(c -> {
                CheckBoxTableCell<ObservableList<SimpleBooleanProperty>, Boolean> cell =
                    new CheckBoxTableCell<>();
                cell.setAlignment(Pos.CENTER);
                return cell;
            });
            col.setOnEditCommit(event -> {
                int rowIdx = event.getTablePosition().getRow();
                boolean val = event.getNewValue();
                context.set(rowIdx, attrIdx, val);
                event.getRowValue().get(attrIdx).set(val);
                markModified();
            });

            tableView.getColumns().add(col);
        }

        // ── Données ───────────────────────────────────────────────────────────
        for (int o = 0; o < nbObj; o++) {
            ObservableList<SimpleBooleanProperty> row =
                FXCollections.observableArrayList();
            for (int a = 0; a < nbAttr; a++) {
                final int oi = o, ai = a;
                SimpleBooleanProperty prop =
                    new SimpleBooleanProperty(context.get(o, a));
                prop.addListener((obs, old, val) -> {
                    context.set(oi, ai, val);
                    markModified();
                });
                row.add(prop);
            }
            tableView.getItems().add(row);
        }
    }

    private void updateLabels() {
        String name = (context.getName() != null && !context.getName().isBlank())
            ? context.getName() : I18n.get("editor.new.context.name");
        contextNameLabel.setText(name + (modified ? " *" : ""));
        statsLabel.setText(I18n.get("editor.stats",
            context.getObjectCount(), context.getAttributeCount()));
    }

    private void markModified() {
        if (!modified) {
            modified = true;
            Platform.runLater(this::updateLabels);
        }
    }

    // ── Actions toolbar ───────────────────────────────────────────────────────

    @FXML
    private void onNewContext() {
        if (!confirmDiscardChanges()) return;
        String name = promptText(I18n.get("editor.dialog.new.title"),
                                 I18n.get("editor.dialog.new.prompt"),
                                 I18n.get("editor.new.context.name"));
        if (name == null) return;
        loadContext(ioService.createEmpty(name));
        currentFile = null;
    }

    @FXML
    private void onOpen() {
        if (!confirmDiscardChanges()) return;
        FileChooser fc = buildFileChooser(I18n.get("editor.open.title"), false);
        File f = fc.showOpenDialog(tableView.getScene().getWindow());
        if (f == null) return;
        try {
            IBinaryContext ctx = ioService.read(f.toPath());
            loadContext(ctx);
            currentFile = f.toPath();
            AppPreferences.setLastDirectory(f.getParent());
            statusLabel.setText(I18n.get("editor.status.loaded", f.getName()));
        } catch (Exception e) {
            showError(I18n.get("editor.error.read.title"), e.getMessage());
        }
    }

    @FXML
    private void onSave() {
        if (currentFile == null) { onSaveAs(); return; }
        saveToFile(currentFile, ContextFormat.fromFile(currentFile.toFile()));
    }

    @FXML
    private void onSaveAs() {
        FileChooser fc = buildFileChooser(I18n.get("editor.saveas.title"), true);
        File f = fc.showSaveDialog(tableView.getScene().getWindow());
        if (f == null) return;
        currentFile = f.toPath();
        saveToFile(currentFile, ContextFormat.fromFile(f));
    }

    private void saveToFile(Path path, ContextFormat format) {
        try {
            ioService.write(context, path, format);
            modified = false;
            updateLabels();
            statusLabel.setText(I18n.get("editor.status.saved", path.getFileName()));
        } catch (Exception e) {
            showError(I18n.get("editor.error.write.title"), e.getMessage());
        }
    }

    // ── Actions structure ─────────────────────────────────────────────────────

    @FXML
    private void onAddObject() {
        String name = promptText(I18n.get("editor.dialog.add.object.title"),
                                 I18n.get("editor.dialog.add.object.prompt"),
                                 I18n.get("editor.default.object.name")
                                     + (context.getObjectCount() + 1));
        if (name == null) return;

        // Ajouter l'objet avec un intent vide
        context.addObjectName(name);
        // Initialiser toutes les cellules à false pour ce nouvel objet
        int newObjIdx = context.getObjectCount() - 1;
        for (int a = 0; a < context.getAttributeCount(); a++) {
            context.set(newObjIdx, a, false);
        }
        rebuildTable();
        markModified();
        // Scroller vers la dernière ligne
        tableView.scrollTo(tableView.getItems().size() - 1);
    }

    @FXML
    private void onAddAttribute() {
        String name = promptText(I18n.get("editor.dialog.add.attr.title"),
                                 I18n.get("editor.dialog.add.attr.prompt"),
                                 I18n.get("editor.default.attr.name")
                                     + (context.getAttributeCount() + 1));
        if (name == null) return;

        context.addAttributeName(name);
        int newAttrIdx = context.getAttributeCount() - 1;
        for (int o = 0; o < context.getObjectCount(); o++) {
            context.set(o, newAttrIdx, false);
        }
        rebuildTable();
        markModified();
    }

    @FXML
    private void onRemoveObject() {
        int sel = tableView.getSelectionModel().getSelectedIndex();
        if (sel < 0) {
            showInfo(I18n.get("editor.select.object.first"));
            return;
        }
        String objName = context.getObjectName(sel);
        if (!confirmDelete(I18n.get("editor.confirm.delete.object", objName))) return;
        context.removeObject(sel);
        rebuildTable();
        markModified();
    }

    @FXML
    private void onRemoveAttribute() {
        // Récupère la colonne sélectionnée (index - 1 car col 0 = objets)
        TablePosition<?, ?> pos = tableView.getSelectionModel().getSelectedCells()
            .stream().findFirst().orElse(null);
        int colIdx = (pos != null) ? pos.getColumn() - 1 : -1;
        if (colIdx < 0) {
            showInfo(I18n.get("editor.select.attr.first"));
            return;
        }
        String attrName = context.getAttributeName(colIdx);
        if (!confirmDelete(I18n.get("editor.confirm.delete.attr", attrName))) return;
        context.removeAttribute(colIdx);
        rebuildTable();
        markModified();
    }

    @FXML
    private void onRenameContext() {
        String current = context.getName() != null ? context.getName() : "";
        String newName = promptText(I18n.get("editor.dialog.rename.context"),
                                    I18n.get("editor.dialog.name.prompt"), current);
        if (newName == null) return;
        context.setName(newName);
        updateLabels();
        markModified();
    }

    // ── Renommage inline ──────────────────────────────────────────────────────

    private void promptRenameObject(int idx) {
        String newName = promptText(I18n.get("editor.dialog.rename.object"),
                                    I18n.get("editor.dialog.name.prompt"),
                                    context.getObjectName(idx));
        if (newName == null) return;
        context.setObjectName(idx, newName);
        tableView.refresh();
        markModified();
    }

    private void promptRenameAttribute(int idx) {
        String newName = promptText(I18n.get("editor.dialog.rename.attr"),
                                    I18n.get("editor.dialog.name.prompt"),
                                    context.getAttributeName(idx));
        if (newName == null) return;
        context.setAttributeName(idx, newName);
        // Mettre à jour l'en-tête de la colonne (idx+1 car col 0 = objets)
        Label header = (Label) tableView.getColumns().get(idx + 1).getGraphic();
        if (header != null) header.setText(newName);
        markModified();
    }

    // ── Utilitaires UI ────────────────────────────────────────────────────────

    private FileChooser buildFileChooser(String title, boolean forSave) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("CXT (Burmeister)", "*.cxt"),
            new FileChooser.ExtensionFilter("CEX (ConExp)",     "*.cex"),
            new FileChooser.ExtensionFilter("SLF",              "*.slf"),
            new FileChooser.ExtensionFilter("CSV",              "*.csv"),
            new FileChooser.ExtensionFilter("XML (Galicia)",    "*.xml"),
            new FileChooser.ExtensionFilter(I18n.get("filter.all"), "*.*")
        );
        return fc;
    }

    private String promptText(String title, String prompt, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(prompt);
        Optional<String> result = dialog.showAndWait();
        return result.filter(s -> !s.isBlank()).orElse(null);
    }

    private boolean confirmDiscardChanges() {
        if (!modified) return true;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(I18n.get("editor.confirm.discard.title"));
        alert.setHeaderText(null);
        alert.setContentText(I18n.get("editor.confirm.discard.detail"));
        return alert.showAndWait()
            .filter(b -> b == ButtonType.OK).isPresent();
    }

    private boolean confirmDelete(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(I18n.get("editor.confirm.delete.title"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        return alert.showAndWait()
            .filter(b -> b == ButtonType.OK).isPresent();
    }

    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg);
        a.showAndWait();
    }

    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(I18n.get("app.title")); a.setHeaderText(null); a.setContentText(msg);
        a.showAndWait();
    }

    // ── Accesseur pour MainController ─────────────────────────────────────────

    /** Retourne le contexte courant (pour le passer directement aux commandes). */
    public IBinaryContext getContext() { return context; }

    /** Charge un fichier depuis l'extérieur (ex: depuis le panneau commandes). */
    public void openFile(Path path) {
        try {
            loadContext(ioService.read(path));
            currentFile = path;
        } catch (Exception e) {
            showError(I18n.get("editor.error.read.title"), e.getMessage());
        }
    }
}