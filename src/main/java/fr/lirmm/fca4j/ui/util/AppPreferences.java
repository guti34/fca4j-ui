package fr.lirmm.fca4j.ui.util;

import java.util.prefs.Preferences;

/**
 * Préférences persistantes de l'application (java.util.prefs).
 * Stockées dans le registre Windows, ~/Library sur macOS, ~/.java sur Linux.
 */
public class AppPreferences {

    private static final Preferences PREFS =
        Preferences.userNodeForPackage(AppPreferences.class);

    // Clés
    private static final String KEY_FCA4J_JAR   = "fca4j.jar.path";
    private static final String KEY_GRAPHVIZ_DOT = "graphviz.dot.path";
    private static final String KEY_LAST_DIR     = "last.open.directory";

    // Valeurs par défaut
    private static final String DEFAULT_DOT = detectDefaultDot();

    private AppPreferences() {}

    // ── FCA4J JAR ────────────────────────────────────────────────────────────

    public static String getFca4jJarPath() {
        return PREFS.get(KEY_FCA4J_JAR, "");
    }

    public static void setFca4jJarPath(String path) {
        PREFS.put(KEY_FCA4J_JAR, path);
    }

    public static boolean isFca4jConfigured() {
        String p = getFca4jJarPath();
        return p != null && !p.isBlank();
    }

    // ── GraphViz ─────────────────────────────────────────────────────────────

    public static String getDotPath() {
        return PREFS.get(KEY_GRAPHVIZ_DOT, DEFAULT_DOT);
    }

    public static void setDotPath(String path) {
        PREFS.put(KEY_GRAPHVIZ_DOT, path);
    }

    // ── Dernier répertoire ouvert ─────────────────────────────────────────────

    public static String getLastDirectory() {
        return PREFS.get(KEY_LAST_DIR, System.getProperty("user.home"));
    }

    public static void setLastDirectory(String path) {
        PREFS.put(KEY_LAST_DIR, path);
    }

    // ── Utilitaire ────────────────────────────────────────────────────────────

    /** Détecte le chemin par défaut de `dot` selon l'OS. */
    private static String detectDefaultDot() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return "C:\\Program Files\\Graphviz\\bin\\dot.exe";
        } else if (os.contains("mac")) {
            return "/opt/homebrew/bin/dot";
        } else {
            return "/usr/bin/dot";
        }
    }

    // ── Langue ────────────────────────────────────────────────────────────────

    private static final String KEY_LANGUAGE = "ui.language";

    public static String getLanguage() {
        return PREFS.get(KEY_LANGUAGE, "");
    }

    public static void setLanguage(String languageTag) {
        PREFS.put(KEY_LANGUAGE, languageTag);
    }

}
