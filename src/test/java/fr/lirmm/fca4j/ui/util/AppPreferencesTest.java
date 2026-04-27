package fr.lirmm.fca4j.ui.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link AppPreferences}.
 * Note : ces tests modifient les préférences utilisateur réelles (java.util.prefs).
 * Chaque test nettoie ses clés après exécution.
 */
class AppPreferencesTest {

    private static final String TEST_PREFIX = "__test__";

    @AfterEach
    void cleanup() {
        // Nettoyer les clés de test
        AppPreferences.saveString(TEST_PREFIX + ".str", "");
        AppPreferences.saveBool(TEST_PREFIX + ".bool", false);
        AppPreferences.saveInt(TEST_PREFIX + ".int", 0);
    }

    // ── saveString / loadString ──────────────────────────────────────────────

    @Test
    @DisplayName("saveString puis loadString retourne la valeur enregistrée")
    void saveLoadString() {
        AppPreferences.saveString(TEST_PREFIX + ".str", "hello");
        assertEquals("hello", AppPreferences.loadString(TEST_PREFIX + ".str", "default"));
    }

    @Test
    @DisplayName("loadString retourne la valeur par défaut si clé absente")
    void loadStringDefault() {
        assertEquals("fallback",
            AppPreferences.loadString(TEST_PREFIX + ".nonexistent", "fallback"));
    }

    // ── saveBool / loadBool ──────────────────────────────────────────────────

    @Test
    @DisplayName("saveBool puis loadBool retourne la valeur enregistrée")
    void saveLoadBool() {
        AppPreferences.saveBool(TEST_PREFIX + ".bool", true);
        assertTrue(AppPreferences.loadBool(TEST_PREFIX + ".bool", false));
    }

    @Test
    @DisplayName("loadBool retourne la valeur par défaut si clé absente")
    void loadBoolDefault() {
        assertFalse(AppPreferences.loadBool(TEST_PREFIX + ".nonexistent", false));
    }

    // ── saveInt / loadInt ────────────────────────────────────────────────────

    @Test
    @DisplayName("saveInt puis loadInt retourne la valeur enregistrée")
    void saveLoadInt() {
        AppPreferences.saveInt(TEST_PREFIX + ".int", 42);
        assertEquals(42, AppPreferences.loadInt(TEST_PREFIX + ".int", 0));
    }

    @Test
    @DisplayName("loadInt retourne la valeur par défaut si clé absente")
    void loadIntDefault() {
        assertEquals(99, AppPreferences.loadInt(TEST_PREFIX + ".nonexistent", 99));
    }

    // ── recentEntryPath / recentEntrySeparator ───────────────────────────────

    @Test
    @DisplayName("recentEntryPath extrait le chemin sans séparateur")
    void recentEntryPathSimple() {
        assertEquals("/home/user/ctx.cxt",
            AppPreferences.recentEntryPath("/home/user/ctx.cxt"));
    }

    @Test
    @DisplayName("recentEntryPath extrait le chemin avec séparateur")
    void recentEntryPathWithSep() {
        assertEquals("/home/user/ctx.csv",
            AppPreferences.recentEntryPath("/home/user/ctx.csv|SEMICOLON"));
    }

    @Test
    @DisplayName("recentEntrySeparator retourne COMMA par défaut")
    void recentEntrySeparatorDefault() {
        assertEquals("COMMA",
            AppPreferences.recentEntrySeparator("/home/user/ctx.cxt"));
    }

    @Test
    @DisplayName("recentEntrySeparator extrait le séparateur")
    void recentEntrySeparatorExplicit() {
        assertEquals("SEMICOLON",
            AppPreferences.recentEntrySeparator("/home/user/ctx.csv|SEMICOLON"));
    }

    @Test
    @DisplayName("recentEntrySeparator gère TAB")
    void recentEntrySeparatorTab() {
        assertEquals("TAB",
            AppPreferences.recentEntrySeparator("/home/user/data.tsv|TAB"));
    }

    // ── detectDefaultDot ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getDotPath retourne un chemin non-vide")
    void dotPathNotEmpty() {
        String dot = AppPreferences.getDotPath();
        assertNotNull(dot);
        assertFalse(dot.isBlank());
    }

    // ── isFca4jConfigured ────────────────────────────────────────────────────

    @Test
    @DisplayName("isFca4jConfigured retourne false si pas de JAR configuré")
    void notConfiguredByDefault() {
        String original = AppPreferences.getFca4jJarPath();
        try {
            AppPreferences.setFca4jJarPath("");
            assertFalse(AppPreferences.isFca4jConfigured());
        } finally {
            AppPreferences.setFca4jJarPath(original);
        }
    }

    @Test
    @DisplayName("isFca4jConfigured retourne true après configuration")
    void configuredAfterSet() {
        String original = AppPreferences.getFca4jJarPath();
        try {
            AppPreferences.setFca4jJarPath("/path/to/fca4j.jar");
            assertTrue(AppPreferences.isFca4jConfigured());
        } finally {
            AppPreferences.setFca4jJarPath(original);
        }
    }
}
