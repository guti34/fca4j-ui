/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.ui.controller;

import fr.lirmm.fca4j.ui.service.GraphRenderer;
import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.I18n;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Gère la toolbar du graphe : setup des boutons, ouverture de fichiers DOT,
 * sauvegarde DOT, export SVG/PNG/PDF via GraphViz, et loupe.
 * Extrait de MainController pour réduire sa taille.
 */
public class GraphExporter {

    private final GraphRenderer renderer;
    private final Button btnOpenDot;
    private final Button btnSaveDot;
    private final Button btnExportSvg;
    private final Button btnExportPng;
    private final Button btnExportPdf;
    private final Button btnMagnifier;
    private final Label dotFileLabel;
    private final Consumer<String> console;
    private final WindowProvider windowProvider;

    /**
     * Interface fonctionnelle pour obtenir la Window courante (lazy).
     */
    @FunctionalInterface
    public interface WindowProvider {
        Window get();
    }

    public GraphExporter(GraphRenderer renderer,
                         Button btnOpenDot, Button btnSaveDot,
                         Button btnExportSvg, Button btnExportPng,
                         Button btnExportPdf, Button btnMagnifier,
                         Label dotFileLabel,
                         Consumer<String> console,
                         WindowProvider windowProvider) {
        this.renderer = renderer;
        this.btnOpenDot = btnOpenDot;
        this.btnSaveDot = btnSaveDot;
        this.btnExportSvg = btnExportSvg;
        this.btnExportPng = btnExportPng;
        this.btnExportPdf = btnExportPdf;
        this.btnMagnifier = btnMagnifier;
        this.dotFileLabel = dotFileLabel;
        this.console = console;
        this.windowProvider = windowProvider;
    }

    // ── Setup toolbar ────────────────────────────────────────────────────────

    public void setupToolbar() {
        setBtn(btnOpenDot,   new FontIcon(Material2AL.FOLDER_OPEN),       I18n.get("graph.btn.open.dot"));
        setBtn(btnSaveDot,   new FontIcon(Material2MZ.SAVE),              I18n.get("graph.btn.save.dot"));
        setBtn(btnExportSvg, new FontIcon(Material2AL.IMAGE),             I18n.get("graph.btn.export.svg"));
        setBtn(btnExportPng, new FontIcon(Material2AL.IMAGE),             I18n.get("graph.btn.export.png"));
        setBtn(btnExportPdf, new FontIcon(Material2AL.INSERT_DRIVE_FILE), I18n.get("graph.btn.export.pdf"));
        setBtn(btnMagnifier, new FontIcon(Material2MZ.SEARCH),            I18n.get("graph.btn.magnifier"));
    }

    private void setBtn(Button btn, FontIcon icon, String tooltip) {
        if (btn == null) return;
        icon.setIconSize(20);
        icon.setIconColor(Color.valueOf("#444444"));
        btn.setGraphic(icon);
        btn.setText("");
        btn.setTooltip(new Tooltip(tooltip));
    }

    // ── Activation / désactivation des boutons ───────────────────────────────

    public void enableButtons() {
        btnSaveDot.setDisable(false);
        btnExportSvg.setDisable(false);
        btnExportPng.setDisable(false);
        btnExportPdf.setDisable(false);
        btnMagnifier.setDisable(false);
    }

    public void clearGraph() {
        renderer.clear();
        btnSaveDot.setDisable(true);
        btnExportSvg.setDisable(true);
        btnExportPng.setDisable(true);
        btnExportPdf.setDisable(true);
        btnMagnifier.setDisable(true);
        if (renderer.isMagnifierActive())
            renderer.toggleMagnifier();
        dotFileLabel.setText("");
    }

    // ── Loupe ────────────────────────────────────────────────────────────────

    public void toggleMagnifier() {
        if (renderer.getCurrentDotFile() == null) return;
        renderer.toggleMagnifier();
        btnMagnifier.setStyle(
            renderer.isMagnifierActive() ? "-fx-background-color: #e0e0e0;" : "");
    }

