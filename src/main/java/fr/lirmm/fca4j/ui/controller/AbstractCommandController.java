/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.ui.controller;

import fr.lirmm.fca4j.ui.model.CommandBuilder;
import fr.lirmm.fca4j.ui.model.CommandDescriptor;
import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.I18n;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Classe abstraite regroupant le comportement commun des contrôleurs
 * de commande : gestion du fichier d'entrée (browse, détection de format,
 * ouverture dans l'éditeur), préférences, alertes.
 * <p>
 * Les sous-classes concrètes (LatticeAocController, RuleBasisController,
 * ReduceClarifyController, IrreducibleController, InspectController) doivent :
 * <ul>
 *   <li>Déclarer leurs propres champs FXML spécifiques</li>
 *   <li>Appeler {@link #configureBase} depuis leur méthode {@code configure()}</li>
 *   <li>Implémenter {@link #onRun()}</li>
 *   <li>Implémenter {@link #savePrefs()} et {@link #loadPrefs()}</li>
 * </ul>
 */
public abstract class AbstractCommandController {

    // ── Champs communs (doivent être @FXML dans les sous-classes) ─────────────
    // Les sous-classes doivent déclarer :
    //   @FXML protected TextField inputFileField;
    //   @FXML protected Button editInputButton;

    protected CommandDescriptor descriptor;
    protected Consumer<CommandBuilder> onRun;
    protected Consumer<Path> openInEditor;
    protected Consumer<String> onInputChanged;

    // ── Configuration ────────────────────────────────────────────────────────

    /**
     * Initialisation commune. À appeler depuis le configure() de la sous-classe.
     */
    protected void configureBase(CommandDescriptor desc,
                                  Consumer<CommandBuilder> onRun,
                                  Consumer<Path> openInEditor,
                                  Consumer<String> onInputChanged,
                                  Button editInputButton) {
        this.descriptor = desc;
        this.onRun = onRun;
        this.openInEditor = openInEditor;
        this.onInputChanged = onInputChanged;

        // Bouton "Ouvrir dans l'éditeur"
        if (editInputButton != null) {
            FontIcon editIcon = new FontIcon(Material2AL.EDIT);
            editIcon.setIconSize(14);
            editInputButton.setGraphic(editIcon);
            editInputButton.setText("");
            editInputButton.setTooltip(new Tooltip(I18n.get("btn.open.in.editor")));
        }
    }

    // ── Actions communes ─────────────────────────────────────────────────────

    /**
     * Ouvre le fichier d'entrée dans l'éditeur de contexte.
     */
    protected void editInput(TextField inputFileField) {
        String path = inputFileField.getText().trim();
        if (path.isBlank()) {
            showError(I18n.get("error.no.input.title"),
                      I18n.get("error.no.input.detail"));
            return;
        }
        if (openInEditor != null)
            openInEditor.accept(Path.of(path));
    }

    /**
     * Vérifie que le fichier d'entrée est renseigné.
     * @return true si le champ est non-vide, false sinon (avec alerte affichée)
     */
    protected boolean validateInput(TextField inputFileField) {
        if (inputFileField.getText().isBlank()) {
            showError(I18n.get("error.no.input.title"),
                      I18n.get("error.no.input.detail"));
            return false;
        }
        return true;
    }

    // ── Détection de format ──────────────────────────────────────────────────

    /**
     * Détecte le format d'un fichier de contexte à partir de son extension
     * et met à jour la ComboBox correspondante.
     */
    protected void autoDetectFormat(String filename, ComboBox<String> combo) {
        String lower = filename.toLowerCase();
        if      (lower.endsWith(".cxt")) combo.setValue("CXT");
        else if (lower.endsWith(".slf")) combo.setValue("SLF");
        else if (lower.endsWith(".xml")) combo.setValue("XML");
        else if (lower.endsWith(".cex")) combo.setValue("CEX");
        else if (lower.endsWith(".csv")) combo.setValue("CSV");
        else if (combo.getItems().contains(I18n.get("format.auto")))
            combo.setValue(I18n.get("format.auto"));
        else if (combo.getItems().contains("(auto)"))
            combo.setValue("(auto)");
    }

    // ── FileChooser utilitaires ──────────────────────────────────────────────

    /**
     * Crée un FileChooser pour les fichiers de contexte formel.
     */
    protected FileChooser buildContextChooser(String title) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter(I18n.get("filter.context.all"),
                "*.cxt", "*.slf", "*.cex", "*.xml", "*.csv"),
            new FileChooser.ExtensionFilter(I18n.get("filter.all"), "*.*"));
        return fc;
    }

    // ── Alertes ──────────────────────────────────────────────────────────────

    protected void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    // ── Préférences ──────────────────────────────────────────────────────────

    /**
     * Sauvegarde les préférences spécifiques à la commande.
     * À implémenter dans chaque sous-classe.
     */
    protected abstract void savePrefs();

    /**
     * Charge les préférences spécifiques à la commande.
     * À implémenter dans chaque sous-classe.
     */
    protected abstract void loadPrefs();

    /**
     * Retourne le nom de la commande pour le préfixe des préférences.
     */
    protected String commandName() {
        return descriptor != null ? descriptor.getName() : "UNKNOWN";
    }

    // ── Méthodes à implémenter ───────────────────────────────────────────────

    /**
     * Lance l'exécution de la commande.
     */
    public abstract void onRun();

    /**
     * Définit le fichier d'entrée (appelé depuis MainController).
     */
    public abstract void setInputFile(String path);

    /**
     * Retourne le fichier d'entrée courant.
     */
    public abstract String getInputFile();
}
