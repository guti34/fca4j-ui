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
    private static final String PREF_CMD = "cmd.";

    private static final int MAX_RECENT = 10;
    private static final String KEY_RECENT_CTX    = "recent.context.";
    private static final String KEY_RECENT_FAMILY = "recent.family.";
    private static final String KEY_RECENT_MODEL = "recent.model.";
    
    
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
    public static void saveString(String key, String value) {
        if (value != null) PREFS.put(PREF_CMD + key, value);
    }
    public static String loadString(String key, String def) {
        return PREFS.get(PREF_CMD + key, def);
    }
    public static void saveBool(String key, boolean value) {
    	PREFS.putBoolean(PREF_CMD + key, value);
    }
    public static boolean loadBool(String key, boolean def) {
        return PREFS.getBoolean(PREF_CMD + key, def);
    }
    public static void saveInt(String key, int value) {
    	PREFS.putInt(PREF_CMD + key, value);
    }
    public static int loadInt(String key, int def) {
        return PREFS.getInt(PREF_CMD + key, def);
    }
    // ── Langue ────────────────────────────────────────────────────────────────

    private static final String KEY_LANGUAGE = "ui.language";

    public static String getLanguage() {
        return PREFS.get(KEY_LANGUAGE, "");
    }

    public static void setLanguage(String languageTag) {
        PREFS.put(KEY_LANGUAGE, languageTag);
    }
 // ── Fichiers récents ──────────────────────────────────────────────────────

    public static java.util.List<String> getRecentContexts() {
        return loadRecentList(KEY_RECENT_CTX);
    }

    public static java.util.List<String> getRecentFamilies() {
        return loadRecentList(KEY_RECENT_FAMILY);
    }
    public static java.util.List<String> getRecentModels() {
        return loadRecentList(KEY_RECENT_MODEL);
    }

    public static void addRecentModel(String path) {
        saveRecentList(KEY_RECENT_MODEL, path);
    }
    public static void addRecentContext(String path) {
        saveRecentList(KEY_RECENT_CTX, path);
    }

    public static void addRecentFamily(String path) {
        saveRecentList(KEY_RECENT_FAMILY, path);
    }

    private static java.util.List<String> loadRecentList(String prefix) {
        java.util.List<String> list = new java.util.ArrayList<>();
        for (int i = 0; i < MAX_RECENT; i++) {
            String val = PREFS.get(prefix + i, "");
            if (!val.isBlank()) list.add(val);
        }
        return list;
    }

    private static void saveRecentList(String prefix, String newPath) {
        java.util.List<String> list = loadRecentList(prefix);
        list.remove(newPath);          // retirer si déjà présent
        list.add(0, newPath);          // ajouter en tête
        if (list.size() > MAX_RECENT)
            list = list.subList(0, MAX_RECENT);
        for (int i = 0; i < list.size(); i++)
            PREFS.put(prefix + i, list.get(i));
        // Effacer les entrées suivantes si liste raccourcie
        for (int i = list.size(); i < MAX_RECENT; i++)
            PREFS.remove(prefix + i);
    }
}
