package fr.lirmm.fca4j.ui.service;

import fr.lirmm.fca4j.core.BinaryContext;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.core.RCAFamily.FormalContext;
import fr.lirmm.fca4j.core.RCAFamily.RelationalContext;
import fr.lirmm.fca4j.iset.std.BitSetFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Synchronise les contextes relationnels d'une RCAFamily après modification
 * d'un contexte formel source (objets) ou target (attributs/objets du target).
 *
 * Règles :
 * - Contexte formel modifié en tant que SOURCE d'un relationnel
 *   → les LIGNES du contexte relationnel reflètent les objets de la source
 * - Contexte formel modifié en tant que TARGET d'un relationnel
 *   → les COLONNES du contexte relationnel reflètent les objets du target
 *
 * Dans les deux cas : ajout silencieux, suppression silencieuse.
 * Les valeurs existantes (x) sont conservées quand l'objet/attribut est toujours présent.
 */
public class RcfIntegrityService {

    /**
     * Point d'entrée principal. À appeler après chaque modification d'un
     * contexte formel via l'éditeur de contexte.
     *
     * @param family  la famille à synchroniser
     * @param fcName  le nom du contexte formel qui vient d'être modifié
     */
    public void synchronize(RCAFamily family, String fcName) {
        FormalContext fc = family.getFormalContext(fcName);
        if (fc == null) return;

        // Ce contexte est SOURCE de certains relationnels → synchroniser les lignes
        for (RelationalContext rc : family.outgoingRelationalContextOf(fc)) {
            synchronizeRows(family, rc, fc);
        }

        // Ce contexte est TARGET de certains relationnels → synchroniser les colonnes
        for (RelationalContext rc : family.incomingRelationalContextOf(fc)) {
            synchronizeColumns(family, rc, fc);
        }
    }

    /**
     * Synchronise les LIGNES d'un contexte relationnel avec les objets
     * du contexte formel source.
     * Les lignes correspondent aux objets de la source.
     */
    private void synchronizeRows(RCAFamily family, RelationalContext rc,
                                  FormalContext source) {
        IBinaryContext rel    = rc.getContext();
        IBinaryContext srcCtx = source.getContext();
        FormalContext  tgt    = family.getTargetOf(rc);
        IBinaryContext tgtCtx = tgt.getContext();

        int newObjCount  = srcCtx.getObjectCount();
        int newAttrCount = tgtCtx.getObjectCount(); // colonnes = objets du target

        // Reconstruire un nouveau contexte relationnel avec les bons objets
        BinaryContext newRel = new BinaryContext(
            newObjCount, newAttrCount, rel.getName(), new BitSetFactory());

        // Noms des lignes = objets de la source
        for (int i = 0; i < newObjCount; i++)
            newRel.addObjectName(srcCtx.getObjectName(i));

        // Noms des colonnes = objets du target
        for (int j = 0; j < newAttrCount; j++)
            newRel.addAttributeName(tgtCtx.getObjectName(j));

        // Conserver les valeurs existantes pour les paires (objet source, objet target)
        // qui existent encore dans les deux contextes
        for (int i = 0; i < newObjCount; i++) {
            String srcObj = srcCtx.getObjectName(i);
            int oldRow = rel.getObjectIndex(srcObj);
            if (oldRow < 0) continue; // nouvel objet → toute la ligne à false

            for (int j = 0; j < newAttrCount; j++) {
                String tgtObj = tgtCtx.getObjectName(j);
                int oldCol = rel.getAttributeIndex(tgtObj);
                if (oldCol >= 0 && rel.get(oldRow, oldCol))
                    newRel.set(i, j, true);
            }
        }

        // Remplacer le contexte dans le relationnel
        rc.setContext(newRel);
    }

    /**
     * Synchronise les COLONNES d'un contexte relationnel avec les objets
     * du contexte formel target.
     * Les colonnes correspondent aux objets du target.
     */
    private void synchronizeColumns(RCAFamily family, RelationalContext rc,
                                     FormalContext target) {
        IBinaryContext rel    = rc.getContext();
        IBinaryContext tgtCtx = target.getContext();
        FormalContext  src    = family.getSourceOf(rc);
        IBinaryContext srcCtx = src.getContext();

        int newObjCount  = srcCtx.getObjectCount();
        int newAttrCount = tgtCtx.getObjectCount();

        BinaryContext newRel = new BinaryContext(
            newObjCount, newAttrCount, rel.getName(), new BitSetFactory());

        // Noms des lignes = objets de la source (inchangés)
        for (int i = 0; i < newObjCount; i++)
            newRel.addObjectName(srcCtx.getObjectName(i));

        // Noms des colonnes = objets du target (mis à jour)
        for (int j = 0; j < newAttrCount; j++)
            newRel.addAttributeName(tgtCtx.getObjectName(j));

        // Conserver les valeurs existantes
        for (int i = 0; i < newObjCount; i++) {
            String srcObj = srcCtx.getObjectName(i);
            int oldRow = rel.getObjectIndex(srcObj);
            if (oldRow < 0) continue;

            for (int j = 0; j < newAttrCount; j++) {
                String tgtObj = tgtCtx.getObjectName(j);
                int oldCol = rel.getAttributeIndex(tgtObj);
                if (oldCol >= 0 && rel.get(oldRow, oldCol))
                    newRel.set(i, j, true);
            }
        }

        rc.setContext(newRel);
    }
}
