package fr.lirmm.fca4j.ui.model;

import fr.lirmm.fca4j.ui.model.ImportModel.FormalContextDef;
import fr.lirmm.fca4j.ui.model.ImportModel.RelationalContextDef;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link ImportModel}.
 */
class ImportModelTest {

    private ImportModel model;

    @BeforeEach
    void setUp() {
        model = new ImportModel();
    }

    @Test
    @DisplayName("Ajout et récupération de contextes formels")
    void addFormalContext() {
        FormalContextDef fc = new FormalContextDef("A", "a.csv", List.of(0), List.of(1));
        model.addFormalContext(fc);
        assertEquals(1, model.getFormalContexts().size());
        assertEquals("A", model.getFormalContexts().get(0).nom);
    }

    @Test
    @DisplayName("Suppression d'un contexte formel")
    void removeFormalContext() {
        FormalContextDef fc = new FormalContextDef("A", "a.csv", List.of(), List.of());
        model.addFormalContext(fc);
        assertTrue(model.removeFormalContext(fc));
        assertTrue(model.getFormalContexts().isEmpty());
    }

    @Test
    @DisplayName("getFormalContextByName retourne le bon contexte")
    void getByName() {
        model.addFormalContext(new FormalContextDef("A", "a.csv", List.of(), List.of()));
        model.addFormalContext(new FormalContextDef("B", "b.csv", List.of(), List.of()));

        FormalContextDef found = model.getFormalContextByName("B");
        assertNotNull(found);
        assertEquals("B", found.nom);
    }

    @Test
    @DisplayName("getFormalContextByName retourne null si absent")
    void getByNameNotFound() {
        assertNull(model.getFormalContextByName("X"));
    }

    @Test
    @DisplayName("Ajout et suppression de contextes relationnels")
    void addRemoveRelationalContext() {
        RelationalContextDef rc = new RelationalContextDef(
                "R", "exist", "A", "B", "", List.of(), List.of(), "");
        model.addRelationalContext(rc);
        assertEquals(1, model.getRelationalContexts().size());

        assertTrue(model.removeRelationalContext(rc));
        assertTrue(model.getRelationalContexts().isEmpty());
    }

    @Test
    @DisplayName("getRelationsUsing retourne les relations source et target")
    void getRelationsUsing() {
        model.addFormalContext(new FormalContextDef("A", "", List.of(), List.of()));
        model.addFormalContext(new FormalContextDef("B", "", List.of(), List.of()));
        model.addFormalContext(new FormalContextDef("C", "", List.of(), List.of()));

        model.addRelationalContext(new RelationalContextDef(
                "R1", "exist", "A", "B", "", List.of(), List.of(), ""));
        model.addRelationalContext(new RelationalContextDef(
                "R2", "exist", "B", "C", "", List.of(), List.of(), ""));
        model.addRelationalContext(new RelationalContextDef(
                "R3", "exist", "C", "A", "", List.of(), List.of(), ""));

        List<RelationalContextDef> relA = model.getRelationsUsing("A");
        assertEquals(2, relA.size()); // R1 (source=A) et R3 (target=A)

        List<RelationalContextDef> relB = model.getRelationsUsing("B");
        assertEquals(2, relB.size()); // R1 (target=B) et R2 (source=B)

        List<RelationalContextDef> relX = model.getRelationsUsing("X");
        assertTrue(relX.isEmpty());
    }

    @Test
    @DisplayName("toString des définitions retourne le nom")
    void toStringDefs() {
        FormalContextDef fc = new FormalContextDef("MyCtx", "", List.of(), List.of());
        assertEquals("MyCtx", fc.toString());

        RelationalContextDef rc = new RelationalContextDef(
                "MyRel", "exist", "A", "B", "", List.of(), List.of(), "");
        assertEquals("MyRel", rc.toString());
    }
}
