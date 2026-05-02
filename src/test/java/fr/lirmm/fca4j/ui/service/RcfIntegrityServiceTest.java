/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.ui.service;

import fr.lirmm.fca4j.core.BinaryContext;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.core.RCAFamily.RelationalContext;
import fr.lirmm.fca4j.iset.std.BitSetFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link RcfIntegrityService}.
 */
class RcfIntegrityServiceTest {

    private RcfIntegrityService service;
    private final BitSetFactory factory = new BitSetFactory();

    @BeforeEach
    void setUp() {
        service = new RcfIntegrityService();
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private BinaryContext createContext(String name, String[] objects, String[] attributes) {
        BinaryContext ctx = new BinaryContext(
                objects.length, attributes.length, name, factory);
        for (String obj : objects)  ctx.addObjectName(obj);
        for (String attr : attributes) ctx.addAttributeName(attr);
        return ctx;
    }

    /** Retourne le premier (et unique) contexte relationnel de la famille. */
    private IBinaryContext getFirstRelContext(RCAFamily family) {
        return family.getRelationalContexts().iterator().next().getContext();
    }

    /**
     * Construit une famille simple :
     *   Animals (cat, dog, bird) --livesIn[exist]--> Habitats (house, garden, sky)
     *   avec cat→house, dog→garden, bird→sky
     */
    private RCAFamily buildSimpleFamily() {
        BinaryContext animals = createContext("Animals",
                new String[]{"cat", "dog", "bird"},
                new String[]{"legs", "wings"});
        animals.set(0, 0, true); // cat has legs
        animals.set(1, 0, true); // dog has legs
        animals.set(2, 1, true); // bird has wings

        BinaryContext habitats = createContext("Habitats",
                new String[]{"house", "garden", "sky"},
                new String[]{"indoor", "outdoor"});

        BinaryContext livesIn = createContext("livesIn",
                new String[]{"cat", "dog", "bird"},
                new String[]{"house", "garden", "sky"});
        livesIn.set(0, 0, true); // cat livesIn house
        livesIn.set(1, 1, true); // dog livesIn garden
        livesIn.set(2, 2, true); // bird livesIn sky

        RCAFamily family = new RCAFamily("TestFamily", factory);
        family.addFormalContext(animals, null);
        family.addFormalContext(habitats, null);
        family.addRelationalContext(livesIn, "Animals", "Habitats", "exist");

        return family;
    }

    // ── Tests synchronisation source ─────────────────────────────────────────

    @Test
    @DisplayName("Ajout d'un objet dans la source ajoute une ligne au relationnel")
    void addObjectToSource() {
        RCAFamily family = buildSimpleFamily();

        BinaryContext newAnimals = createContext("Animals",
                new String[]{"cat", "dog", "bird", "fish"},
                new String[]{"legs", "wings"});
        newAnimals.set(0, 0, true);
        newAnimals.set(1, 0, true);
        newAnimals.set(2, 1, true);
        family.getFormalContext("Animals").setContext(newAnimals);

        service.synchronize(family, "Animals");

        IBinaryContext rel = getFirstRelContext(family);
        assertEquals(4, rel.getObjectCount());
        assertEquals("fish", rel.getObjectName(3));
        assertTrue(rel.get(0, 0));   // cat → house
        assertTrue(rel.get(1, 1));   // dog → garden
        assertTrue(rel.get(2, 2));   // bird → sky
        assertFalse(rel.get(3, 0));  // fish → house = false
        assertFalse(rel.get(3, 1));  // fish → garden = false
        assertFalse(rel.get(3, 2));  // fish → sky = false
    }

    @Test
    @DisplayName("Suppression d'un objet dans la source retire la ligne du relationnel")
    void removeObjectFromSource() {
        RCAFamily family = buildSimpleFamily();

        BinaryContext newAnimals = createContext("Animals",
                new String[]{"cat", "bird"},
                new String[]{"legs", "wings"});
        newAnimals.set(0, 0, true);
        newAnimals.set(1, 1, true);
        family.getFormalContext("Animals").setContext(newAnimals);

        service.synchronize(family, "Animals");

        IBinaryContext rel = getFirstRelContext(family);
        assertEquals(2, rel.getObjectCount());
        assertEquals("cat", rel.getObjectName(0));
        assertEquals("bird", rel.getObjectName(1));
        assertTrue(rel.get(0, 0));  // cat → house
        assertTrue(rel.get(1, 2));  // bird → sky
    }

    // ── Tests synchronisation target ─────────────────────────────────────────

    @Test
    @DisplayName("Ajout d'un objet dans le target ajoute une colonne au relationnel")
    void addObjectToTarget() {
        RCAFamily family = buildSimpleFamily();

        BinaryContext newHabitats = createContext("Habitats",
                new String[]{"house", "garden", "sky", "barn"},
                new String[]{"indoor", "outdoor"});
        family.getFormalContext("Habitats").setContext(newHabitats);

        service.synchronize(family, "Habitats");

        IBinaryContext rel = getFirstRelContext(family);
        assertEquals(4, rel.getAttributeCount());
        assertEquals("barn", rel.getAttributeName(3));
        assertTrue(rel.get(0, 0));   // cat → house
        assertTrue(rel.get(1, 1));   // dog → garden
        assertFalse(rel.get(0, 3));  // cat → barn = false
    }

    @Test
    @DisplayName("Suppression d'un objet dans le target retire la colonne du relationnel")
    void removeObjectFromTarget() {
        RCAFamily family = buildSimpleFamily();

        BinaryContext newHabitats = createContext("Habitats",
                new String[]{"house", "sky"},
                new String[]{"indoor", "outdoor"});
        family.getFormalContext("Habitats").setContext(newHabitats);

        service.synchronize(family, "Habitats");

        IBinaryContext rel = getFirstRelContext(family);
        assertEquals(2, rel.getAttributeCount());
        assertEquals("house", rel.getAttributeName(0));
        assertEquals("sky", rel.getAttributeName(1));
        assertTrue(rel.get(0, 0));  // cat → house
        assertTrue(rel.get(2, 1));  // bird → sky
    }

    // ── Cas limites ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("synchronize avec un nom de contexte inconnu ne fait rien")
    void unknownContextName() {
        RCAFamily family = buildSimpleFamily();
        service.synchronize(family, "NonExistent");

        IBinaryContext rel = getFirstRelContext(family);
        assertEquals(3, rel.getObjectCount());
        assertEquals(3, rel.getAttributeCount());
    }

    @Test
    @DisplayName("synchronize préserve les valeurs quand rien ne change")
    void noChanges() {
        RCAFamily family = buildSimpleFamily();
        service.synchronize(family, "Animals");

        IBinaryContext rel = getFirstRelContext(family);
        assertEquals(3, rel.getObjectCount());
        assertEquals(3, rel.getAttributeCount());
        assertTrue(rel.get(0, 0));  // cat → house
        assertTrue(rel.get(1, 1));  // dog → garden
        assertTrue(rel.get(2, 2));  // bird → sky
    }
}