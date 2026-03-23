package fr.lirmm.fca4j.ui.controller;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.core.RCAFamily.FormalContext;
import fr.lirmm.fca4j.core.RCAFamily.RelationalContext;
import fr.lirmm.fca4j.iset.std.BitSetFactory;
import fr.lirmm.fca4j.ui.service.ContextIOService;
import fr.lirmm.fca4j.ui.service.RcfService;
import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.I18n;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/**
 * Éditeur visuel de famille de contextes relationnels (RCAFamily).
 */
public class FamilyEditorController implements Initializable {

    // ── Boutons toolbar ───────────────────────────────────────────────────────
    @FXML private Button btnNew;
    @FXML private Button btnOpen;
    @FXML private Button btnSave;
    @FXML private Button btnSaveAs;
    @FXML private Button btnAddContext;
    @FXML private Button btnAddRelation;
    @FXML private Button btnAutoLayout;

    // ── Widgets FXML ──────────────────────────────────────────────────────────
    @FXML private Label                              familyNameLabel;
    @FXML private Label                              statsLabel;
    @FXML private Pane                               graphPane;
    @FXML private Canvas                             graphCanvas;
    @FXML private TableView<FormalContext>           contextsTable;
    @FXML private TableColumn<FormalContext, String> ctxNameCol;
    @FXML private TableColumn<FormalContext, String> ctxObjCol;
    @FXML private TableColumn<FormalContext, String> ctxAttrCol;
    @FXML private TableView<RelationalContext>           relationsTable;
    @FXML private TableColumn<RelationalContext, String> relNameCol;
    @FXML private TableColumn<RelationalContext, String> relSourceCol;
    @FXML private TableColumn<RelationalContext, String> relTargetCol;
    @FXML private TableColumn<RelationalContext, String> relOpCol;

    // ── État ──────────────────────────────────────────────────────────────────
    private ContextMenu              contextMenu;
    private Consumer<IBinaryContext> openInContextEditor;
    private RCAFamily                family;
    private Path                     currentFile;
    private boolean                  modified = false;
    private final RcfService         rcfService = new RcfService();
    private final Map<String, Double> edgeCurvatures = new LinkedHashMap<>();
    private final Map<String, Point2D> nodePositions = new LinkedHashMap<>();
    private String selectedNode = null;
    private String selectedEdge = null;
    private String dragNode     = null;
    private double dragOffX, dragOffY;

    // ── Couleurs ──────────────────────────────────────────────────────────────
    private static final Color  COLOR_NODE_FILL     = Color.web("#4A90E2");
    private static final Color  COLOR_NODE_SELECTED = Color.web("#FF8C42");
    private static final Color  COLOR_NODE_STROKE   = Color.web("#2c5f9e");
    private static final Color  COLOR_EDGE          = Color.web("#555555");
    private static final Color  COLOR_EDGE_SELECTED = Color.web("#FF8C42");
    private static final Color  COLOR_TEXT          = Color.WHITE;

    // ── Dimensions des nœuds (rectangles arrondis) ────────────────────────────
    private static final double NODE_H       = 50;
    private static final double NODE_ARC     = 12;
    private static final double NODE_MIN_W   = 90;
    private static final double NODE_CHAR_W  = 8.0;
    private static final double NODE_PADDING = 20;

