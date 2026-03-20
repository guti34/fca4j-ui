package fr.lirmm.fca4j.ui.controller;

import fr.lirmm.fca4j.core.BinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.std.BitSetFactory;
import fr.lirmm.fca4j.ui.service.ContextIOService;
import fr.lirmm.fca4j.ui.service.ContextIOService.ContextFormat;
import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.I18n;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.cell.CheckBoxTableCell;
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

    /**
     * Représente une ligne de la TableView.
     * Porte le nom de l'objet comme SimpleStringProperty pour que
     * le tri de la TableView reste cohérent avec la colonne objets.
     */
    private static class ContextRow {
        private final SimpleStringProperty objectName;
        private final ObservableList<SimpleBooleanProperty> cells;

        ContextRow(String name, ObservableList<SimpleBooleanProperty> cells) {
            this.objectName = new SimpleStringProperty(name);
            this.cells      = cells;
        }

        String getName()                    { return objectName.get(); }
        void   setName(String n)            { objectName.set(n); }
        SimpleStringProperty nameProperty() { return objectName; }
        ObservableList<SimpleBooleanProperty> getCells() { return cells; }
    }

    // ── Boutons toolbar ───────────────────────────────────────────────────────
    @FXML private Button btnNew;
    @FXML private Button btnOpen;
    @FXML private Button btnSave;
    @FXML private Button btnSaveAs;
    @FXML private Button btnAddObject;
    @FXML private Button btnAddAttr;
    @FXML private Button btnDelObject;
    @FXML private Button btnDelAttr;
    @FXML private Button btnRename;

    // ── Widgets FXML ──────────────────────────────────────────────────────────
    @FXML private Label              contextNameLabel;
    @FXML private Label              statsLabel;
    @FXML private TableView<ContextRow> tableView;
    @FXML private Label              statusLabel;

    // ── Stockage des lignes ───────────────────────────────────────────────────
    private final List<ContextRow> rows = new ArrayList<>();

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
        setupToolbar();
        loadContext(ioService.createEmpty(I18n.get("editor.new.context.name")));
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private void setupToolbar() {
        setIconAndTooltip(btnNew,       new FontIcon(Material2AL.FIBER_NEW),              I18n.get("editor.tooltip.new"));
        setIconAndTooltip(btnOpen,      new FontIcon(Material2AL.FOLDER_OPEN),            I18n.get("editor.tooltip.open"));
        setIconAndTooltip(btnSave,      new FontIcon(Material2MZ.SAVE),                   I18n.get("editor.tooltip.save"));
        setIconAndTooltip(btnSaveAs,    new FontIcon(Material2MZ.SAVE_ALT),               I18n.get("editor.tooltip.saveas"));
        setIconAndTooltip(btnAddObject, new FontIcon(Material2MZ.PLAYLIST_ADD),           I18n.get("editor.tooltip.add.object"));
        setIconAndTooltip(btnAddAttr,   new FontIcon(Material2AL.ADD_CIRCLE_OUTLINE),     I18n.get("editor.tooltip.add.attr"));
        setIconAndTooltip(btnDelObject, new FontIcon(Material2MZ.REMOVE),                 I18n.get("editor.tooltip.del.object"));
        setIconAndTooltip(btnDelAttr,   new FontIcon(Material2MZ.REMOVE_CIRCLE_OUTLINE),  I18n.get("editor.tooltip.del.attr"));
        setIconAndTooltip(btnRename,    new FontIcon(Material2MZ.TEXT_FIELDS),            I18n.get("editor.tooltip.rename"));
    }

    private void setIconAndTooltip(Button btn, FontIcon icon, String tooltipText) {
        icon.setIconSize(20);
        icon.setIconColor(javafx.scene.paint.Color.valueOf("#333333"));
        btn.setGraphic(icon);
        btn.setTooltip(new Tooltip(tooltipText));
    }

    // ── Chargement ────────────────────────────────────────────────────────────

    public void loadContext(IBinaryContext ctx) {
        this.context  = ctx;
        this.modified = false;
        rebuildTable();
        updateLabels();
    }

    // ── Construction de la TableView ──────────────────────────────────────────

    private void rebuildTable() {
        tableView.getColumns().clear();
        tableView.getItems().clear();
        rows.clear();

        int nbObj  = context.getObjectCount();
        int nbAttr = context.getAttributeCount();

        // ── Colonne "Objet" ───────────────────────────────────────────────────
        TableColumn<ContextRow, String> objCol =
            new TableColumn<>(I18n.get("editor.col.objects"));
        objCol.setPrefWidth(150);
        objCol.setEditable(false);
        // Lire le nom depuis la ContextRow — reste correct après tri
        objCol.setCellValueFactory(data -> data.getValue().nameProperty());
        objCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                Label lbl = new Label(item);
                lbl.setMaxWidth(Double.MAX_VALUE);
                lbl.setOnMouseClicked(e -> {
                    if (e.getClickCount() == 2) {
                        ContextRow row = tableView.getItems().get(getIndex());
                        promptRenameObjectRow(row);
                    }
                });
                setGraphic(lbl);
                setText(null);
            }
        });
        tableView.getColumns().add(objCol);

        // ── Colonnes attributs ────────────────────────────────────────────────
        for (int a = 0; a < nbAttr; a++) {
            final int attrIdx = a;
            TableColumn<ContextRow, Boolean> col = new TableColumn<>();

            // Largeur adaptée au nom, entre 50px et 160px (≈20 caractères)
            double prefWidth = Math.max(50.0,
                Math.min(160.0, context.getAttributeName(a).length() * 8.0 + 16));
            col.setPrefWidth(prefWidth);
            col.setMinWidth(50.0);
            col.setMaxWidth(160.0);
            col.setEditable(true);
            col.setResizable(true);

            // En-tête avec tooltip si nom long
            String attrName = context.getAttributeName(a);
            Label header = new Label(attrName);
            header.setMaxWidth(Double.MAX_VALUE);
            if (attrName.length() > 15) {
                header.setTooltip(new Tooltip(attrName));
            }

            // Menu contextuel clic droit
            MenuItem renameItem = new MenuItem(I18n.get("editor.menu.rename.attr"));
            MenuItem deleteItem = new MenuItem(I18n.get("editor.menu.delete.attr"));
            renameItem.setOnAction(e -> promptRenameAttribute(attrIdx));
            deleteItem.setOnAction(e -> deleteAttribute(attrIdx));
            ContextMenu ctxMenu = new ContextMenu(renameItem, deleteItem);

            header.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    promptRenameAttribute(attrIdx);
                } else if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                    ctxMenu.show(header, e.getScreenX(), e.getScreenY());
                }
            });
            col.setGraphic(header);

            // BooleanProperty lu depuis la ContextRow
            col.setCellValueFactory(data -> data.getValue().getCells().get(attrIdx));
            col.setCellFactory(CheckBoxTableCell.forTableColumn(col));

            tableView.getColumns().add(col);
        }

        // ── Construction des données ──────────────────────────────────────────
        for (int o = 0; o < nbObj; o++) {
            ObservableList<SimpleBooleanProperty> cells =
                FXCollections.observableArrayList();
            for (int a = 0; a < nbAttr; a++) {
                final int ai = a;
                SimpleBooleanProperty prop =
                    new SimpleBooleanProperty(context.get(o, a));
                prop.addListener((obs, oldVal, newVal) -> {
                    // Retrouver l'index réel via le nom (résistant au tri)
                    int realObj = context.getObjectIndex(
                        rows.stream()
                            .filter(r -> r.getCells().get(ai) == prop)
                            .findFirst()
                            .map(ContextRow::getName)
                            .orElse("")
                    );
                    if (realObj >= 0) context.set(realObj, ai, newVal);
                    markModified();
                });
                cells.add(prop);
            }
            ContextRow cr = new ContextRow(context.getObjectName(o), cells);
            rows.add(cr);
            tableView.getItems().add(cr);
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
        ISet emptyIntent = ioService.getFactory().createSet();
        context.addObject(name, emptyIntent);
        rebuildTable();
        markModified();
        tableView.scrollTo(tableView.getItems().size() - 1);
    }

    @FXML
    private void onAddAttribute() {
        String name = promptText(I18n.get("editor.dialog.add.attr.title"),
                                 I18n.get("editor.dialog.add.attr.prompt"),
                                 I18n.get("editor.default.attr.name")
                                     + (context.getAttributeCount() + 1));
        if (name == null) return;
        ISet emptyExtent = ioService.getFactory().createSet();
        context.addAttribute(name, emptyExtent);
        rebuildTable();
        markModified();
        tableView.scrollToColumn(tableView.getColumns().get(tableView.getColumns().size() - 1));
    }

    @FXML
    private void onRemoveObject() {
        int sel = tableView.getSelectionModel().getSelectedIndex();
        if (sel < 0) {
            showInfo(I18n.get("editor.select.object.first"));
            return;
        }
        // Nom depuis la ContextRow — correct même après tri
        ContextRow row = tableView.getItems().get(sel);
        String objName = row.getName();
        if (!confirmDelete(I18n.get("editor.confirm.delete.object", objName))) return;
        int realIdx = context.getObjectIndex(objName);
        if (realIdx >= 0) context.removeObject(realIdx);
        rebuildTable();
        markModified();
    }

    @FXML
    private void onRemoveAttribute() {
        if (context.getAttributeCount() == 0) {
            showInfo(I18n.get("editor.select.attr.first"));
            return;
        }
        javafx.scene.control.ChoiceDialog<String> dialog =
            new javafx.scene.control.ChoiceDialog<>(
                context.getAttributeName(0),
                java.util.stream.IntStream.range(0, context.getAttributeCount())
                    .mapToObj(context::getAttributeName)
                    .collect(java.util.stream.Collectors.toList())
            );
        dialog.setTitle(I18n.get("editor.dialog.del.attr.title"));
        dialog.setHeaderText(null);
        dialog.setContentText(I18n.get("editor.dialog.del.attr.prompt"));
        dialog.showAndWait().ifPresent(name -> {
            int idx = context.getAttributeIndex(name);
            if (idx >= 0) deleteAttribute(idx);
        });
    }

    private void deleteAttribute(int attrIdx) {
        String attrName = context.getAttributeName(attrIdx);
        if (!confirmDelete(I18n.get("editor.confirm.delete.attr", attrName))) return;
        context.removeAttribute(attrIdx);
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

    // ── Renommage ─────────────────────────────────────────────────────────────

    private void promptRenameObjectRow(ContextRow row) {
        String oldName = row.getName();
        String newName = promptText(I18n.get("editor.dialog.rename.object"),
                                    I18n.get("editor.dialog.name.prompt"), oldName);
        if (newName == null) return;
        int realIdx = context.getObjectIndex(oldName);
        if (realIdx >= 0) context.setObjectName(realIdx, newName);
        row.setName(newName);  // met à jour le label via SimpleStringProperty
        markModified();
    }

    private void promptRenameAttribute(int idx) {
        String newName = promptText(I18n.get("editor.dialog.rename.attr"),
                                    I18n.get("editor.dialog.name.prompt"),
                                    context.getAttributeName(idx));
        if (newName == null) return;
        context.setAttributeName(idx, newName);
        // Mettre à jour l'en-tête (idx+1 car col 0 = objets)
        Label header = (Label) tableView.getColumns().get(idx + 1).getGraphic();
        if (header != null) {
            header.setText(newName);
            header.setTooltip(newName.length() > 15 ? new Tooltip(newName) : null);
        }
        markModified();
    }

    // ── Utilitaires UI ────────────────────────────────────────────────────────

    private FileChooser buildFileChooser(String title, boolean forSave) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("SLF (HTK)",        "*.slf"),
            new FileChooser.ExtensionFilter("CEX (ConExp)",     "*.cex"),
            new FileChooser.ExtensionFilter("CXT (Burmeister)", "*.cxt"),
            new FileChooser.ExtensionFilter("XML (Galicia)",    "*.xml"),
            new FileChooser.ExtensionFilter("CSV",              "*.csv"),
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
        return alert.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    private boolean confirmDelete(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(I18n.get("editor.confirm.delete.title"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        return alert.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
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

    // ── Accesseurs ────────────────────────────────────────────────────────────

    public IBinaryContext getContext() { return context; }

    public void openFile(Path path) {
        try {
            loadContext(ioService.read(path));
            currentFile = path;
        } catch (Exception e) {
            showError(I18n.get("editor.error.read.title"), e.getMessage());
        }
    }
}
