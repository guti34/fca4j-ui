/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.ui.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Modèle mémoire d'un fichier modèle JSON pour FAMILY_IMPORT.
 * Structure : liste de FormalContext + liste de RelationalContext.
 */
public class ImportModel {

    // ── FormalContext ─────────────────────────────────────────────────────────

    public static class FormalContextDef {
        public String       nom            = "";
        public String       path           = "";
        public List<Integer> attrID        = new ArrayList<>();
        public List<Integer> attr          = new ArrayList<>();
        // Toujours [] dans notre éditeur simplifié
        public List<Integer> attrQuartile     = new ArrayList<>();
        public List<Integer> attrQuartileBase = new ArrayList<>();
        // Non produit par l'éditeur
        // attrCustomInterval est omis

        public FormalContextDef() {}

        public FormalContextDef(String nom, String path,
                                 List<Integer> attrID, List<Integer> attr) {
            this.nom    = nom;
            this.path   = path;
            this.attrID = new ArrayList<>(attrID);
            this.attr   = new ArrayList<>(attr);
        }

        @Override public String toString() { return nom; }
    }

    // ── RelationalContext ─────────────────────────────────────────────────────

    public static class RelationalContextDef {
        public String       nom                = "";
        public String       quantif            = "exist";
        public String       source             = "";
        public String       target             = "";
        public String       path               = "";
        public List<Integer> sourceKeys        = new ArrayList<>();
        public List<Integer> targetKeys        = new ArrayList<>();
        public String       nomRelationInverse = "";

        public RelationalContextDef() {}

        public RelationalContextDef(String nom, String quantif,
                                     String source, String target,
                                     String path,
                                     List<Integer> sourceKeys,
                                     List<Integer> targetKeys,
                                     String nomRelationInverse) {
            this.nom                = nom;
            this.quantif            = quantif;
            this.source             = source;
            this.target             = target;
            this.path               = path;
            this.sourceKeys         = new ArrayList<>(sourceKeys);
            this.targetKeys         = new ArrayList<>(targetKeys);
            this.nomRelationInverse = nomRelationInverse;
        }

        @Override public String toString() { return nom; }
    }

    // ── Modèle complet ────────────────────────────────────────────────────────

    private final List<FormalContextDef>    formalContexts    = new ArrayList<>();
    private final List<RelationalContextDef> relationalContexts = new ArrayList<>();

    public List<FormalContextDef>     getFormalContexts()    { return formalContexts; }
    public List<RelationalContextDef> getRelationalContexts(){ return relationalContexts; }

    public void addFormalContext(FormalContextDef fc) {
        formalContexts.add(fc);
    }

    public void addRelationalContext(RelationalContextDef rc) {
        relationalContexts.add(rc);
    }

    public boolean removeFormalContext(FormalContextDef fc) {
        return formalContexts.remove(fc);
    }

    public boolean removeRelationalContext(RelationalContextDef rc) {
        return relationalContexts.remove(rc);
    }

    public FormalContextDef getFormalContextByName(String name) {
        return formalContexts.stream()
            .filter(fc -> fc.nom.equals(name))
            .findFirst().orElse(null);
    }

    /** Retourne toutes les relations qui utilisent ce contexte formel. */
    public List<RelationalContextDef> getRelationsUsing(String contextName) {
        return relationalContexts.stream()
            .filter(rc -> rc.source.equals(contextName)
                       || rc.target.equals(contextName))
            .toList();
    }
}