    /** Largeur dynamique selon le nom du contexte. */
    private double nodeWidth(String name) {
        return Math.max(NODE_MIN_W, name.length() * NODE_CHAR_W + NODE_PADDING);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupToolbar();
        setupContextsTable();
        setupRelationsTable();
        setupCanvas();
        setupContextMenu();
        loadEmpty();
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private void setupToolbar() {
        setIconAndTooltip(btnNew,         new FontIcon(Material2AL.FIBER_NEW),    I18n.get("button.new"));
        setIconAndTooltip(btnOpen,        new FontIcon(Material2AL.FOLDER_OPEN),  I18n.get("button.open"));
        setIconAndTooltip(btnSave,        new FontIcon(Material2MZ.SAVE),         I18n.get("button.save"));
        setIconAndTooltip(btnSaveAs,      new FontIcon(Material2MZ.SAVE_ALT),     I18n.get("button.saveas"));
        setIconAndTooltip(btnAddContext,  new FontIcon(Material2MZ.TABLE_ROWS), I18n.get("family.btn.add.context"));
        setIconAndTooltip(btnAddRelation, new FontIcon(Material2AL.LINK),         I18n.get("family.btn.add.relation"));
        setIconAndTooltip(btnAutoLayout,  new FontIcon(Material2AL.ACCOUNT_TREE), I18n.get("family.btn.auto.layout"));
    }

    private void setIconAndTooltip(Button btn, FontIcon icon, String tooltipText) {
        if (btn == null) return;
        icon.setIconSize(20);
        icon.setIconColor(Color.valueOf("#333333"));
        btn.setGraphic(icon);
        btn.setText("");
        btn.setTooltip(new Tooltip(tooltipText));
    }

    // ── Menu contextuel ───────────────────────────────────────────────────────

    private void setupContextMenu() {
        contextMenu = new ContextMenu();

        MenuItem editInEditorItem  = new MenuItem(I18n.get("family.menu.edit.in.editor"));
        MenuItem renameItem        = new MenuItem(I18n.get("family.menu.rename"));
        MenuItem removeItem        = new MenuItem(I18n.get("family.menu.remove"));
        MenuItem exportItem        = new MenuItem(I18n.get("family.menu.export"));
        MenuItem addContextItem    = new MenuItem(I18n.get("family.btn.add.context"));
        MenuItem addRelationItem   = new MenuItem(I18n.get("family.btn.add.relation"));

        editInEditorItem.setOnAction(e -> contextMenuEditInEditor());
        renameItem.setOnAction(e -> contextMenuRename());
        removeItem.setOnAction(e -> contextMenuRemove());
        exportItem.setOnAction(e -> contextMenuExport());
        addContextItem.setOnAction(e -> onAddContext());
        addRelationItem.setOnAction(e -> onAddRelation());

        contextMenu.getItems().addAll(
            editInEditorItem, new SeparatorMenuItem(),
            renameItem, new SeparatorMenuItem(),
            exportItem, new SeparatorMenuItem(),
            removeItem, new SeparatorMenuItem(),
            addContextItem, addRelationItem
        );

        contextsTable.setContextMenu(contextMenu);
        relationsTable.setContextMenu(contextMenu);

        graphCanvas.setOnContextMenuRequested(e -> {
            String hit     = hitTest(e.getX(), e.getY());
            String edgeHit = hit == null ? edgeHitTest(e.getX(), e.getY()) : null;
            if (hit != null) {
                selectedNode = hit; selectedEdge = null;
                syncTableSelection(hit);
                relationsTable.getSelectionModel().clearSelection();
            } else if (edgeHit != null) {
                selectedEdge = edgeHit; selectedNode = null;
                syncEdgeTableSelection(edgeHit);
                contextsTable.getSelectionModel().clearSelection();
            } else {
                selectedNode = selectedEdge = null;
                contextsTable.getSelectionModel().clearSelection();
                relationsTable.getSelectionModel().clearSelection();
            }
            redrawGraph();
            contextMenu.show(graphCanvas, e.getScreenX(), e.getScreenY());
            e.consume();
        });

        contextMenu.setOnShowing(e -> {
            boolean isFc   = contextsTable.getSelectionModel().getSelectedItem() != null;
            boolean isRc   = relationsTable.getSelectionModel().getSelectedItem() != null;
            boolean isEdge = selectedEdge != null && !isFc;
            boolean hasSel = isFc || isRc || isEdge;

            editInEditorItem.setDisable(!hasSel);
            renameItem.setDisable(!hasSel);
            removeItem.setDisable(!hasSel);
            exportItem.setVisible(isFc || (selectedNode != null && !isRc && !isEdge));

            addContextItem.setDisable(false);
            addRelationItem.setVisible(family.getFormalContexts().size() >= 2);

            // Fond vide du canvas : seulement Add Context
/*            if (emptyCanvas) {
                renameItem.setVisible(false);
                removeItem.setVisible(false);
                exportItem.setVisible(false);
                editInEditorItem.setVisible(false);
                addRelationItem.setVisible(false);
                addContextItem.setVisible(true);
            } else {
                renameItem.setVisible(true);
                removeItem.setVisible(true);
                editInEditorItem.setVisible(true);
            }
*/            
        });
    }

    public void setOpenInContextEditor(Consumer<IBinaryContext> callback) {
        this.openInContextEditor = callback;
    }

    private void contextMenuEditInEditor() {
        FormalContext selFc     = contextsTable.getSelectionModel().getSelectedItem();
        RelationalContext selRc = relationsTable.getSelectionModel().getSelectedItem();
        if (selFc == null && selectedNode != null) selFc = family.getFormalContext(selectedNode);
        if (selRc == null && selectedEdge != null) {
            for (RelationalContext rc : family.getRelationalContexts())
                if (rc.getName().equals(selectedEdge)) { selRc = rc; break; }
        }
        IBinaryContext ctx = null;
        if (selFc != null)      ctx = selFc.getContext();
        else if (selRc != null) ctx = selRc.getContext();
        if (ctx == null) return;
        if (openInContextEditor != null) openInContextEditor.accept(ctx);
    }

    private void contextMenuRename() {
        FormalContext selFc     = contextsTable.getSelectionModel().getSelectedItem();
        RelationalContext selRc = relationsTable.getSelectionModel().getSelectedItem();
        if (selFc != null)      onRenameContext();
        else if (selRc != null) promptRenameRelation(selRc);
    }

    private void promptRenameRelation(RelationalContext rc) {
        String newName = promptText(I18n.get("family.dialog.rename.relation"),
            I18n.get("family.dialog.relation.name"), rc.getRelationName());
        if (newName == null || newName.equals(rc.getRelationName())) return;
        if (!family.renameRelationalContext(rc, newName)) {
            showError(I18n.get("family.error.rename"), ""); return;
        }
        recomputeCurvatures(); markModified(); refreshAll();
    }

    private void contextMenuRemove() {
        FormalContext selFc     = contextsTable.getSelectionModel().getSelectedItem();
        RelationalContext selRc = relationsTable.getSelectionModel().getSelectedItem();
        if (selFc != null)      onRemoveContext();
        else if (selRc != null) onRemoveRelation();
    }

    private void contextMenuExport() {
        FormalContext selFc = contextsTable.getSelectionModel().getSelectedItem();
        if (selFc == null && selectedNode != null) selFc = family.getFormalContext(selectedNode);
        if (selFc == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.get("family.menu.export"));
        fc.setInitialFileName(selFc.getName());
        fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("SLF",              "*.slf"),
            new FileChooser.ExtensionFilter("CEX (ConExp)",     "*.cex"),
            new FileChooser.ExtensionFilter("CXT (Burmeister)", "*.cxt"),
            new FileChooser.ExtensionFilter("XML (Galicia)",    "*.xml"),
            new FileChooser.ExtensionFilter("CSV",              "*.csv")
        );
        File f = fc.showSaveDialog(graphCanvas.getScene().getWindow());
        if (f == null) return;
        try {
            new ContextIOService().write(selFc.getContext(), f.toPath(),
                ContextIOService.ContextFormat.fromFile(f));
        } catch (Exception e) {
            showError(I18n.get("family.error.export"), e.getMessage());
        }
    }

