/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.ui.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pré-traitement "cosmétique" du texte .dot avant rendu par dot -Tsvg,
 * réservé à fca4j-ui : substitution des noms d'opérateurs
 * (fr.lirmm.fca4j.core.operator, énumérés dans MyScalingOperatorFactory)
 * par leur symbole Unicode, et injection d'une police par défaut capable
 * de les afficher.
 *
 * <p><b>Ce traitement n'a rien à faire dans fca4j en ligne de commande</b> :
 * le .dot produit par le cœur doit rester lisible/scriptable tel quel.</p>
 *
 * <p>Le nom d'un attribut relationnel est toujours construit par
 * {@code RCAFamily.FormalContext.addRelationalAttribute} sous la forme
 * exacte (voir fca4j-core RCAFamily.java) :</p>
 * <pre>attr_name = rc.operator + "_" + rc.getRelationName() + "(" + conceptExpr + ")";</pre>
 *
 * <p>Ce préfixe "operatorName_" reste inchangé quel que soit le mode de
 * renommage (SIMPLE, REDUCED_INTENT, FULL_INTENT...) car
 * {@code AttributeRenamer.build} ne réécrit jamais que le contenu entre
 * parenthèses. On peut donc détecter de façon fiable un attribut
 * relationnel en exigeant qu'une parenthèse ouvrante suive le préfixe à
 * une distance raisonnable (les entrées de {@code GraphVizDotWriter} étant
 * concaténées avec un "\n" littéral et non une vraie frontière de mot, une
 * simple regex \b...\b ne suffit pas).</p>
 */
public final class DotDisplayPreprocessor {

    // opérateurs non paramétrés — noms vérifiés via getName() de chaque
    // classe de fr.lirmm.fca4j.core.operator
    private static final Map<String, String> FIXED_OPERATORS = new LinkedHashMap<>();
    static {
        FIXED_OPERATORS.put("exist", "\u2203");             // ∃   MyExistentialScaling
        FIXED_OPERATORS.put("existForall", "\u2203\u2200"); // ∃∀  MyForAllExistentialScaling
        FIXED_OPERATORS.put("existContains", "\u2287");     // ⊇   MyContainsExistScaling
        FIXED_OPERATORS.put("equality", "=");               // =   MyEqualityScaling
    }

    // opérateurs paramétrés : préfixe + seuil numérique en %, ex. "existForallN75"
    // (voir MyForNearlyAllExistentialScaling / MyContainsExistNScaling)
    private static final Map<String, String> PARAMETERIZED_PREFIXES = new LinkedHashMap<>();
    static {
        PARAMETERIZED_PREFIXES.put("existForallN", "\u2200\u2265");   // ∀≥  MyForNearlyAllExistentialScaling
        PARAMETERIZED_PREFIXES.put("existContainsN", "\u2287\u2265"); // ⊇≥  MyContainsExistNScaling
    }

    // distance max (en caractères) tolérée entre "operatorName_" et la
    // parenthèse ouvrante qui confirme qu'il s'agit bien d'un attribut
    // relationnel (limite le risque de faux positif inter-attributs).
    private static final int LOOKAHEAD_BOUND = 60;

    private static final String DISPLAY_FONT = "DejaVu Sans";

    private DotDisplayPreprocessor() {
    }

    /**
     * Retourne une version du .dot adaptée à l'affichage : noms d'opérateurs
     * remplacés par leur symbole Unicode, police par défaut fixée pour les
     * nœuds et arêtes.
     *
     * @param dotSource contenu .dot original, tel que produit par fca4j-project
     */
    public static String forDisplay(String dotSource) {
        String result = injectDefaultFont(dotSource);

        // opérateurs paramétrés d'abord : sinon le préfixe "existForallN"
        // pourrait être partiellement absorbé par une future extension du
        // set d'opérateurs fixes portant un nom proche.
        for (Map.Entry<String, String> entry : PARAMETERIZED_PREFIXES.entrySet()) {
            result = replaceParameterized(result, entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : FIXED_OPERATORS.entrySet()) {
            result = replaceOperatorPrefix(result, entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static String injectDefaultFont(String dotSource) {
        // Insère "node [fontname="..."]; edge [fontname="..."];" juste après
        // la première accolade ouvrante du graphe ("digraph G {"), ce qui
        // s'applique par défaut à tous les nœuds/arêtes déclarés ensuite.
        int braceIndex = dotSource.indexOf('{');
        if (braceIndex < 0) {
            return dotSource; // .dot mal formé : on ne touche à rien
        }
        String defaults = "\n  node [fontname=\"" + DISPLAY_FONT + "\"];\n"
                         + "  edge [fontname=\"" + DISPLAY_FONT + "\"];\n";
        return dotSource.substring(0, braceIndex + 1)
             + defaults
             + dotSource.substring(braceIndex + 1);
    }

    /**
     * Remplace "operatorName_" par son symbole, uniquement lorsqu'une
     * parenthèse ouvrante apparaît à moins de {@link #LOOKAHEAD_BOUND}
     * caractères (sans traverser un "|" ni un "\" — séparateurs de sections
     * / d'attributs dans le label), ce qui confirme qu'on est bien sur le
     * préfixe d'un attribut relationnel operatorName_relationName(...).
     */
    private static String replaceOperatorPrefix(String text, String operatorName, String symbol) {
        Pattern pattern = Pattern.compile(
            Pattern.quote(operatorName) + "_(?=[^|\\\\]{0," + LOOKAHEAD_BOUND + "}\\()");
        Matcher matcher = pattern.matcher(text);
        return matcher.replaceAll(Matcher.quoteReplacement(symbol));
    }

    /**
     * Idem pour les opérateurs paramétrés : "existForallN75_..." devient
     * "∀≥75%..." (le suffixe numérique est conservé).
     */
    private static String replaceParameterized(String text, String prefix, String symbol) {
        Pattern pattern = Pattern.compile(
            Pattern.quote(prefix) + "(\\d+(?:\\.\\d+)?)_(?=[^|\\\\]{0," + LOOKAHEAD_BOUND + "}\\()");
        Matcher matcher = pattern.matcher(text);
        return matcher.replaceAll(Matcher.quoteReplacement(symbol) + "$1%");
    }
}
