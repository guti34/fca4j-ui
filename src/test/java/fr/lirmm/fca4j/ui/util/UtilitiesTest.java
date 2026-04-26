package fr.lirmm.fca4j.ui.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link Utilities}.
 */
class UtilitiesTest {

    // ── sanitizeName ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("sanitizeName remplace les espaces par des underscores")
    void sanitizeSpaces() {
        assertEquals("hello_world", Utilities.sanitizeName("hello world"));
    }

    @Test
    @DisplayName("sanitizeName compresse les espaces multiples")
    void sanitizeMultipleSpaces() {
        assertEquals("a_b_c", Utilities.sanitizeName("a   b   c"));
    }

    @Test
    @DisplayName("sanitizeName trim les espaces en début/fin")
    void sanitizeTrim() {
        assertEquals("test", Utilities.sanitizeName("  test  "));
    }

    @Test
    @DisplayName("sanitizeName retourne vide pour null")
    void sanitizeNull() {
        assertEquals("", Utilities.sanitizeName(null));
    }

    @Test
    @DisplayName("sanitizeName retourne vide pour chaîne vide")
    void sanitizeEmpty() {
        assertEquals("", Utilities.sanitizeName(""));
    }

    @Test
    @DisplayName("sanitizeName ne modifie pas un nom sans espaces")
    void sanitizeNoChange() {
        assertEquals("context_1", Utilities.sanitizeName("context_1"));
    }
}
