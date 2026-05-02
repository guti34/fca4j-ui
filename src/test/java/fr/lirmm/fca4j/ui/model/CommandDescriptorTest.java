/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.ui.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link CommandDescriptor}.
 */
class CommandDescriptorTest {

    @ParameterizedTest
    @ValueSource(strings = {"LATTICE", "AOCPOSET", "RULEBASIS", "DBASIS",
            "CLARIFY", "REDUCE", "IRREDUCIBLE", "INSPECT", "BINARIZE"})
    @DisplayName("forName() retourne un descripteur non-null pour chaque commande connue")
    void forNameKnown(String name) {
        CommandDescriptor desc = CommandDescriptor.forName(name);
        assertNotNull(desc, "Descripteur null pour " + name);
        assertEquals(name, desc.getName());
    }

    @Test
    @DisplayName("forName() retourne null pour une commande inconnue")
    void forNameUnknown() {
        assertNull(CommandDescriptor.forName("UNKNOWN"));
        assertNull(CommandDescriptor.forName(""));
        assertNull(CommandDescriptor.forName("lattice")); // case-sensitive
    }

    @Test
    @DisplayName("LATTICE a l'algorithme ICEBERG et hasIcebergPercent=true")
    void latticeDescriptor() {
        CommandDescriptor d = CommandDescriptor.LATTICE;
        assertTrue(d.getAlgorithms().contains("ICEBERG"));
        assertTrue(d.hasIcebergPercent());
        assertEquals("AUTO", d.getDefaultAlgorithm());
        assertEquals(CommandDescriptor.CommandFamily.LATTICE_AOC, d.getFamily());
    }

    @Test
    @DisplayName("AOCPOSET n'a pas d'iceberg et a la famille LATTICE_AOC")
    void aocposetDescriptor() {
        CommandDescriptor d = CommandDescriptor.AOCPOSET;
        assertFalse(d.hasIcebergPercent());
        assertEquals(CommandDescriptor.CommandFamily.LATTICE_AOC, d.getFamily());
        assertTrue(d.getAlgorithms().contains("HERMES"));
    }

    @Test
    @DisplayName("RULEBASIS et DBASIS ont la famille RULE_BASIS")
    void ruleBasisFamily() {
        assertEquals(CommandDescriptor.CommandFamily.RULE_BASIS,
                CommandDescriptor.RULEBASIS.getFamily());
        assertEquals(CommandDescriptor.CommandFamily.RULE_BASIS,
                CommandDescriptor.DBASIS.getFamily());
    }

    @Test
    @DisplayName("CLARIFY et REDUCE ont la famille REDUCE_CLARIFY")
    void reduceClarifyFamily() {
        assertEquals(CommandDescriptor.CommandFamily.REDUCE_CLARIFY,
                CommandDescriptor.CLARIFY.getFamily());
        assertEquals(CommandDescriptor.CommandFamily.REDUCE_CLARIFY,
                CommandDescriptor.REDUCE.getFamily());
    }

    @Test
    @DisplayName("Commandes sans algorithmes ont une liste vide")
    void noAlgorithms() {
        assertTrue(CommandDescriptor.CLARIFY.getAlgorithms().isEmpty());
        assertTrue(CommandDescriptor.REDUCE.getAlgorithms().isEmpty());
        assertTrue(CommandDescriptor.IRREDUCIBLE.getAlgorithms().isEmpty());
        assertTrue(CommandDescriptor.INSPECT.getAlgorithms().isEmpty());
        assertTrue(CommandDescriptor.BINARIZE.getAlgorithms().isEmpty());
    }
}
