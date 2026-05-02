/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.ui.service;

import fr.lirmm.fca4j.ui.model.ImportModel;
import fr.lirmm.fca4j.ui.model.ImportModel.FormalContextDef;
import fr.lirmm.fca4j.ui.model.ImportModel.RelationalContextDef;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link ImportModelService}.
 */
class ImportModelServiceTest {

    private final ImportModelService service = new ImportModelService();

    // ── Création ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createEmpty() retourne un modèle avec des listes vides")
    void createEmpty() {
        ImportModel model = service.createEmpty();
        assertNotNull(model);
        assertTrue(model.getFormalContexts().isEmpty());
        assertTrue(model.getRelationalContexts().isEmpty());
    }

    // ── Sérialisation round-trip ──────────────────────────────────────────────

    @Test
    @DisplayName("Écrire puis relire un modèle avec contextes formels")
    void roundTripFormalContexts(@TempDir Path tmpDir) throws Exception {
        ImportModel model = new ImportModel();
        model.addFormalContext(new FormalContextDef(
                "Animals", "data/animals.csv",
                List.of(0, 1), List.of(2, 3, 4)));
        model.addFormalContext(new FormalContextDef(
                "Habitats", "data/habitats.csv",
                List.of(0), List.of(1, 2)));

        Path file = tmpDir.resolve("model.json");
        service.write(model, file);
        assertTrue(file.toFile().exists());

        ImportModel reloaded = service.read(file);
        assertEquals(2, reloaded.getFormalContexts().size());

        FormalContextDef fc1 = reloaded.getFormalContexts().get(0);
        assertEquals("Animals", fc1.nom);
        assertEquals("data/animals.csv", fc1.path);
        assertEquals(List.of(0, 1), fc1.attrID);
        assertEquals(List.of(2, 3, 4), fc1.attr);

        FormalContextDef fc2 = reloaded.getFormalContexts().get(1);
        assertEquals("Habitats", fc2.nom);
        assertEquals("data/habitats.csv", fc2.path);
    }

    @Test
    @DisplayName("Écrire puis relire un modèle avec contextes relationnels")
    void roundTripRelationalContexts(@TempDir Path tmpDir) throws Exception {
        ImportModel model = new ImportModel();
        model.addFormalContext(new FormalContextDef(
                "A", "a.csv", List.of(0), List.of(1)));
        model.addFormalContext(new FormalContextDef(
                "B", "b.csv", List.of(0), List.of(1)));
        model.addRelationalContext(new RelationalContextDef(
                "rel_A_B", "exist", "A", "B", "trans.csv",
                List.of(0, 1), List.of(2, 3), ""));

        Path file = tmpDir.resolve("model.json");
        service.write(model, file);

        ImportModel reloaded = service.read(file);
        assertEquals(1, reloaded.getRelationalContexts().size());

        RelationalContextDef rc = reloaded.getRelationalContexts().get(0);
        assertEquals("rel_A_B", rc.nom);
        assertEquals("exist", rc.quantif);
        assertEquals("A", rc.source);
        assertEquals("B", rc.target);
        assertEquals("trans.csv", rc.path);
        assertEquals(List.of(0, 1), rc.sourceKeys);
        assertEquals(List.of(2, 3), rc.targetKeys);
    }

    @Test
    @DisplayName("Modèle vide : sérialisation et relecture")
    void roundTripEmpty(@TempDir Path tmpDir) throws Exception {
        ImportModel model = service.createEmpty();
        Path file = tmpDir.resolve("empty.json");
        service.write(model, file);

        ImportModel reloaded = service.read(file);
        assertTrue(reloaded.getFormalContexts().isEmpty());
        assertTrue(reloaded.getRelationalContexts().isEmpty());
    }

    // ── JSON valide ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Le JSON sérialisé ne contient pas de virgule pendante")
    void noTrailingComma(@TempDir Path tmpDir) throws Exception {
        ImportModel model = new ImportModel();
        model.addFormalContext(new FormalContextDef(
                "Ctx", "ctx.csv", List.of(0), List.of(1)));
        model.addRelationalContext(new RelationalContextDef(
                "Rel", "exist", "Ctx", "Ctx", "",
                List.of(0), List.of(1), ""));

        Path file = tmpDir.resolve("model.json");
        service.write(model, file);
        String json = Files.readString(file);

        // Pas de pattern ",\n        }" (virgule suivie directement par fermeture d'objet)
        assertFalse(json.matches("(?s).*,\\s*\\}.*"),
                "Le JSON contient une virgule pendante avant }");
    }

    // ── Caractères spéciaux ──────────────────────────────────────────────────

    @Test
    @DisplayName("Les guillemets et backslashs dans les noms sont échappés")
    void specialCharacters(@TempDir Path tmpDir) throws Exception {
        ImportModel model = new ImportModel();
        model.addFormalContext(new FormalContextDef(
                "test\"ctx", "path\\to\\file.csv",
                List.of(), List.of()));

        Path file = tmpDir.resolve("special.json");
        service.write(model, file);

        ImportModel reloaded = service.read(file);
        FormalContextDef fc = reloaded.getFormalContexts().get(0);
        assertEquals("test\"ctx", fc.nom);
        assertEquals("path\\to\\file.csv", fc.path);
    }

    // ── CSV headers ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("readCsvHeaders lit les en-têtes avec séparateur virgule")
    void readCsvHeadersComma(@TempDir Path tmpDir) throws Exception {
        Path csv = tmpDir.resolve("data.csv");
        Files.writeString(csv, "name,age,city\nAlice,30,Paris\n");

        List<String> headers = service.readCsvHeaders(csv);
        assertEquals(List.of("name", "age", "city"), headers);
    }

    @Test
    @DisplayName("readCsvHeaders lit les en-têtes avec séparateur point-virgule")
    void readCsvHeadersSemicolon(@TempDir Path tmpDir) throws Exception {
        Path csv = tmpDir.resolve("data.csv");
        Files.writeString(csv, "nom;prenom;ville\nDupont;Jean;Lyon\n");

        List<String> headers = service.readCsvHeaders(csv);
        assertEquals(List.of("nom", "prenom", "ville"), headers);
    }

    @Test
    @DisplayName("readCsvHeaders retourne une liste vide pour un fichier vide")
    void readCsvHeadersEmpty(@TempDir Path tmpDir) throws Exception {
        Path csv = tmpDir.resolve("empty.csv");
        Files.writeString(csv, "");

        List<String> headers = service.readCsvHeaders(csv);
        assertTrue(headers.isEmpty());
    }
}
