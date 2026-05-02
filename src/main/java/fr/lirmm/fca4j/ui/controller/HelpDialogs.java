/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.ui.controller;

import fr.lirmm.fca4j.ui.MainApp;
import fr.lirmm.fca4j.ui.util.I18n;

import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Hyperlink;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Regroupe les dialogues d'aide : About, Raccourcis clavier.
 * Extrait de MainController pour réduire sa taille.
 */
public class HelpDialogs {

    private final Window owner;
    private final Consumer<String> openUrl;

    /**
     * @param owner   fenêtre parente pour la modalité des dialogues
     * @param openUrl callback pour ouvrir une URL dans le navigateur
     */
    public HelpDialogs(Window owner, Consumer<String> openUrl) {
        this.owner = owner;
        this.openUrl = openUrl;
    }

    // ── Dialogue About ───────────────────────────────────────────────────────

    public void showAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(I18n.get("menu.help.about"));
        alert.setHeaderText(MainApp.APP_TITLE + " " + MainApp.APP_VERSION);

        TextFlow content = new TextFlow();
        content.setLineSpacing(4);
        content.setPrefWidth(380);

        List<javafx.scene.Node> nodes = new ArrayList<>();

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
            InputStream logoStream = getClass()
                    .getResourceAsStream("/fr/lirmm/fca4j/ui/icons/fca4j-ui_128x128.png");
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

    // ── Dialogue Raccourcis clavier ───────────────────────────────────────────

    public void showShortcuts() {
        Stage dialog = new Stage();
        dialog.setTitle(I18n.get("menu.help.shortcuts"));
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(owner);

        WebView wv = new WebView();
        wv.getEngine().loadContent(buildShortcutsHtml());
        wv.setPrefSize(520, 480);

        dialog.setScene(new Scene(wv));
        dialog.setResizable(false);
        dialog.show();
    }

    // ── Construction du HTML des raccourcis ───────────────────────────────────

    private String buildShortcutsHtml() {
        boolean isFr = "fr".equals(I18n.getLocale().getLanguage());
        String title   = isFr ? "Raccourcis clavier \u2014 FCA4J UI"   : "Keyboard Shortcuts \u2014 FCA4J UI";
        String secGen  = isFr ? "G\u00e9n\u00e9ral"                    : "General";
        String secEdit = isFr ? "\u00c9diteur de contexte"              : "Context Editor";
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
            + row("Ctrl+O",  isFr ? "Ouvrir un contexte"          : "Open context")
            + row("Ctrl+S",  isFr ? "Enregistrer"                 : "Save")
            + row("F5",      isFr ? "Relancer la commande"         : "Re-run command")
            + row("Ctrl+L",  isFr ? "Effacer la console"           : "Clear console")
            + row("F2",      isFr ? "Aide sur la commande courante" : "Help on current command")
            + row("F1",      isFr ? "Afficher cette aide"          : "Show this help")
            + "</table>"
            + "<h2>" + secEdit + "</h2>"
            + "<table>"
            + row("Ctrl+Z",       isFr ? "Annuler (undo)"                  : "Undo")
            + row("Double-clic",  isFr ? "Renommer objet / attribut"        : "Rename object / attribute")
            + row("Clic cellule", isFr ? "Cocher / d\u00e9cocher"           : "Toggle cell")
            + "</table>"
            + "</body></html>";
    }

    private static String row(String key, String desc) {
        return "<tr><td><kbd>" + key + "</kbd></td><td>" + desc + "</td></tr>";
    }

    // ── Utilitaires ──────────────────────────────────────────────────────────

    private Text text(String s) {
        return new Text(s);
    }

    private Text text(String s, boolean bold) {
        Text t = new Text(s);
        if (bold)
            t.setStyle("-fx-font-weight: bold;");
        return t;
    }

    private Hyperlink hyperlink(String url) {
        Hyperlink h = new Hyperlink(url);
        h.setOnAction(e -> openUrl.accept(url));
        h.setPadding(new javafx.geometry.Insets(0));
        return h;
    }
}
