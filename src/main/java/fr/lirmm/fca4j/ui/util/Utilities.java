/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.ui.util;

import java.io.File;

import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

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
    }

    // ── Historisation / résolution des chemins de sortie ─────────────────────

    /**
     * Nom de sortie par défaut à partir du fichier d'entrée : nom de base +
     * suffixe + extension. Renvoyé sous forme relative (même dossier que
     * l'entrée), résolu à l'exécution par {@link #resolveOutput}.
     */
    public static String defaultOutputName(String inputPath, String suffix, String ext) {
        if (inputPath == null || inputPath.isBlank()) return "";
        String fileName = java.nio.file.Path.of(inputPath).getFileName().toString();
        String base = fileName.replaceAll("\\.[^.]+$", "");
        ext = normalizeExt(ext);
        return base + (suffix == null ? "" : suffix) + ext;
    }

    /**
     * Remplace l'extension du dernier segment d'un chemin (relatif ou absolu),
     * en préservant la partie répertoire et la forme (séparateurs, relativité).
     */
    public static String replaceExtension(String path, String newExt) {
        if (path == null || path.isBlank()) return path;
        newExt = normalizeExt(newExt);
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String dir  = slash >= 0 ? path.substring(0, slash + 1) : "";
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = name.lastIndexOf('.');
        String baseName = dot > 0 ? name.substring(0, dot) : name; // dot>0 : ne touche pas ".cxt"
        return dir + baseName + newExt;
    }

    /**
     * Convertit une valeur de champ de sortie (relative ou absolue) en chemin
     * absolu normalisé, résolu par rapport au dossier du fichier d'entrée.
     * Forme canonique stockée dans l'historique.
     */
    public static String toAbsoluteOutput(String outputValue, String inputPath) {
        if (outputValue == null || outputValue.isBlank()) return outputValue;
        java.nio.file.Path p = java.nio.file.Path.of(outputValue);
        if (!p.isAbsolute() && inputPath != null && !inputPath.isBlank()) {
            java.nio.file.Path inputDir = java.nio.file.Path.of(inputPath).getParent();
            if (inputDir != null) p = inputDir.resolve(p);
        }
        return p.toAbsolutePath().normalize().toString();
    }

    /**
     * Renvoie le chemin de sortie en privilégiant le relatif par rapport au
     * dossier de l'entrée. Renvoie l'absolu si la sortie est sur une autre
     * racine (drive/UNC) ou si le relatif devrait remonter jusqu'à la racine.
     */
    public static String relativizeForDisplay(String outputPath, String inputPath) {
        if (outputPath == null || outputPath.isBlank()) return outputPath;
        if (inputPath  == null || inputPath.isBlank())  return outputPath;
        java.nio.file.Path outRaw = java.nio.file.Path.of(outputPath);
        if (!outRaw.isAbsolute()) return outputPath; // déjà relatif : afficher tel quel
        try {
            java.nio.file.Path inputDir = java.nio.file.Path.of(inputPath)
                    .toAbsolutePath().normalize().getParent();
            if (inputDir == null) return outputPath;
            java.nio.file.Path out = outRaw.toAbsolutePath().normalize();
            // Racines différentes (autre drive / autre share) → absolu
            if (inputDir.getRoot() == null || !inputDir.getRoot().equals(out.getRoot()))
                return out.toString();
            java.nio.file.Path rel = inputDir.relativize(out);
            int up = 0;
            for (java.nio.file.Path seg : rel) {
                if ("..".equals(seg.toString())) up++;
                else break;
            }
            // On remonte jusqu'à la racine du drive → absolu
            if (up > 0 && up >= inputDir.getNameCount())
                return out.toString();
            return rel.toString();
        } catch (IllegalArgumentException e) {
            return outputPath; // relativize impossible → absolu
        }
    }

    private static String normalizeExt(String ext) {
        if (ext == null || ext.isEmpty()) return "";
        return ext.startsWith(".") ? ext : "." + ext;
    }
    /**
     * Positionne le dossier initial de façon sûre : remonte vers le premier
     * ancêtre existant, et n'applique rien si aucun n'est trouvé.
     * macOS (Glass) rejette un dossier invalide avec IllegalArgumentException,
     * contrairement à Windows qui l'ignore silencieusement.
     */
    public static void setSafeInitialDirectory(FileChooser chooser, String savedPath) {
        File dir = resolveExistingDirectory(savedPath);
        if (dir != null) {
            chooser.setInitialDirectory(dir);
        }
        // sinon : on ne fait rien → l'OS choisit un défaut raisonnable
    }

    public static void setSafeInitialDirectory(DirectoryChooser chooser, String savedPath) {
        File dir = resolveExistingDirectory(savedPath);
        if (dir != null) {
            chooser.setInitialDirectory(dir);
        }
    }

    private static File resolveExistingDirectory(String savedPath) {
        if (savedPath == null || savedPath.isBlank()) {
            return null;
        }
        File f;
        try {
            f = new File(savedPath).getAbsoluteFile();
        } catch (Exception e) {
            return null;
        }
        // Si on a mémorisé un fichier plutôt qu'un dossier, on prend son parent
        if (f.isFile()) {
            f = f.getParentFile();
        }
        // Remonte jusqu'au premier ancêtre réellement existant
        while (f != null && !(f.exists() && f.isDirectory())) {
            f = f.getParentFile();
        }
        return f;
    }
}