    // ── Save DOT ─────────────────────────────────────────────────────────────

    public void saveDot() {
        Path src = renderer.getCurrentDotFile();
        if (src == null) return;

        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.get("graph.btn.save.dot"));
        fc.setInitialFileName(src.getFileName().toString());
        fc.setInitialDirectory(src.getParent().toFile());
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("DOT", "*.dot"));
        File dest = fc.showSaveDialog(windowProvider.get());
        if (dest == null) return;

        try {
            java.nio.file.Files.copy(src, dest.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            console.accept(I18n.get("graph.saved.dot", dest.getName()));
        } catch (Exception e) {
            console.accept("[Error] " + e.getMessage());
        }
    }

    // ── Export SVG / PNG / PDF ────────────────────────────────────────────────

    public void exportSvg() { export("svg", "*.svg"); }
    public void exportPng() { export("png", "*.png"); }
    public void exportPdf() { export("pdf", "*.pdf"); }

    private void export(String format, String extension) {
        Path src = renderer.getCurrentDotFile();
        if (src == null) return;

        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.get("graph.export." + format));
        fc.setInitialFileName(src.getFileName().toString()
            .replace(".dot", "." + format));
        fc.setInitialDirectory(src.getParent().toFile());
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(format.toUpperCase(), extension));
        File dest = fc.showSaveDialog(windowProvider.get());
        if (dest == null) return;

        String dotExe = AppPreferences.getDotPath();
        ProcessBuilder pb = new ProcessBuilder(
            dotExe, "-T" + format, src.toString(), "-o", dest.getAbsolutePath());
        pb.redirectErrorStream(true);

        CompletableFuture.runAsync(() -> {
            try {
                Process proc = pb.start();
                int exit = proc.waitFor();
                Platform.runLater(() -> {
                    if (exit == 0)
                        console.accept(I18n.get("graph.exported",
                            format.toUpperCase(), dest.getName()));
                    else
                        console.accept("[GraphViz] export " + format
                            + " failed (exit " + exit + ")");
                });
            } catch (Exception e) {
                Platform.runLater(() -> console.accept("[GraphViz] " + e.getMessage()));
            }
        });
    }

    // ── Open DOT ─────────────────────────────────────────────────────────────

    /**
     * Ouvre un fichier DOT via un FileChooser et lance le rendu.
     *
     * @param onRenderStarted callback appelé après le lancement du rendu
     *                         (pour afficher l'overlay, sélectionner l'onglet, etc.)
     * @param onRenderDone    callback appelé quand le rendu est terminé
     *                         (pour cacher l'overlay)
     */
    public void openDot(Runnable onRenderStarted, Runnable onRenderDone) {
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.get("graph.btn.open.dot"));
        fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("GraphViz DOT", "*.dot"),
            new FileChooser.ExtensionFilter(I18n.get("filter.all"), "*.*"));
        File f = fc.showOpenDialog(windowProvider.get());
        if (f == null) return;

        AppPreferences.setLastDirectory(f.getParent());
        Path dot = f.toPath();
        dotFileLabel.setText(dot.getFileName().toString());
        console.accept(I18n.get("console.graphviz.render", dot.getFileName()));
        clearGraph();
        dotFileLabel.setText(dot.getFileName().toString());

        renderer.render(dot).thenRun(() -> Platform.runLater(() -> {
            enableButtons();
            onRenderDone.run();
        })).exceptionally(ex -> {
            Platform.runLater(() -> {
                onRenderDone.run();
                console.accept("[GraphViz] " + ex.getCause().getMessage());
            });
            return null;
        });

        onRenderStarted.run();
    }

    /** Met à jour le label du fichier DOT courant. */
    public void setDotFileLabel(String name) {
        dotFileLabel.setText(name);
    }
}
