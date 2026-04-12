package fr.lirmm.fca4j.ui.controller;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.ui.service.ContextIOService;
import fr.lirmm.fca4j.ui.service.ContextIOService.ContextFormat;
import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.I18n;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;

/**
 * Éditeur de contexte formel binaire — double Canvas virtuel.
 *
 * Deux Canvas côte à côte partagent le même offY :
 *  - objCanvas  : noms des objets (colonne gauche figée)
 *  - mainCanvas : matrice des attributs (zone droite scrollable)
 *
 * Alignement pixel-perfect garanti : les deux Canvas démarrent
 * leurs lignes exactement à Y = HEADER_H.
 * Aucune texture GPU de la taille totale du contexte.
 */
public class ContextEditorController implements Initializable {

    // ── Constantes visuelles ──────────────────────────────────────────────────
    private static final double CELL_H      = 24.0;
    private static final double HEADER_H    = 90.0;   // hauteur zone en-tête (les deux Canvas)
    private static final double OBJ_COL_W   = 154.0;  // largeur colonne objets
    private static final double CHECK_R     =  6.0;
    private static final double COL_MIN_W   = 24.0;
    private static final double COL_MAX_W   = 120.0;

    // Couleurs
    private static final Color C_BG_HEADER  = Color.web("#f0f4f8");
    private static final Color C_BG_ODD     = Color.web("#ffffff");
    private static final Color C_BG_EVEN    = Color.web("#f8f9fa");
    private static final Color C_BG_HOVER   = Color.web("#e8f0fe");
    private static final Color C_GRID       = Color.web("#dee2e6");
    private static final Color C_CHECK_ON   = Color.web("#0047B3");
    private static final Color C_CHECK_OFF  = Color.web("#ced4da");
    private static final Color C_TEXT_HDR   = Color.web("#333333");
    private static final Color C_TEXT_SEL   = Color.web("#0047B3");
    private static final Color C_TEXT_OBJ   = Color.web("#212529");
    private static final Color C_BORDER     = Color.web("#cccccc");

    // ── FXML ──────────────────────────────────────────────────────────────────
    @FXML private Button btnNew, btnOpen, btnSave, btnSaveAs;
    @FXML private Button btnAddObject, btnAddAttr, btnDelObject, btnDelAttr, btnRename;
    @FXML private Label  contextNameLabel, statsLabel, statusLabel, fileNameLabel, separatorLabel;

    // Panes conteneurs des deux Canvas
    @FXML private Pane objCanvasPane;   // colonne gauche objets (largeur fixe)
    @FXML private Pane mainCanvasPane;  // zone droite attributs (HGrow=ALWAYS)
    @FXML private ScrollBar vScrollBar;
    @FXML private ScrollBar hScrollBar;

    // Canvas créés en Java (pas en FXML — taille dynamique)
    private Canvas objCanvas;
    private Canvas mainCanvas;

    // ── État scroll ───────────────────────────────────────────────────────────
    private double offX = 0;   // offset horizontal matrice (pixels)
    private double offY = 0;   // offset vertical partagé   (pixels)

    // ── Layout colonnes ───────────────────────────────────────────────────────
    private double[] colWidths;
    private double[] colX;
    private double   totalWidth;

    // ── Hover ─────────────────────────────────────────────────────────────────
    private int hoverCol = -1;
    private int hoverRow = -1;

    // ── Tooltip attribut ──────────────────────────────────────────────────────
    private final Tooltip attrTooltip = new Tooltip();
    private       int     tooltipCol  = -1;  // colonne dont le tooltip est affiché
    
    // ── Menus contextuels ────────────────────────────────────────────────────
    private ContextMenu attrContextMenu;
    private ContextMenu objContextMenu;

    // ── Services & état ───────────────────────────────────────────────────────
    private final ContextIOService   ioService = new ContextIOService();
    private Runnable                 onLoadStart, onLoadEnd;
    private Consumer<String>         onFileLoadedCallback;
    private Consumer<IBinaryContext> onSaveCallback;
    private IBinaryContext           context;
    private Path                     currentFile;
    private boolean                  modified = false, fromFamily = false;

    // ── Pile Undo ──────────────────────────────────────────────────────────────
    private static final int         UNDO_MAX   = 15;
    private final java.util.Deque<IBinaryContext> undoStack = new java.util.ArrayDeque<>();
    private boolean undoing = false; 