    // ── Configuration des tableaux ────────────────────────────────────────────

    private void setupContextsTable() {
        ctxNameCol.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(data.getValue().getName()));
        ctxObjCol.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(
                String.valueOf(data.getValue().getContext().getObjectCount())));
        ctxAttrCol.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(
                String.valueOf(data.getValue().getContext().getAttributeCount())));
        ctxNameCol.setText(I18n.get("family.col.context"));
        ctxObjCol.setText(I18n.get("family.col.objects"));
        ctxAttrCol.setText(I18n.get("family.col.attributes"));

        contextsTable.getSelectionModel().selectedItemProperty().addListener((obs, old, fc) -> {
            if (fc != null) {
                selectedNode = fc.getName(); selectedEdge = null;
                relationsTable.getSelectionModel().clearSelection();
                redrawGraph();
            }
        });
        contextsTable.setRowFactory(tv -> {
            TableRow<FormalContext> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) contextMenuEditInEditor();
            });
            return row;
        });
    }

    private void setupRelationsTable() {
        relNameCol.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(data.getValue().getRelationName()));
        relSourceCol.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(
                family != null ? family.getSourceOf(data.getValue()).getName() : ""));
        relTargetCol.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(
                family != null ? family.getTargetOf(data.getValue()).getName() : ""));
        relOpCol.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(
                data.getValue().getOperator().getName()));
        relNameCol.setText(I18n.get("family.col.relation"));
        relSourceCol.setText(I18n.get("family.col.source"));
        relTargetCol.setText(I18n.get("family.col.target"));
        relOpCol.setText(I18n.get("family.col.operator"));

        relationsTable.getSelectionModel().selectedItemProperty().addListener((obs, old, rc) -> {
            if (rc != null) {
                selectedEdge = rc.getName(); selectedNode = null;
                contextsTable.getSelectionModel().clearSelection();
                redrawGraph();
            }
        });
        relationsTable.setRowFactory(tv -> {
            TableRow<RelationalContext> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) contextMenuEditInEditor();
            });
            return row;
        });
    }

    private void setupCanvas() {
        graphCanvas.widthProperty().bind(graphPane.widthProperty());
        graphCanvas.heightProperty().bind(graphPane.heightProperty());
        graphCanvas.widthProperty().addListener(e -> redrawGraph());
        graphCanvas.heightProperty().addListener(e -> redrawGraph());
        graphCanvas.setOnMousePressed(this::onCanvasMousePressed);
        graphCanvas.setOnMouseDragged(this::onCanvasMouseDragged);
        graphCanvas.setOnMouseReleased(this::onCanvasMouseReleased);
    }

    // ── Chargement ────────────────────────────────────────────────────────────

    public void loadFamily(RCAFamily rcf) {
        this.family = rcf; this.modified = false;
        layoutNodes(); recomputeCurvatures(); refreshAll();
    }

    public void loadFamily(Path path) {
        try {
            RCAFamily rcf = rcfService.read(path);
            this.currentFile = path;
            loadFamily(rcf);
        } catch (Exception e) { showError(I18n.get("family.error.read"), e.getMessage()); }
    }

    public void reloadFamily(RCAFamily rcf) {
        this.family = rcf;
        recomputeCurvatures(); refreshAll();
    }

    private void loadEmpty() {
        family = rcfService.createEmpty(I18n.get("family.new.name"));
        currentFile = null; modified = false;
        nodePositions.clear(); edgeCurvatures.clear();
        refreshAll();
    }

    // ── Layout automatique ────────────────────────────────────────────────────

    private void layoutNodes() {
        nodePositions.clear();
        List<String> names = new ArrayList<>();
        for (FormalContext fc : family.getFormalContexts()) names.add(fc.getName());
        int n = names.size();
        if (n == 0) return;
        double cx = graphCanvas.getWidth() / 2;
        double cy = graphCanvas.getHeight() / 2;
        double radius = Math.min(300, 130 * n / Math.PI);
        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n - Math.PI / 2;
            nodePositions.put(names.get(i),
                new Point2D(cx + radius * Math.cos(angle), cy + radius * Math.sin(angle)));
        }
    }

    // ── Dessin du graphe ──────────────────────────────────────────────────────

    private void redrawGraph() {
        GraphicsContext gc = graphCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, graphCanvas.getWidth(), graphCanvas.getHeight());
        if (family == null) return;

        // Arêtes
        gc.setFont(Font.font(11));
        for (RelationalContext rc : family.getRelationalContexts()) {
            FormalContext src = family.getSourceOf(rc);
            FormalContext tgt = family.getTargetOf(rc);
            if (src == null || tgt == null) continue;
            Point2D ps = nodePositions.get(src.getName());
            Point2D pt = nodePositions.get(tgt.getName());
            if (ps == null || pt == null) continue;

            double curvature = getCurvature(rc);
            boolean selEdge  = rc.getName().equals(selectedEdge);
            gc.setStroke(selEdge ? COLOR_EDGE_SELECTED : COLOR_EDGE);
            gc.setLineWidth(selEdge ? 3.0 : 1.5);

            Point2D[] norm = normalizedPoints(rc);
            Point2D ctrl   = controlPoint(norm[0], norm[1], curvature);
            Point2D mid    = bezierMidpoint(norm[0], norm[1], ctrl);

            drawArrow(gc, ps, pt, ctrl, src.getName(), tgt.getName());

            gc.setFill(selEdge ? COLOR_EDGE_SELECTED : Color.web("#333333"));
            gc.fillText(rc.getRelationName() + "\n[" + rc.getOperator().getName() + "]",
                mid.getX() + 4, mid.getY() - 4);
        }

        // Nœuds (rectangles arrondis)
        gc.setFont(Font.font(12));
        for (FormalContext fc : family.getFormalContexts()) {
            Point2D p = nodePositions.get(fc.getName());
            if (p == null) continue;
            boolean sel = fc.getName().equals(selectedNode);
            double nw = nodeWidth(fc.getName());

            gc.setFill(sel ? COLOR_NODE_SELECTED : COLOR_NODE_FILL);
            gc.setStroke(COLOR_NODE_STROKE);
            gc.setLineWidth(sel ? 2.5 : 1.5);
            gc.fillRoundRect(p.getX() - nw/2, p.getY() - NODE_H/2, nw, NODE_H, NODE_ARC, NODE_ARC);
            gc.strokeRoundRect(p.getX() - nw/2, p.getY() - NODE_H/2, nw, NODE_H, NODE_ARC, NODE_ARC);

            gc.setFill(COLOR_TEXT);
            gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
            gc.fillText(fc.getName(), p.getX(), p.getY() - 6);
            gc.setFont(Font.font(10));
            gc.fillText(fc.getContext().getObjectCount() + " obj / "
                + fc.getContext().getAttributeCount() + " attr", p.getX(), p.getY() + 10);
            gc.setFont(Font.font(12));
        }
    }

    // ── Courbures ─────────────────────────────────────────────────────────────

    private void recomputeCurvatures() {
        edgeCurvatures.clear();
        Map<String, List<RelationalContext>> groups = new LinkedHashMap<>();
        for (RelationalContext rc : family.getRelationalContexts()) {
            FormalContext src = family.getSourceOf(rc);
            FormalContext tgt = family.getTargetOf(rc);
            if (src == null || tgt == null) continue;
            String a = src.getName(), b = tgt.getName();
            String key = a.compareTo(b) <= 0 ? a + "§" + b : b + "§" + a;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(rc);
        }
        for (List<RelationalContext> group : groups.values()) {
            group.sort((a, b) -> a.getName().compareTo(b.getName()));
            int total = group.size();
            if (total == 1) {
                edgeCurvatures.put(group.get(0).getName(), 0.0);
            } else {
                for (int i = 0; i < total; i++) {
                    double sign = (i % 2 == 0) ? 1 : -1;
                    double magnitude = 50.0 * (i / 2 + 1);
                    edgeCurvatures.put(group.get(i).getName(), sign * magnitude);
                }
            }
        }
    }

    private double getCurvature(RelationalContext rc) {
        return edgeCurvatures.getOrDefault(rc.getName(), 0.0);
    }

    private Point2D[] normalizedPoints(RelationalContext rc) {
        FormalContext src = family.getSourceOf(rc);
        FormalContext tgt = family.getTargetOf(rc);
        Point2D ps = nodePositions.get(src.getName());
        Point2D pt = nodePositions.get(tgt.getName());
        if (src.getName().compareTo(tgt.getName()) <= 0) return new Point2D[]{ps, pt};
        else                                              return new Point2D[]{pt, ps};
    }

    private Point2D controlPoint(Point2D from, Point2D to, double curvature) {
        double mx = (from.getX() + to.getX()) / 2;
        double my = (from.getY() + to.getY()) / 2;
        double dx = to.getX() - from.getX(), dy = to.getY() - from.getY();
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1) return new Point2D(mx, my);
        return new Point2D(mx + (-dy / len) * curvature, my + (dx / len) * curvature);
    }

    private Point2D bezierMidpoint(Point2D from, Point2D to, Point2D ctrl) {
        return new Point2D(
            0.25 * from.getX() + 0.5 * ctrl.getX() + 0.25 * to.getX(),
            0.25 * from.getY() + 0.5 * ctrl.getY() + 0.25 * to.getY());
    }

    // ── Intersection segment → bord rectangle ────────────────────────────────

    private Point2D rectIntersect(double fx, double fy, double tx, double ty,
                                   double rx, double ry, String nodeName) {
        double hw = nodeWidth(nodeName) / 2, hh = NODE_H / 2;
        double dx = tx - fx, dy = ty - fy;
        double tMin = Double.MAX_VALUE;
        double ix = rx, iy = ry;

        if (Math.abs(dx) > 0.001) {
            double t = (rx + hw - fx) / dx; double y = fy + t * dy;
            if (t > 0 && Math.abs(y - ry) <= hh && t < tMin) { tMin = t; ix = rx + hw; iy = y; }
            t = (rx - hw - fx) / dx; y = fy + t * dy;
            if (t > 0 && Math.abs(y - ry) <= hh && t < tMin) { tMin = t; ix = rx - hw; iy = y; }
        }
        if (Math.abs(dy) > 0.001) {
            double t = (ry + hh - fy) / dy; double x = fx + t * dx;
            if (t > 0 && Math.abs(x - rx) <= hw && t < tMin) { tMin = t; ix = x; iy = ry + hh; }
            t = (ry - hh - fy) / dy; x = fx + t * dx;
            if (t > 0 && Math.abs(x - rx) <= hw && t < tMin) { tMin = t; ix = x; iy = ry - hh; }
        }
        return new Point2D(ix, iy);
    }

    private void drawArrow(GraphicsContext gc, Point2D from, Point2D to,
                           Point2D ctrl, String srcName, String tgtName) {
        Point2D p1 = rectIntersect(ctrl.getX(), ctrl.getY(),
            from.getX(), from.getY(), from.getX(), from.getY(), srcName);
        Point2D p2 = rectIntersect(ctrl.getX(), ctrl.getY(),
            to.getX(), to.getY(), to.getX(), to.getY(), tgtName);

        double dtx = to.getX() - ctrl.getX(), dty = to.getY() - ctrl.getY();
        double lt = Math.sqrt(dtx * dtx + dty * dty);
        double x2 = p2.getX() - (lt > 0 ? dtx / lt : 0) * 8;
        double y2 = p2.getY() - (lt > 0 ? dty / lt : 0) * 8;

        gc.beginPath();
        gc.moveTo(p1.getX(), p1.getY());
        gc.quadraticCurveTo(ctrl.getX(), ctrl.getY(), x2, y2);
        gc.stroke();

        double angle = Math.atan2(y2 - ctrl.getY(), x2 - ctrl.getX());
        double arrowLen = 10, arrowAngle = Math.PI / 6;
        gc.setFill(gc.getStroke());
        gc.fillPolygon(
            new double[]{x2, x2 - arrowLen*Math.cos(angle-arrowAngle), x2 - arrowLen*Math.cos(angle+arrowAngle)},
            new double[]{y2, y2 - arrowLen*Math.sin(angle-arrowAngle), y2 - arrowLen*Math.sin(angle+arrowAngle)},
            3);
    }

    // ── Interactions souris ───────────────────────────────────────────────────

    private void onCanvasMousePressed(MouseEvent e) {
        String hit = hitTest(e.getX(), e.getY());
        if (hit != null) {
            if (e.getClickCount() == 2) {
                selectedNode = hit; syncTableSelection(hit); redrawGraph();
                contextMenuEditInEditor(); return;
            }
            selectedNode = dragNode = hit; selectedEdge = null;
            Point2D p = nodePositions.get(hit);
            dragOffX = e.getX() - p.getX(); dragOffY = e.getY() - p.getY();
            syncTableSelection(hit);
            relationsTable.getSelectionModel().clearSelection();
        } else {
            String edgeHit = edgeHitTest(e.getX(), e.getY());
            if (edgeHit != null) {
                selectedEdge = edgeHit; selectedNode = null; dragNode = null;
                syncEdgeTableSelection(edgeHit);
                contextsTable.getSelectionModel().clearSelection();
            } else {
                selectedNode = selectedEdge = dragNode = null;
                contextsTable.getSelectionModel().clearSelection();
                relationsTable.getSelectionModel().clearSelection();
                if (e.getClickCount() == 2) onAddContext();
            }
        }
        redrawGraph();
    }

    private void onCanvasMouseDragged(MouseEvent e) {
        if (dragNode != null) {
            nodePositions.put(dragNode, new Point2D(e.getX() - dragOffX, e.getY() - dragOffY));
            redrawGraph();
        }
    }

    private void onCanvasMouseReleased(MouseEvent e) { dragNode = null; }

    private String hitTest(double x, double y) {
        for (Map.Entry<String, Point2D> entry : nodePositions.entrySet()) {
            Point2D p = entry.getValue();
            double nw = nodeWidth(entry.getKey());
            if (Math.abs(x - p.getX()) <= nw / 2 && Math.abs(y - p.getY()) <= NODE_H / 2)
                return entry.getKey();
        }
        return null;
    }

    private void syncTableSelection(String name) {
        for (FormalContext fc : contextsTable.getItems()) {
            if (fc.getName().equals(name)) {
                contextsTable.getSelectionModel().select(fc);
                contextsTable.scrollTo(fc); return;
            }
        }
    }

    private void syncEdgeTableSelection(String edgeName) {
        for (RelationalContext rc : relationsTable.getItems()) {
            if (rc.getName().equals(edgeName)) {
                relationsTable.getSelectionModel().select(rc);
                relationsTable.scrollTo(rc); return;
            }
        }
    }

    private String edgeHitTest(double x, double y) {
        for (RelationalContext rc : family.getRelationalContexts()) {
            FormalContext src = family.getSourceOf(rc);
            FormalContext tgt = family.getTargetOf(rc);
            if (src == null || tgt == null) continue;
            Point2D ps = nodePositions.get(src.getName());
            Point2D pt = nodePositions.get(tgt.getName());
            if (ps == null || pt == null) continue;
            double curvature = getCurvature(rc);
            Point2D[] norm = normalizedPoints(rc);
            Point2D ctrl = controlPoint(norm[0], norm[1], curvature);
            double minDist = Double.MAX_VALUE;
            for (int i = 0; i <= 20; i++) {
                double t = i / 20.0;
                double bx = (1-t)*(1-t)*ps.getX() + 2*(1-t)*t*ctrl.getX() + t*t*pt.getX();
                double by = (1-t)*(1-t)*ps.getY() + 2*(1-t)*t*ctrl.getY() + t*t*pt.getY();
                minDist = Math.min(minDist, Math.sqrt((x-bx)*(x-bx) + (y-by)*(y-by)));
            }
            if (minDist < 8.0) return rc.getName();
        }
        return null;
    }

    // ── Rafraîchissement ──────────────────────────────────────────────────────

    void refreshAll() {
        if (currentFile == null) {
            String name = family != null ? family.getName() : "";
            familyNameLabel.setText(name + (modified ? " *" : ""));
        } else {
            familyNameLabel.setText(
                currentFile.getFileName().toString() + (modified ? " *" : ""));
        }
        if (family != null)
            statsLabel.setText(I18n.get("family.stats",
                family.getFormalContexts().size(),
                family.getRelationalContexts().size()));
        contextsTable.getItems().setAll(family != null ? family.getFormalContexts() : List.of());
        relationsTable.getItems().setAll(family != null ? family.getRelationalContexts() : List.of());
        redrawGraph();
    }

    public void markModified() {
        if (!modified) { modified = true; Platform.runLater(this::refreshAll); }
    }

    // ── Actions toolbar ───────────────────────────────────────────────────────

    @FXML private void onNew() { if (!confirmDiscard()) return; loadEmpty(); }

    @FXML private void onOpen() {
        if (!confirmDiscard()) return;
        FileChooser fc = buildFamilyChooser(I18n.get("family.open.title"));
        File f = fc.showOpenDialog(graphCanvas.getScene().getWindow());
        if (f != null) { loadFamily(f.toPath()); AppPreferences.setLastDirectory(f.getParent()); }
    }

    @FXML private void onSave() {
        if (currentFile == null) { onSaveAs(); return; }
        saveToFile(currentFile);
    }

    @FXML private void onSaveAs() {
        FileChooser fc = buildFamilyChooser(I18n.get("family.saveas.title"));
        fc.getExtensionFilters().clear();
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("RCFT", "*.rcft"),
            new FileChooser.ExtensionFilter("RCFGZ (compressed)", "*.rcfgz"));
        File f = fc.showSaveDialog(graphCanvas.getScene().getWindow());
        if (f != null) {
            currentFile = f.toPath(); saveToFile(currentFile);
            AppPreferences.setLastDirectory(f.getParent());
        }
    }

    private void saveToFile(Path path) {
        try {
            rcfService.write(family, path);
            modified = false; refreshAll();
        } catch (Exception e) { showError(I18n.get("family.error.write"), e.getMessage()); }
    }

    @FXML private void onAddContext() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.get("family.dialog.add.context"));
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(12));

        TextField nameField = new TextField(
            I18n.get("family.default.context.name") + (family.getFormalContexts().size() + 1));
        TextField fileField = new TextField();
        fileField.setPromptText(I18n.get("family.dialog.context.file.prompt"));
        fileField.setEditable(false);
        Button browseBtn = new Button(I18n.get("button.browse"));

        final IBinaryContext[] loadedCtx = {null};
        browseBtn.setOnAction(e -> {
            FileChooser fc = buildFamilyContextChooser();
            File f = fc.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (f != null) {
                try {
                    loadedCtx[0] = new ContextIOService().read(f.toPath());
                    fileField.setText(f.getAbsolutePath());
                    nameField.setText(loadedCtx[0].getName());
                } catch (Exception ex) { showError(I18n.get("family.error.read"), ex.getMessage()); }
            }
        });

        grid.add(new Label(I18n.get("family.dialog.context.name")), 0, 0); grid.add(nameField, 1, 0);
        grid.add(new Label(I18n.get("family.dialog.context.file")), 0, 1); grid.add(fileField, 1, 1);
        grid.add(browseBtn, 2, 1);
        dialog.getDialogPane().setContent(grid);
        Platform.runLater(nameField::requestFocus);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            String name = nameField.getText().trim();
            if (name.isBlank()) return;
            IBinaryContext ctx;
            if (loadedCtx[0] != null) { loadedCtx[0].setName(name); ctx = loadedCtx[0]; }
            else ctx = new fr.lirmm.fca4j.core.BinaryContext(0, 0, name, new BitSetFactory());
            family.addFormalContext(ctx, null);
            double cx = graphCanvas.getWidth() / 2, cy = graphCanvas.getHeight() / 2;
            nodePositions.put(name, new Point2D(
                cx + (Math.random() - 0.5) * 200, cy + (Math.random() - 0.5) * 150));
            recomputeCurvatures(); markModified(); refreshAll();
        });
    }

    private FileChooser buildFamilyContextChooser() {
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.get("family.dialog.context.file"));
        fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("SLF",              "*.slf"),
            new FileChooser.ExtensionFilter("CEX (ConExp)",     "*.cex"),
            new FileChooser.ExtensionFilter("CXT (Burmeister)", "*.cxt"),
            new FileChooser.ExtensionFilter("XML (Galicia)",    "*.xml"),
            new FileChooser.ExtensionFilter("CSV",              "*.csv"),
            new FileChooser.ExtensionFilter(I18n.get("filter.all"), "*.*"));
        return fc;
    }

    @FXML private void onRemoveContext() {
        FormalContext sel = contextsTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showInfo(I18n.get("family.select.context.first")); return; }
        if (!confirmDelete(I18n.get("family.confirm.delete.context", sel.getName()))) return;
        if (!family.deleteFormalContext(sel)) {
            showError(I18n.get("family.error.delete.context.title"),
                I18n.get("family.error.delete.context.detail", sel.getName())); return;
        }
        nodePositions.remove(sel.getName());
        recomputeCurvatures(); markModified(); refreshAll();
    }

    @FXML private void onAddRelation() {
        if (family.getFormalContexts().size() < 2) {
            showInfo(I18n.get("family.need.two.contexts")); return;
        }
        showAddRelationDialog();
    }

    @FXML private void onRemoveRelation() {
        RelationalContext sel = relationsTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showInfo(I18n.get("family.select.relation.first")); return; }
        if (!confirmDelete(I18n.get("family.confirm.delete.relation", sel.getRelationName()))) return;
        if (!family.deleteRelationalContext(sel)) {
            showError(I18n.get("family.error.delete.relation"), ""); return;
        }
        recomputeCurvatures(); markModified(); refreshAll();
    }

    @FXML private void onRenameContext() {
        FormalContext sel = contextsTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showInfo(I18n.get("family.select.context.first")); return; }
        String newName = promptText(I18n.get("family.dialog.rename.context"),
            I18n.get("family.dialog.context.name"), sel.getName());
        if (newName == null || newName.equals(sel.getName())) return;
        Point2D pos = nodePositions.remove(sel.getName());
        if (!family.renameFormalContext(sel, newName)) {
            showError(I18n.get("family.error.rename"), "");
            nodePositions.put(sel.getName(), pos); return;
        }
        nodePositions.put(newName, pos);
        recomputeCurvatures(); markModified(); refreshAll();
    }

    @FXML private void onAutoLayout() { layoutNodes(); redrawGraph(); }

    // ── Dialogue ajout de relation ────────────────────────────────────────────

    private void showAddRelationDialog() {
        List<String> contextNames = new ArrayList<>();
        family.getFormalContexts().forEach(fc -> contextNames.add(fc.getName()));

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.get("family.dialog.add.relation"));
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(12));

     // Générer un nom de relation unique
        String defaultName = generateRelationName();
        TextField nameField = new TextField(defaultName);        ComboBox<String> srcCombo = new ComboBox<>();
        srcCombo.getItems().addAll(contextNames); srcCombo.setValue(contextNames.get(0));
        ComboBox<String> tgtCombo = new ComboBox<>();
        tgtCombo.getItems().addAll(contextNames);
        tgtCombo.setValue(contextNames.size() > 1 ? contextNames.get(1) : contextNames.get(0));
        ComboBox<String> opCombo  = new ComboBox<>();
        opCombo.getItems().addAll("exist", "existForall", "existContains", "equality");
        opCombo.setValue("exist");

        grid.add(new Label(I18n.get("family.dialog.relation.name")), 0, 0); grid.add(nameField, 1, 0);
        grid.add(new Label(I18n.get("family.col.source")), 0, 1);           grid.add(srcCombo, 1, 1);
        grid.add(new Label(I18n.get("family.col.target")), 0, 2);           grid.add(tgtCombo, 1, 2);
        grid.add(new Label(I18n.get("family.col.operator")), 0, 3);         grid.add(opCombo, 1, 3);
        dialog.getDialogPane().setContent(grid);
        javafx.scene.control.ButtonType okButton = dialog.getDialogPane()
        	    .getButtonTypes().get(0); // ButtonType.OK
        	javafx.scene.Node okNode = dialog.getDialogPane().lookupButton(okButton);

        	// Vérification initiale
        	okNode.setDisable(srcCombo.getValue().equals(tgtCombo.getValue()));

        	// Listeners sur les deux combos
        	srcCombo.valueProperty().addListener((obs, old, val) ->
        	    okNode.setDisable(val != null && val.equals(tgtCombo.getValue())));
        	tgtCombo.valueProperty().addListener((obs, old, val) ->
        	    okNode.setDisable(val != null && val.equals(srcCombo.getValue())));
        	
        dialog.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            String relName = nameField.getText().trim();
            String src = srcCombo.getValue(), tgt = tgtCombo.getValue(), op = opCombo.getValue();
            if (relName.isBlank()) return;
            FormalContext srcFc = family.getFormalContext(src);
            FormalContext tgtFc = family.getFormalContext(tgt);
            IBinaryContext rc = new fr.lirmm.fca4j.core.BinaryContext(
                srcFc.getContext().getObjectCount(), tgtFc.getContext().getObjectCount(),
                relName, new BitSetFactory());
            for (int i = 0; i < srcFc.getContext().getObjectCount(); i++)
                rc.addObjectName(srcFc.getContext().getObjectName(i));
            for (int i = 0; i < tgtFc.getContext().getObjectCount(); i++)
                rc.addAttributeName(tgtFc.getContext().getObjectName(i));
            family.addRelationalContext(rc, src, tgt, op);
            recomputeCurvatures(); markModified(); refreshAll();
        });
    }
    private String generateRelationName() {
        String base = I18n.get("family.default.relation.name");
        // Si "relation" n'existe pas encore, le retourner directement
        Set<String> existing = new HashSet<>();
        for (RelationalContext rc : family.getRelationalContexts())
            existing.add(rc.getRelationName());
        if (!existing.contains(base)) return base;
        // Sinon chercher relation1, relation2...
        int i = 1;
        while (existing.contains(base + i)) i++;
        return base + i;
    }
    // ── Accesseurs ────────────────────────────────────────────────────────────

    public RCAFamily getFamily()         { return family; }
    public Path      getCurrentFile()    { return currentFile; }
    public void      openFile(Path path) { loadFamily(path); }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private FileChooser buildFamilyChooser(String title) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("RCFT", "*.rcft"),
            new FileChooser.ExtensionFilter("RCFGZ (compressed)", "*.rcfgz"),
            new FileChooser.ExtensionFilter("RCFAL (JSON)", "*.rcfal"),
            new FileChooser.ExtensionFilter(I18n.get("filter.all"), "*.*"));
        return fc;
    }

    private String promptText(String title, String prompt, String def) {
        TextInputDialog d = new TextInputDialog(def);
        d.setTitle(title); d.setHeaderText(null); d.setContentText(prompt);
        return d.showAndWait().filter(s -> !s.isBlank()).orElse(null);
    }

    private boolean confirmDiscard() {
        if (!modified) return true;
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(I18n.get("editor.confirm.discard.title")); a.setHeaderText(null);
        a.setContentText(I18n.get("editor.confirm.discard.detail"));
        return a.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    private boolean confirmDelete(String msg) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(I18n.get("editor.confirm.delete.title")); a.setHeaderText(null); a.setContentText(msg);
        return a.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }

    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(I18n.get("app.title")); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
}
