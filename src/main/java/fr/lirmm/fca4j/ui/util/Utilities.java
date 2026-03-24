package fr.lirmm.fca4j.ui.util;

import javafx.scene.control.TextField;

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

}
