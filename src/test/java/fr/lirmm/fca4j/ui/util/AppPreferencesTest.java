package fr.lirmm.fca4j.ui.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link AppPreferences}.
 * Note : ces tests modifient les préférences utilisateur réelles (java.util.prefs).
 * Chaque test nettoie ses clés après exécution.
 */
class AppPreferencesTest {

    private static final String TEST_KEY = "__test__";

    @AfterEach
    void cleanup() {
        AppPreferences.saveString(TEST_KEY + ".str", "");
        AppPreferences.saveBool(TEST_KEY + ".bool", false);
        AppPreferences.saveInt(TEST_KEY + ".int", 0);
    }

    // ── saveString / loadString ──────────────────────────────────────────────

    @Test
    @DisplayName("saveString puis loadString retourne la valeur enregistrée")
    void saveLoadString() {
        AppPreferences.saveString(TEST_KEY + ".str", "hello");
        assertEquals("hello", AppPreferences.loadString(TEST_KEY + ".str", "default"));
    }

    @Test
    @DisplayName("loadString retourne la valeur par défaut si clé absente")
    void loadStringDefault() {
        assertEquals("fallback",
            AppPreferences.loadString(TEST_KEY + ".nonexistent", "fallback"));
    }

    // ── saveBool / loadBool ──────────────────────────────────────────────────

    @Test
    @DisplayName("saveBool puis loadBool retourne la valeur enregistrée")
    void saveLoadBool() {
        AppPreferences.saveBool(TEST_KEY + ".bool", true);
        assertTrue(AppPreferences.loadBool(TEST_KEY + ".bool", false));
    }

    @Test
    @DisplayName("loadBool retourne la valeur par défaut si clé absente")
    void loadBoolDefault() {
        assertFalse(AppPreferences.loadBool(TEST_KEY + ".nonexistent", false));
    }

    // ── saveInt / loadInt ────────────────────────────────────────────────────

    @Test
    @DisplayName("saveInt puis loadInt retourne la valeur enregistrée")
    void saveLoadInt() {
        AppPreferences.saveInt(TEST_KEY + ".int", 42);
        assertEquals(42, AppPreferences.loadInt(TEST_KEY + ".int", 0));
    }

    @Test
    @DisplayName("loadInt retourne la valeur par défaut si clé absente")
    void loadIntDefault() {
        assertEquals(99, AppPreferences.loadInt(TEST_KEY + ".nonexistent", 99));
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

    // ── getDotPath ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("getDotPath retourne un chemin non-vide")
    void dotPathNotEmpty() {
        String dot = AppPreferences.getDotPath();
        assertNotNull(dot);
        assertFalse(dot.isBlank());
    }

    // ── useExternalFca4j ─────────────────────────────────────────────────────

    @Test
    @DisplayName("isUseExternalFca4j retourne false par défaut")
    void useExternalDefaultFalse() {
        boolean original = AppPreferences.isUseExternalFca4j();
        try {
            AppPreferences.setUseExternalFca4j(false);
            assertFalse(AppPreferences.isUseExternalFca4j());
        } finally {
            AppPreferences.setUseExternalFca4j(original);
        }
    }

    @Test
    @DisplayName("setUseExternalFca4j persiste la valeur")
    void useExternalPersists() {
        boolean original = AppPreferences.isUseExternalFca4j();
        try {
            AppPreferences.setUseExternalFca4j(true);
            assertTrue(AppPreferences.isUseExternalFca4j());
            AppPreferences.setUseExternalFca4j(false);
            assertFalse(AppPreferences.isUseExternalFca4j());
        } finally {
            AppPreferences.setUseExternalFca4j(original);
        }
    }

    // ── saveOutputForInput / loadOutputForInput ──────────────────────────────

    @Test
    @DisplayName("saveOutputForInput puis loadOutputForInput retourne la valeur")
    void outputForInput() {
        AppPreferences.saveOutputForInput("LATTICE", "/tmp/ctx.cxt", "/tmp/out.xml");
        assertEquals("/tmp/out.xml",
            AppPreferences.loadOutputForInput("LATTICE", "/tmp/ctx.cxt"));
    }

    @Test
    @DisplayName("loadOutputForInput retourne vide si absent")
    void outputForInputDefault() {
        assertEquals("",
            AppPreferences.loadOutputForInput("LATTICE", "/nonexistent/path.cxt"));
    }

    // ── Dernière commande ────────────────────────────────────────────────────

    @Test
    @DisplayName("getLastDirectory retourne au moins le home")
    void lastDirectoryDefault() {
        String dir = AppPreferences.getLastDirectory();
        assertNotNull(dir);
        assertFalse(dir.isBlank());
    }

    @Test
    @DisplayName("setLastDirectory puis getLastDirectory")
    void lastDirectoryPersists() {
        String original = AppPreferences.getLastDirectory();
        try {
            AppPreferences.setLastDirectory("/tmp/test");
            assertEquals("/tmp/test", AppPreferences.getLastDirectory());
        } finally {
            AppPreferences.setLastDirectory(original);
        }
    }

    // ── Langue ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getLanguage retourne une chaîne non-null")
    void languageNotNull() {
        assertNotNull(AppPreferences.getLanguage());
    }

    @Test
    @DisplayName("setLanguage puis getLanguage persiste la valeur")
    void languagePersists() {
        String original = AppPreferences.getLanguage();
        try {
            AppPreferences.setLanguage("es");
            assertEquals("es", AppPreferences.getLanguage());
        } finally {
            AppPreferences.setLanguage(original);
        }
    }

    // ── Fichiers récents ─────────────────────────────────────────────────────

    @Test
    @DisplayName("addRecentContext ajoute en tête de liste")
    void recentContextAddFirst() {
        List<String> original = AppPreferences.getRecentContexts();
        try {
            AppPreferences.clearRecentContexts();
            AppPreferences.addRecentContext("/tmp/a.cxt");
            AppPreferences.addRecentContext("/tmp/b.cxt");
            List<String> recents = AppPreferences.getRecentContexts();
            assertEquals("/tmp/b.cxt", recents.get(0));
            assertEquals("/tmp/a.cxt", recents.get(1));
        } finally {
            AppPreferences.clearRecentContexts();
            for (int i = original.size() - 1; i >= 0; i--)
                AppPreferences.addRecentContext(original.get(i));
        }
    }

    @Test
    @DisplayName("addRecentContext avec séparateur crée une entrée avec pipe")
    void recentContextWithSeparator() {
        List<String> original = AppPreferences.getRecentContexts();
        try {
            AppPreferences.clearRecentContexts();
            AppPreferences.addRecentContext("/tmp/data.csv", "SEMICOLON");
            List<String> recents = AppPreferences.getRecentContexts();
            assertEquals("/tmp/data.csv|SEMICOLON", recents.get(0));
        } finally {
            AppPreferences.clearRecentContexts();
            for (int i = original.size() - 1; i >= 0; i--)
                AppPreferences.addRecentContext(original.get(i));
        }
    }

    @Test
    @DisplayName("clearRecentContexts vide la liste")
    void clearRecents() {
        List<String> original = AppPreferences.getRecentContexts();
        try {
            AppPreferences.addRecentContext("/tmp/test.cxt");
            AppPreferences.clearRecentContexts();
            assertTrue(AppPreferences.getRecentContexts().isEmpty());
        } finally {
            for (int i = original.size() - 1; i >= 0; i--)
                AppPreferences.addRecentContext(original.get(i));
        }
    }
}
