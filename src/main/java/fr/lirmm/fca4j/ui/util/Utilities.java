package fr.lirmm.fca4j.ui.util;

import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;

public class Utilities {
    /**
     * Si path est relatif, le résoudre par rapport au dossier du fichier d'entrée.
     */
    public static String resolveOutput(String outputPath, TextField inputFileField) {
        if (outputPath == null || outputPath.isBlank()) return outputPath;
        java.nio.file.Path p = java.nio.file.Path.of(outputPath);
        if (p.isAbsolute()) return outputPath;
        String input = inputFileField.getText().trim();
        if (input.isBlank()) return outputPath;
        java.nio.file.Path inputDir = java.nio.file.Path.of(input).getParent();
        if (inputDir == null) return outputPath;
        return inputDir.resolve(p).toString();
    }
    /** Remplace les espaces (et caractères non valides) par des underscores. */
    public static String sanitizeName(String name) {
        if (name == null) return "";
        return name.trim().replaceAll("\\s+", "_");
    }
    /**
     * Installe un tooltip dynamique sur un TextField qui affiche
     * le chemin complet au survol. Se met à jour automatiquement.
     */
    public static void bindPathTooltip(TextField field) {
        Tooltip tooltip = new Tooltip();
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(600);
        // Afficher seulement si le champ n'est pas vide
        field.textProperty().addListener((obs, old, val) -> {
            if (val == null || val.isBlank()) {
                Tooltip.uninstall(field, tooltip);
            } else {
                tooltip.setText(val);
                Tooltip.install(field, tooltip);
            }
        });
        // Initialiser si le champ est déjà rempli
        if (!field.getText().isBlank()) {
            tooltip.setText(field.getText());
            Tooltip.install(field, tooltip);
        }
    }}