    // ──────────────────────────────────────────────────────────────────────────
    //  INITIALISATION
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupToolbar();
        setupContextMenus();
        setupCanvases();
        loadContext(ioService.createEmpty(I18n.get("editor.new.context.name")));
    }

    private void setupCanvases() {
        // Canvas objets (gauche)
        objCanvas = new Canvas(OBJ_COL_W, 10);
        objCanvasPane.getChildren().add(objCanvas);
        objCanvasPane.widthProperty().addListener((obs, o, n) -> {
            objCanvas.setWidth(n.doubleValue());
            redrawAll();
        });
        objCanvasPane.heightProperty().addListener((obs, o, n) -> {
            objCanvas.setHeight(n.doubleValue());
            updateScrollBarsRange();
            redrawAll();
        });
        objCanvas.setOnMouseClicked(this::onObjCanvasClicked);
        objCanvas.setOnScroll(this::onScroll);

        // Canvas principal (droite)
        mainCanvas = new Canvas(10, 10);
        mainCanvasPane.getChildren().add(mainCanvas);
        mainCanvasPane.widthProperty().addListener((obs, o, n) -> {
            mainCanvas.setWidth(n.doubleValue());
            updateScrollBarsRange();
            redrawAll();
        });
        mainCanvasPane.heightProperty().addListener((obs, o, n) -> {
            mainCanvas.setHeight(n.doubleValue());
            updateScrollBarsRange();
            redrawAll();
        });
        mainCanvas.setOnMouseMoved(this::onMouseMoved);
        mainCanvas.setOnMouseExited(e -> { hoverCol = -1; hoverRow = -1; redrawAll(); });
        mainCanvas.setOnMouseClicked(this::onMainCanvasClicked);
        mainCanvas.setOnScroll(this::onScroll);

        // ScrollBar vertical
        vScrollBar.valueProperty().addListener((obs, o, n) -> {
            offY = n.doubleValue();
            redrawAll();
        });

        // ScrollBar horizontal
        hScrollBar.valueProperty().addListener((obs, o, n) -> {
            offX = n.doubleValue();
            redrawAll();
        });
    }
    // ──────────────────────────────────────────────────────────────────────────
    //  MENUS CONTEXTUELS
    // ──────────────────────────────────────────────────────────────────────────

    private void setupContextMenus() {
        // ── Menu contextuel sur les en-têtes d'attribut ──
        attrContextMenu = new ContextMenu();

        // ── Menu contextuel sur les noms d'objet ──
        objContextMenu = new ContextMenu();
    }

    /** Affiche le menu contextuel attribut sur l'en-tête de colonne. */
    private void showAttrContextMenu(MouseEvent e, int col) {
        attrContextMenu.getItems().clear();
        String attrName = context.getAttributeName(col);

        MenuItem renameItem = new MenuItem(I18n.get("editor.menu.rename.attr"));
        renameItem.setOnAction(ev -> promptRenameAttribute(col));

        MenuItem deleteItem = new MenuItem(I18n.get("editor.menu.delete.attr"));
        deleteItem.setOnAction(ev -> deleteAttr(col));

        MenuItem addItem = new MenuItem(I18n.get("editor.menu.add.attr"));
        addItem.setOnAction(ev -> onAddAttribute());

        attrContextMenu.getItems().addAll(renameItem, deleteItem, new SeparatorMenuItem(), addItem);
        attrContextMenu.show(mainCanvasPane, e.getScreenX(), e.getScreenY());
    }

    /** Affiche le menu contextuel objet sur le nom d'objet. */
    private void showObjContextMenu(MouseEvent e, int row) {
        objContextMenu.getItems().clear();
        String objName = context.getObjectName(row);

        MenuItem renameItem = new MenuItem(I18n.get("editor.menu.rename.object"));
        renameItem.setOnAction(ev -> promptRenameObject(row));

        MenuItem deleteItem = new MenuItem(I18n.get("editor.menu.delete.object"));
        deleteItem.setOnAction(ev -> {
            if (!confirmDelete(I18n.get("editor.confirm.delete.object", objName))) return;
            pushUndo(); context.removeObject(row);
            undoing = true; rebuildView(); undoing = false;
            markModified();
        });

        MenuItem addItem = new MenuItem(I18n.get("editor.menu.add.object"));
        addItem.setOnAction(ev -> onAddObject());

        objContextMenu.getItems().addAll(renameItem, deleteItem, new SeparatorMenuItem(), addItem);
        objContextMenu.show(objCanvasPane, e.getScreenX(), e.getScreenY());
    }

 
    // ──────────────────────────────────────────────────────────────────────────
    //  LAYOUT COLONNES
    // ──────────────────────────────────────────────────────────────────────────

    private void computeColumnLayout() {
        int n = context == null ? 0 : context.getAttributeCount();
        colWidths = new double[n]; colX = new double[n];
        double x = 0;
        for (int a = 0; a < n; a++) {
            double w = context.getAttributeName(a).length() * 6.5 + 12;
            colWidths[a] = Math.max(COL_MIN_W, Math.min(COL_MAX_W, w));
            colX[a] = x; x += colWidths[a];
        }
        totalWidth = x;
    }

    private void updateScrollBarsRange() {
        if (context == null) return;
        int nb   = context.getObjectCount();
        double visH = Math.max(0, mainCanvas.getHeight() - HEADER_H);
        double visW = mainCanvas.getWidth();
        double maxV = Math.max(0, nb * CELL_H - visH);
        double maxH = Math.max(0, totalWidth  - visW);

        vScrollBar.setMin(0); vScrollBar.setMax(maxV);
        vScrollBar.setVisibleAmount(Math.min(visH, nb * CELL_H));
        if (offY > maxV) { offY = maxV; vScrollBar.setValue(offY); }

        hScrollBar.setMin(0); hScrollBar.setMax(maxH);
        hScrollBar.setVisibleAmount(Math.min(visW, totalWidth));
        if (offX > maxH) { offX = maxH; hScrollBar.setValue(offX); }

        vScrollBar.setVisible(maxV > 0);
        hScrollBar.setVisible(maxH > 0);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  RENDU — les deux Canvas utilisent le même offY => alignement garanti
    // ──────────────────────────────────────────────────────────────────────────

    private void redrawAll() {
        redrawObjCanvas();
        redrawMainCanvas();
    }

    /** Canvas gauche : en-tête "Objets" + noms des objets visibles. */
    private void redrawObjCanvas() {
        if (context == null || objCanvas == null) return;
        GraphicsContext gc = objCanvas.getGraphicsContext2D();
        double cw = objCanvas.getWidth(), ch = objCanvas.getHeight();
        gc.clearRect(0, 0, cw, ch);

        int nbObj = context.getObjectCount();

     // Fond en-tête
        gc.setFill(C_BG_HEADER);
        gc.fillRect(0, 0, cw, HEADER_H);

        // Diagonale séparatrice Objets \ Attributs
        gc.setStroke(C_GRID); gc.setLineWidth(0.8);
        gc.strokeLine(0, 0, cw, HEADER_H);

        // "Objects" en bas-gauche de la diagonale
        gc.setFill(C_TEXT_SEL);
        gc.setFont(Font.font("System", FontWeight.BOLD, 14));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.BOTTOM);
        gc.fillText(I18n.get("editor.col.objects"), 6, HEADER_H - 6);

        // "Attributes" en haut-droite de la diagonale
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setTextBaseline(VPos.TOP);
        gc.fillText(I18n.get("editor.col.attributes"), cw - 8, 6);

        // Ligne de séparation bas en-tête
        gc.setStroke(C_GRID); gc.setLineWidth(1.0);
        gc.strokeLine(0, HEADER_H, cw, HEADER_H);
        // Ligne de séparation droite (bordure avec canvas principal)
        gc.setStroke(C_BORDER); gc.setLineWidth(1.0);
        gc.strokeLine(cw - 1, 0, cw - 1, ch);

        if (nbObj == 0) return;

        int firstRow = Math.max(0, (int)(offY / CELL_H));
        int lastRow  = Math.min(nbObj - 1, (int)((offY + ch - HEADER_H) / CELL_H) + 1);

        gc.setFont(Font.font("System", FontWeight.NORMAL, 12));

        for (int o = firstRow; o <= lastRow; o++) {
            double y = HEADER_H + o * CELL_H - offY;

            // Fond de ligne
            gc.setFill(o == hoverRow ? C_BG_HOVER : (o % 2 == 0 ? C_BG_ODD : C_BG_EVEN));
            gc.fillRect(0, y, cw, CELL_H);

            // Séparateur horizontal
            gc.setStroke(C_GRID); gc.setLineWidth(0.5);
            gc.strokeLine(0, y + CELL_H, cw, y + CELL_H);

            // Nom de l'objet (tronqué si nécessaire)
            String name = context.getObjectName(o);
            gc.setFill(C_TEXT_OBJ);
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setTextBaseline(VPos.CENTER);
            gc.fillText(truncate(name, cw - 8), 6, y + CELL_H / 2);
        }
    }

    /** Canvas droit : en-têtes attributs + cellules booléennes. */
    private void redrawMainCanvas() {
        if (context == null || mainCanvas == null) return;
        GraphicsContext gc = mainCanvas.getGraphicsContext2D();
        double cw = mainCanvas.getWidth(), ch = mainCanvas.getHeight();
        gc.clearRect(0, 0, cw, ch);

        int nbObj  = context.getObjectCount();
        int nbAttr = context.getAttributeCount();

        if (nbObj == 0 || nbAttr == 0 || colWidths == null) {
            gc.setFill(Color.web("#999")); gc.setFont(Font.font("System", 13));
            gc.setTextAlign(TextAlignment.CENTER); gc.setTextBaseline(VPos.CENTER);
            gc.fillText(I18n.get("editor.empty"), cw / 2, ch / 2);
            return;
        }

        int firstRow = Math.max(0, (int)(offY / CELL_H));
        int lastRow  = Math.min(nbObj - 1, (int)((offY + ch - HEADER_H) / CELL_H) + 1);
        int firstCol = colAtWorldX(offX);
        int lastCol  = Math.min(nbAttr - 1, colAtWorldX(offX + cw) + 1);

        // Fond stripes
        for (int o = firstRow; o <= lastRow; o++) {
            double y = HEADER_H + o * CELL_H - offY;
            gc.setFill(o == hoverRow ? C_BG_HOVER : (o % 2 == 0 ? C_BG_ODD : C_BG_EVEN));
            gc.fillRect(0, y, cw, CELL_H);
        }

        // Grille horizontale
        gc.setStroke(C_GRID); gc.setLineWidth(0.5);
        for (int o = firstRow; o <= lastRow + 1; o++)
            gc.strokeLine(0, HEADER_H + o * CELL_H - offY, cw, HEADER_H + o * CELL_H - offY);

        // Colonnes : grille + cellules
        for (int a = firstCol; a <= lastCol; a++) {
            double cx = colX[a] - offX, cw2 = colWidths[a];
            gc.setStroke(C_GRID); gc.setLineWidth(0.5);
            gc.strokeLine(cx,       HEADER_H, cx,       ch);
            gc.strokeLine(cx + cw2, HEADER_H, cx + cw2, ch);
            for (int o = firstRow; o <= lastRow; o++)
                drawCell(gc, cx, HEADER_H + o * CELL_H - offY, cw2, CELL_H,
                         context.get(o, a), o == hoverRow && a == hoverCol);
        }

        // En-têtes (dessinés en dernier, fond sticky)
        gc.setFill(C_BG_HEADER); gc.fillRect(0, 0, cw, HEADER_H);
        gc.setStroke(C_GRID); gc.setLineWidth(1.0);
        gc.strokeLine(0, HEADER_H, cw, HEADER_H);

        gc.setFont(Font.font("System", FontWeight.NORMAL, 11));
        for (int a = firstCol; a <= lastCol; a++) {
            double cx = colX[a] - offX, cw2 = colWidths[a];
            if (a == hoverCol) { gc.setFill(C_BG_HOVER); gc.fillRect(cx, 0, cw2, HEADER_H - 1); }
            gc.setStroke(C_GRID); gc.setLineWidth(0.5);
            gc.strokeLine(cx + cw2, 0, cx + cw2, HEADER_H);
            gc.save();
            gc.translate(cx + cw2 / 2.0, HEADER_H - 6);
            gc.rotate(-45);
            gc.setFill(a == hoverCol ? C_TEXT_SEL : C_TEXT_HDR);
            gc.setTextAlign(TextAlignment.LEFT); gc.setTextBaseline(VPos.BOTTOM);
            gc.fillText(context.getAttributeName(a), 0, 0);
            gc.restore();
        }
    }

    private void drawCell(GraphicsContext gc, double x, double y,
                          double cw, double ch, boolean checked, boolean hover) {
        double cx = x + cw / 2.0, cy = y + ch / 2.0;
        if (checked) {
            gc.setFill(C_CHECK_ON);
            gc.fillOval(cx - CHECK_R, cy - CHECK_R, CHECK_R * 2, CHECK_R * 2);
            gc.setStroke(Color.WHITE); gc.setLineWidth(1.8);
            gc.strokeLine(cx - CHECK_R * 0.5, cy,
                          cx - CHECK_R * 0.1, cy + CHECK_R * 0.45);
            gc.strokeLine(cx - CHECK_R * 0.1, cy + CHECK_R * 0.45,
                          cx + CHECK_R * 0.5, cy - CHECK_R * 0.4);
        } else {
            gc.setStroke(hover ? C_CHECK_ON : C_CHECK_OFF);
            gc.setLineWidth(1.3);
            gc.strokeOval(cx - CHECK_R, cy - CHECK_R, CHECK_R * 2, CHECK_R * 2);
        }
    }

    /** Tronque un texte pour qu'il tienne dans maxWidth pixels. */
    private String truncate(String text, double maxWidth) {
        if (text == null) return "";
        // Estimation grossière : ~7px par caractère en police 12
        int maxChars = (int)(maxWidth / 7.0);
        if (text.length() <= maxChars) return text;
        return text.substring(0, Math.max(0, maxChars - 1)) + "…";
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  INTERACTIONS
    // ──────────────────────────────────────────────────────────────────────────

    private void onMouseMoved(MouseEvent e) {
        int c = xToCol(e.getX()), r = yToRow(e.getY());
        boolean inHeader = e.getY() < HEADER_H;

        // ── Tooltip sur l'en-tête d'attribut ─────────────────────────────────
        if (inHeader && c >= 0 && c < context.getAttributeCount()) {
            if (c != tooltipCol) {
                tooltipCol = c;
                String fullName = context.getAttributeName(c);
                attrTooltip.setText(fullName);
                // Position : juste sous le curseur
                attrTooltip.show(mainCanvasPane,
                    e.getScreenX() + 12,
                    e.getScreenY() + 16);
            }
        } else {
            attrTooltip.hide();
            tooltipCol = -1;
        }

        // ── Coordonnées dans la barre de statut ───────────────────────────────
        if (r >= 0 && c >= 0) {
            statusLabel.setText(
                context.getObjectName(r) + "  /  " + context.getAttributeName(c)
                + "  [obj " + (r + 1) + ", attr " + (c + 1) + "]"
            );
        } else if (inHeader && c >= 0) {
            statusLabel.setText(context.getAttributeName(c)
                + "  [attr " + (c + 1) + "]");
        } else {
            statusLabel.setText("");
        }

        if (c != hoverCol || r != hoverRow) { hoverCol = c; hoverRow = r; redrawAll(); }
    }

    private void onMainCanvasClicked(MouseEvent e) {
        int c = xToCol(e.getX()), r = yToRow(e.getY());
        // Clic droit sur en-tête → menu contextuel attribut
        if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY && e.getY() < HEADER_H && c >= 0) {
            showAttrContextMenu(e, c); return;
        }
        // Double-clic sur en-tête → renommer attribut
        if (e.getClickCount() == 2 && e.getY() < HEADER_H) {
            if (c >= 0) promptRenameAttribute(c); return;
        }
        // Clic sur cellule → toggle
        if (r >= 0 && c >= 0) { pushUndo(); context.set(r, c, !context.get(r, c)); markModified(); redrawAll(); }
    }
    private void onObjCanvasClicked(MouseEvent e) {
        int r = yToRow(e.getY());
        // Clic droit → menu contextuel objet
        if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY && r >= 0) {
            showObjContextMenu(e, r); return;
        }
        // Double-clic → renommer objet
        if (e.getClickCount() == 2 && r >= 0) promptRenameObject(r);
        if (r >= 0) { hoverRow = r; redrawAll(); }
    }
    private void onScroll(ScrollEvent e) {
        double nv = Math.max(0, Math.min(vScrollBar.getMax(), offY - e.getDeltaY()));
        vScrollBar.setValue(nv); e.consume();
    }

    private int xToCol(double px) {
        double wx = px + offX;
        if (colWidths == null) return -1;
        for (int a = 0; a < colWidths.length; a++)
            if (wx >= colX[a] && wx < colX[a] + colWidths[a]) return a;
        return -1;
    }

    private int yToRow(double py) {
        if (py < HEADER_H) return -1;
        int r = (int)((py - HEADER_H + offY) / CELL_H);
        if (context == null || r < 0 || r >= context.getObjectCount()) return -1;
        return r;
    }

    private int colAtWorldX(double wx) {
        if (colWidths == null || colWidths.length == 0) return 0;
        for (int a = colWidths.length - 1; a >= 0; a--)
            if (colX[a] <= wx) return a;
        return 0;
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  CHARGEMENT / RECONSTRUCTION
    // ──────────────────────────────────────────────────────────────────────────

    public void setOnLoadCallbacks(Runnable start, Runnable end) { onLoadStart = start; onLoadEnd = end; }

    public void loadContext(IBinaryContext ctx) {
        context = ctx; modified = false;
        if (onLoadStart != null) onLoadStart.run();
        java.util.concurrent.CompletableFuture.runAsync(() -> Platform.runLater(() -> {
            rebuildView(); updateLabels(); updateSaveButton();
            if (onLoadEnd != null) onLoadEnd.run();
        }));
    }

    public void loadContextFromFamily(IBinaryContext ctx, Consumer<IBinaryContext> cb) {
        fromFamily = true; currentFile = null; onSaveCallback = cb;
        loadContext(ctx.clone()); updateSaveButton();
    }
    private void rebuildView() {
        offX = 0; offY = 0; hoverCol = -1; hoverRow = -1;
        if(!undoing)undoStack.clear();  
        computeColumnLayout();
        // Adapter les Canvas à la taille visible actuelle
        if (objCanvasPane.getWidth()   > 0) objCanvas.setWidth(objCanvasPane.getWidth());
        if (objCanvasPane.getHeight()  > 0) objCanvas.setHeight(objCanvasPane.getHeight());
        if (mainCanvasPane.getWidth()  > 0) mainCanvas.setWidth(mainCanvasPane.getWidth());
        if (mainCanvasPane.getHeight() > 0) mainCanvas.setHeight(mainCanvasPane.getHeight());
        updateScrollBarsRange();
        vScrollBar.setValue(0); hScrollBar.setValue(0);
        redrawAll();
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  TOOLBAR
    // ──────────────────────────────────────────────────────────────────────────

    private void setupToolbar() {
        setBtn(btnNew,       new FontIcon(Material2AL.FIBER_NEW),               I18n.get("editor.tooltip.new"));
        setBtn(btnOpen,      new FontIcon(Material2AL.FOLDER_OPEN),             I18n.get("editor.tooltip.open"));
        setBtn(btnSave,      new FontIcon(Material2MZ.SAVE),                    I18n.get("editor.tooltip.save"));
        setBtn(btnSaveAs,    new FontIcon(Material2MZ.SAVE_ALT),                I18n.get("editor.tooltip.saveas"));
        setBtn(btnAddObject, new FontIcon(Material2AL.ADD_BOX),                 I18n.get("editor.tooltip.add.object"));
        setBtn(btnDelObject, new FontIcon(Material2AL.CHECK_BOX_OUTLINE_BLANK), I18n.get("editor.tooltip.del.object"));
        setBtn(btnAddAttr,   new FontIcon(Material2AL.ADD_CIRCLE),              I18n.get("editor.tooltip.add.attr"));
        setBtn(btnDelAttr,   new FontIcon(Material2MZ.REMOVE_CIRCLE_OUTLINE),   I18n.get("editor.tooltip.del.attr"));
        setBtn(btnRename,    new FontIcon(Material2AL.EDIT),                    I18n.get("editor.tooltip.rename"));
    }

    private void setBtn(Button b, FontIcon icon, String tip) {
        icon.setIconSize(20); icon.setIconColor(javafx.scene.paint.Color.valueOf("#333333"));
        b.setGraphic(icon); b.setTooltip(new Tooltip(tip));
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  ACTIONS FXML
    // ──────────────────────────────────────────────────────────────────────────

    @FXML public void onNewContext() {
        if (!confirmDiscardChanges()) return;
        if (!confirmLeaveFamily()) return;

        fromFamily = false; onSaveCallback = null;
        String n = promptText(I18n.get("editor.dialog.new.title"), I18n.get("editor.dialog.new.prompt"),
                              I18n.get("editor.new.context.name"), true);
        if (n == null) return;
        loadContext(ioService.createEmpty(n));
        currentFile = null; fileNameLabel.setText("");
        separatorLabel.setVisible(false); separatorLabel.setManaged(false);
        statusLabel.setText("");
    }

    @FXML public void onOpen() {
        if (!confirmDiscardChanges()) return;
        if (!confirmLeaveFamily()) return;

        fromFamily = false; onSaveCallback = null;
        FileChooser fc = buildOpenFC(I18n.get("editor.open.title"));
        File f = fc.showOpenDialog(mainCanvasPane.getScene().getWindow());
        if (f == null) return;
        Path path = f.toPath(); AppPreferences.setLastDirectory(f.getParent());
        if (onLoadStart != null) onLoadStart.run();
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try { return ioService.read(path); } catch (Exception e) { throw new RuntimeException(e); }
        }).thenAccept(ctx -> Platform.runLater(() -> {
            if (onLoadEnd != null) onLoadEnd.run();
            loadContext(ctx); currentFile = path;
            statusLabel.setText(I18n.get("editor.status.loaded", f.getName()));
            if (onFileLoadedCallback != null) onFileLoadedCallback.accept(path.toString());
        })).exceptionally(ex -> {
            Platform.runLater(() -> {
                if (onLoadEnd != null) onLoadEnd.run();
                showError(I18n.get("editor.error.read.title"), ex.getCause().getMessage());
            }); return null;
        });
    }

    @FXML public void onSave() {
        if (fromFamily) {
            modified = false; updateLabels();
            if (onSaveCallback != null) {
                onSaveCallback.accept(context); onSaveCallback = null;
                fromFamily = false; updateSaveButton();
            }
            loadContext(ioService.createEmpty(I18n.get("editor.new.context.name")));
            currentFile = null; statusLabel.setText(""); return;
        }
        if (currentFile == null) { onSaveAs(); return; }
        ContextFormat fmt = ContextFormat.fromFile(currentFile.toFile());
        if (fmt == ContextFormat.CSV) { String s = askCsvSep(); if (s == null) return; ioService.setSeparator(s.charAt(0)); }
        saveToFile(currentFile, fmt);
    }

    @FXML private void onSaveAs() {
        Consumer<IBinaryContext> saved = onSaveCallback; onSaveCallback = null;
        FileChooser fc = buildSaveFC(I18n.get("editor.saveas.title"));
        File f = fc.showSaveDialog(mainCanvasPane.getScene().getWindow());
        if (f == null) { onSaveCallback = saved; return; }
        currentFile = f.toPath();
        ContextFormat fmt = ContextFormat.fromFile(f);
        if (fmt == ContextFormat.CSV) { String s = askCsvSep(); if (s == null) { onSaveCallback = saved; return; } ioService.setSeparator(s.charAt(0)); }
        saveToFile(currentFile, fmt); onSaveCallback = saved;
        AppPreferences.setLastDirectory(f.getParent());
    }

    private void saveToFile(Path path, ContextFormat fmt) {
        try {
            ioService.write(context, path, fmt); modified = false; currentFile = path;
            updateLabels(); statusLabel.setText(I18n.get("editor.status.saved", path.getFileName()));
            AppPreferences.setLastDirectory(path.getParent().toString());
            if (onFileLoadedCallback != null) onFileLoadedCallback.accept(path.toString());
        } catch (Exception e) { showError(I18n.get("editor.error.write.title"), e.getMessage()); }
    }

    @FXML private void onAddObject() {
        String n = promptText(I18n.get("editor.dialog.add.object.title"), I18n.get("editor.dialog.add.object.prompt"),
                              I18n.get("editor.default.object.name") + (context.getObjectCount() + 1), true);
        if (n == null) return;
        pushUndo(); context.addObject(n, ioService.getFactory().createSet());
        undoing=true;
        rebuildView(); 
        undoing=false;
        markModified();
        Platform.runLater(() -> vScrollBar.setValue(vScrollBar.getMax()));
    }

    @FXML private void onAddAttribute() {
        String n = promptText(I18n.get("editor.dialog.add.attr.title"), I18n.get("editor.dialog.add.attr.prompt", true),
                              I18n.get("editor.default.attr.name") + (context.getAttributeCount() + 1), true);
        if (n == null) return;
        pushUndo(); context.addAttribute(n, ioService.getFactory().createSet());
        undoing=true;
        rebuildView(); 
        undoing=false;
        markModified();
        Platform.runLater(() -> hScrollBar.setValue(hScrollBar.getMax()));
    }

    @FXML private void onRemoveObject() {
        if (context.getObjectCount() == 0) { showInfo(I18n.get("editor.select.object.first")); return; }
        ChoiceDialog<String> d = new ChoiceDialog<>(context.getObjectName(0),
            IntStream.range(0, context.getObjectCount()).mapToObj(context::getObjectName).collect(Collectors.toList()));
        d.setTitle(I18n.get("editor.dialog.del.object.title"));
        d.setHeaderText(null);
        d.setContentText(I18n.get("editor.dialog.del.object.prompt"));
        d.showAndWait().ifPresent(name -> {
            int idx = IntStream.range(0, context.getObjectCount())
                .filter(i -> context.getObjectName(i).equals(name)).findFirst().orElse(-1);
            if (idx >= 0) {
                if (!confirmDelete(I18n.get("editor.confirm.delete.object", name))) return;
                pushUndo(); context.removeObject(idx);
                undoing = true; rebuildView(); undoing = false;
                markModified();
            }
        });
    }
    @FXML private void onRemoveAttribute() {
        if (context.getAttributeCount() == 0) { showInfo(I18n.get("editor.select.attr.first")); return; }
        ChoiceDialog<String> d = new ChoiceDialog<>(context.getAttributeName(0),
            IntStream.range(0, context.getAttributeCount()).mapToObj(context::getAttributeName).collect(Collectors.toList()));
        d.setTitle(I18n.get("editor.dialog.del.attr.title")); d.setHeaderText(null);
        d.setContentText(I18n.get("editor.dialog.del.attr.prompt"));
        d.showAndWait().ifPresent(name -> { int idx = context.getAttributeIndex(name); if (idx >= 0) deleteAttr(idx); });
    }

    private void deleteAttr(int idx) {
        if (!confirmDelete(I18n.get("editor.confirm.delete.attr", context.getAttributeName(idx)))) return;
        pushUndo(); 
        context.removeAttribute(idx); 
        undoing=true;
        rebuildView(); 
        undoing=false;
        markModified();
    }

    @FXML private void onRenameContext() {
        String n = promptText(I18n.get("editor.dialog.rename.context"), I18n.get("editor.dialog.name.prompt"),
                              context.getName() != null ? context.getName() : "", true);
        if (n == null) return; context.setName(n); updateLabels(); markModified();
    }

    @FXML private void onRename() {
        if (hoverRow >= 0) { promptRenameObject(hoverRow); return; }
        if (context.getAttributeCount() == 0) return;
        ChoiceDialog<String> d = new ChoiceDialog<>(context.getAttributeName(0),
            IntStream.range(0, context.getAttributeCount()).mapToObj(context::getAttributeName).collect(Collectors.toList()));
        d.setTitle(I18n.get("editor.dialog.rename.attr")); d.setHeaderText(null);
        d.setContentText(I18n.get("editor.dialog.name.prompt"));
        d.showAndWait().ifPresent(n -> promptRenameAttribute(context.getAttributeIndex(n)));
    }

    private void promptRenameObject(int idx) {
        if (idx < 0 || idx >= context.getObjectCount()) return;
        String n = promptText(I18n.get("editor.dialog.rename.object"), I18n.get("editor.dialog.name.prompt"),
                              context.getObjectName(idx), true);
        if (n == null) return;
        pushUndo(); context.setObjectName(idx, n); redrawObjCanvas(); markModified();
    }

    private void promptRenameAttribute(int idx) {
        if (idx < 0 || idx >= context.getAttributeCount()) return;
        String n = promptText(I18n.get("editor.dialog.rename.attr"), I18n.get("editor.dialog.name.prompt"),
                              context.getAttributeName(idx), true);
        if (n == null) return;
        pushUndo(); context.setAttributeName(idx, n); computeColumnLayout(); updateScrollBarsRange(); redrawMainCanvas(); markModified();
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  LABELS & STATE
    // ──────────────────────────────────────────────────────────────────────────

    private void updateLabels() {
        if (currentFile != null) {
            fileNameLabel.setText(currentFile.getFileName().toString());
            separatorLabel.setVisible(true); separatorLabel.setManaged(true);
        } else {
            fileNameLabel.setText(""); separatorLabel.setVisible(false); separatorLabel.setManaged(false);
        }
        String name = (context.getName() != null && !context.getName().isBlank())
            ? context.getName() : I18n.get("editor.new.context.name");
        contextNameLabel.setText(name + (modified ? " *" : ""));
        contextNameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #0047B3; -fx-cursor: hand;");
        statsLabel.setText(I18n.get("editor.stats", context.getObjectCount(), context.getAttributeCount()));
        contextNameLabel.setOnMouseClicked(e -> { if (e.getClickCount() == 2) onRenameContext(); });
    }

    private void markModified() { if (!modified) { modified = true; Platform.runLater(this::updateLabels); } }

    private void updateSaveButton() {
        if (fromFamily) {
            FontIcon ic = new FontIcon(Material2AL.ARROW_BACK);
            ic.setIconSize(20); ic.setIconColor(javafx.scene.paint.Color.valueOf("#0047B3"));
            btnSave.setGraphic(ic); btnSave.setTooltip(new Tooltip(I18n.get("editor.tooltip.save.to.family")));
            btnSave.setStyle("-fx-background-color: #e8f0fe; -fx-border-color: #0047B3; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;");
        } else {
            FontIcon ic = new FontIcon(Material2MZ.SAVE);
            ic.setIconSize(20); ic.setIconColor(javafx.scene.paint.Color.valueOf("#333333"));
            btnSave.setGraphic(ic); btnSave.setTooltip(new Tooltip(I18n.get("editor.tooltip.save"))); btnSave.setStyle("");
        }
    }
    public boolean isFromFamily() {
    	return fromFamily;
    }
    // ──────────────────────────────────────────────────────────────────────────
    //  ACCESSEURS PUBLICS
    // ──────────────────────────────────────────────────────────────────────────

    public IBinaryContext getContext() { return context; }
    public void setOnFileLoaded(Consumer<String> cb) { onFileLoadedCallback = cb; }
    public void openFile(Path path) { openFile(path, "COMMA"); }

    public void openFile(Path path, String separator) {
        if (!"COMMA".equals(separator)) {
            char sep = switch (separator) { case "SEMICOLON" -> ';'; case "TAB" -> '\t'; default -> ','; };
            ioService.setSeparator(sep);
        }
        if (onLoadStart != null) onLoadStart.run();
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try { return ioService.read(path); } catch (Exception e) { throw new RuntimeException(e); }
        }).thenAccept(ctx -> Platform.runLater(() -> {
            if (onLoadEnd != null) onLoadEnd.run();
            loadContext(ctx); currentFile = path;
            statusLabel.setText(I18n.get("editor.status.loaded", path.getFileName()));
            if (onFileLoadedCallback != null) onFileLoadedCallback.accept(path.toString());
        })).exceptionally(ex -> {
            Platform.runLater(() -> {
                if (onLoadEnd != null) onLoadEnd.run();
                showError(I18n.get("editor.error.read.title"), ex.getCause().getMessage());
            }); return null;
        });
    }

    public boolean confirmDiscardChanges() {
        if (!modified) return true;
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(I18n.get("editor.confirm.discard.title")); a.setHeaderText(null);
        a.setContentText(I18n.get("editor.confirm.discard.detail"));
        return a.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }
    /** Avertit l'utilisateur s'il est en mode famille et lui demande confirmation. */
    private boolean confirmLeaveFamily() {
        if (!fromFamily) return true;
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(I18n.get("editor.confirm.leave.family.title"));
        a.setHeaderText(null);
        a.setContentText(I18n.get("editor.confirm.leave.family.detail"));
        return a.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }
    // ──────────────────────────────────────────────────────────────────────────
    //  UTILITAIRES
    // ──────────────────────────────────────────────────────────────────────────

    // ── Undo ─────────────────────────────────────────────────────────────────

    /** Sauvegarde un snapshot du contexte avant une opération destructive. */
    private void pushUndo() {
        undoStack.push(context.clone());
        if (undoStack.size() > UNDO_MAX) undoStack.pollLast();
    }
    /** Restaure le dernier snapshot (Ctrl+Z). */
    public void undo() {
        if (undoStack.isEmpty()) return;
        IBinaryContext snapshot = undoStack.pop();
        context = snapshot;
        undoing = true; rebuildView(); undoing = false;
        markModified();
    }
    public boolean canUndo() { return !undoStack.isEmpty(); }

    private boolean confirmDelete(String msg) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(I18n.get("editor.confirm.delete.title")); a.setHeaderText(null); a.setContentText(msg);
        return a.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    private void showError(String t, String m) {
        Alert a = new Alert(Alert.AlertType.ERROR); a.setTitle(t); a.setHeaderText(null); a.setContentText(m); a.showAndWait();
    }

    private void showInfo(String m) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(I18n.get("app.title")); a.setHeaderText(null); a.setContentText(m); a.showAndWait();
    }

    private String askCsvSep() {
        ChoiceDialog<String> d = new ChoiceDialog<>(I18n.get("separator.comma"),
            I18n.get("separator.comma"), I18n.get("separator.semicolon"), I18n.get("separator.tab"));
        d.setTitle(I18n.get("editor.csv.separator.title")); d.setHeaderText(null);
        d.setContentText(I18n.get("editor.csv.separator.prompt"));
        return d.showAndWait().map(c -> {
            if (c.equals(I18n.get("separator.comma")))     return ",";
            if (c.equals(I18n.get("separator.semicolon"))) return ";";
            if (c.equals(I18n.get("separator.tab")))       return "\t";
            return ",";
        }).orElse(null);
    }

    private String promptText(String title, String prompt, String def, boolean sanitize) {
        TextInputDialog d = new TextInputDialog(def);
        d.setTitle(title); d.setHeaderText(null); d.setContentText(prompt);
        if (sanitize) d.getEditor().textProperty().addListener((obs, o, v) -> {
            if (v.contains(" ")) d.getEditor().setText(v.replace(" ", "_"));
        });
        return d.showAndWait().filter(s -> !s.isBlank()).orElse(null);
    }

    private String promptText(String title, String prompt, String def) { return promptText(title, prompt, def, false); }

    private FileChooser buildOpenFC(String title) {
        FileChooser fc = new FileChooser(); fc.setTitle(title);
        fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter(I18n.get("filter.context.all"),
                "*.cxt", "*.slf", "*.cex", "*.xml", "*.csv"),
            new FileChooser.ExtensionFilter(I18n.get("filter.all"), "*.*"));
        return fc;
    }

    private FileChooser buildSaveFC(String title) {
        FileChooser fc = new FileChooser(); fc.setTitle(title);
        fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("CXT (Burmeister)", "*.cxt"),
            new FileChooser.ExtensionFilter("SLF (HTK)",        "*.slf"),
            new FileChooser.ExtensionFilter("CEX (ConExp)",     "*.cex"),
            new FileChooser.ExtensionFilter("XML (Galicia)",    "*.xml"),
            new FileChooser.ExtensionFilter("CSV",              "*.csv"),
            new FileChooser.ExtensionFilter(I18n.get("filter.all"), "*.*"));
        return fc;
    }}
