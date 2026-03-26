package fr.lirmm.fca4j.ui.service;

import fr.lirmm.fca4j.ui.model.ImportModel;
import fr.lirmm.fca4j.ui.model.ImportModel.FormalContextDef;
import fr.lirmm.fca4j.ui.model.ImportModel.RelationalContextDef;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Lit et écrit les fichiers modèle JSON pour FAMILY_IMPORT.
 * Pas de dépendance externe — parsing JSON minimal à la main.
 */
public class ImportModelService {

    // ── Lecture ───────────────────────────────────────────────────────────────

    public ImportModel read(Path path) throws Exception {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        return parse(json);
    }

    // ── Écriture ──────────────────────────────────────────────────────────────

    public void write(ImportModel model, Path path) throws Exception {
        String json = serialize(model);
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    // ── Création d'un modèle vide ─────────────────────────────────────────────

    public ImportModel createEmpty() {
        return new ImportModel();
    }

    // ── Sérialisation ─────────────────────────────────────────────────────────

    private String serialize(ImportModel model) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        // FormalContext
        sb.append("    \"FormalContext\": [\n");
        List<FormalContextDef> fcs = model.getFormalContexts();
        for (int i = 0; i < fcs.size(); i++) {
            FormalContextDef fc = fcs.get(i);
            sb.append("        {\n");
            sb.append("            \"nom\": ").append(quoted(fc.nom)).append(",\n");
            sb.append("            \"path\": ").append(quoted(fc.path)).append(",\n");
            sb.append("            \"attrID\": ").append(intList(fc.attrID)).append(",\n");
            sb.append("            \"attr\": ").append(intList(fc.attr)).append(",\n");
            sb.append("            \"attrQuartile\": ").append(intList(fc.attrQuartile)).append(",\n");
            sb.append("            \"attrQuartileBase\": ").append(intList(fc.attrQuartileBase)).append(",\n");
            sb.append("            \"attrCustomInterval\": []\n");
            sb.append("        }");
            if (i < fcs.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("    ],\n");

        // RelationalContext
        sb.append("    \"RelationalContext\": [\n");
        List<RelationalContextDef> rcs = model.getRelationalContexts();
        for (int i = 0; i < rcs.size(); i++) {
            RelationalContextDef rc = rcs.get(i);
            sb.append("        {\n");
            sb.append("            \"nom\": ").append(quoted(rc.nom)).append(",\n");
            sb.append("            \"quantif\": ").append(quoted(rc.quantif)).append(",\n");
            sb.append("            \"source\": ").append(quoted(rc.source)).append(",\n");
            sb.append("            \"target\": ").append(quoted(rc.target)).append(",\n");
            sb.append("            \"path\": ").append(quoted(rc.path)).append(",\n");
            sb.append("            \"sourceKeys\": ").append(intList(rc.sourceKeys)).append(",\n");
            sb.append("            \"targetKeys\": ").append(intList(rc.targetKeys)).append(",\n");
            sb.append("        }");
            if (i < rcs.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("    ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String quoted(String s) {
        return "\"" + (s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")) + "\"";
    }

    private String intList(List<Integer> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append(list.get(i));
            if (i < list.size() - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }

    // ── Parsing JSON minimal ──────────────────────────────────────────────────

    private ImportModel parse(String json) throws Exception {
        ImportModel model = new ImportModel();
        // Extraire le tableau FormalContext
        String fcArray = extractArray(json, "FormalContext");
        for (String obj : splitObjects(fcArray)) {
            model.addFormalContext(parseFormalContext(obj));
        }
        // Extraire le tableau RelationalContext
        String rcArray = extractArray(json, "RelationalContext");
        for (String obj : splitObjects(rcArray)) {
            model.addRelationalContext(parseRelationalContext(obj));
        }
        return model;
    }

    private FormalContextDef parseFormalContext(String obj) {
        FormalContextDef fc = new FormalContextDef();
        fc.nom    = parseString(obj, "nom");
        fc.path   = parseString(obj, "path");
        fc.attrID = parseIntArray(obj, "attrID");
        fc.attr   = parseIntArray(obj, "attr");
        List<Integer> aq = parseIntArray(obj, "attrQuartile");
        fc.attrQuartile = aq.isEmpty() ? List.of(-1) : aq;
        List<Integer> aqb = parseIntArray(obj, "attrQuartileBase");
        fc.attrQuartileBase = aqb.isEmpty() ? List.of(-1) : aqb;
        return fc;
    }

    private RelationalContextDef parseRelationalContext(String obj) {
        RelationalContextDef rc = new RelationalContextDef();
        rc.nom                = parseString(obj, "nom");
        rc.quantif            = parseString(obj, "quantif");
        rc.source             = parseString(obj, "source");
        rc.target             = parseString(obj, "target");
        rc.path               = parseString(obj, "path");
        rc.sourceKeys         = parseIntArray(obj, "sourceKeys");
        rc.targetKeys         = parseIntArray(obj, "targetKeys");
        return rc;
    }

    /** Extrait le contenu d'un tableau JSON nommé. */
    private String extractArray(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return "";
        int start = json.indexOf('[', idx);
        if (start < 0) return "";
        int depth = 0, i = start;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') { depth--; if (depth == 0) return json.substring(start + 1, i); }
            i++;
        }
        return "";
    }

    /** Découpe un tableau JSON en objets individuels. */
    private List<String> splitObjects(String array) {
        List<String> result = new ArrayList<>();
        int depth = 0, start = -1;
        for (int i = 0; i < array.length(); i++) {
            char c = array.charAt(i);
            if (c == '{') { if (depth == 0) start = i; depth++; }
            else if (c == '}') { depth--; if (depth == 0 && start >= 0) result.add(array.substring(start, i + 1)); }
        }
        return result;
    }

    /** Extrait une valeur string d'un objet JSON. */
    private String parseString(String obj, String key) {
        String search = "\"" + key + "\"";
        int idx = obj.indexOf(search);
        if (idx < 0) return "";
        int colon = obj.indexOf(':', idx);
        if (colon < 0) return "";
        int q1 = obj.indexOf('"', colon);
        if (q1 < 0) return "";
        int q2 = q1 + 1;
        while (q2 < obj.length()) {
            if (obj.charAt(q2) == '"' && obj.charAt(q2 - 1) != '\\') break;
            q2++;
        }
        return obj.substring(q1 + 1, q2).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /** Extrait un tableau d'entiers d'un objet JSON. */
    private List<Integer> parseIntArray(String obj, String key) {
        String search = "\"" + key + "\"";
        int idx = obj.indexOf(search);
        if (idx < 0) return new ArrayList<>();
        int start = obj.indexOf('[', idx);
        if (start < 0) return new ArrayList<>();
        int end = obj.indexOf(']', start);
        if (end < 0) return new ArrayList<>();
        String content = obj.substring(start + 1, end).trim();
        if (content.isBlank()) return new ArrayList<>();
        List<Integer> result = new ArrayList<>();
        for (String part : content.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                try { result.add(Integer.parseInt(trimmed)); }
                catch (NumberFormatException ignored) {}
            }
        }
        return result;
    }

    // ── Lecture des en-têtes CSV ──────────────────────────────────────────────

    /**
     * Lit la première ligne d'un CSV et retourne la liste des en-têtes.
     * Supporte ; , et \t comme séparateurs (auto-détection).
     */
    public List<String> readCsvHeaders(Path csvPath) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(csvPath.toFile()),
                    StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            if (line == null) return List.of();
            // Auto-détection du séparateur
            char sep = detectSeparator(line);
            String[] parts = line.split(String.valueOf(sep), -1);
            List<String> headers = new ArrayList<>();
            for (String p : parts) headers.add(p.trim().replace("\"", ""));
            return headers;
        }
    }

    private char detectSeparator(String line) {
        long semicolons = line.chars().filter(c -> c == ';').count();
        long commas     = line.chars().filter(c -> c == ',').count();
        long tabs       = line.chars().filter(c -> c == '\t').count();
        if (semicolons >= commas && semicolons >= tabs) return ';';
        if (tabs >= commas) return '\t';
        return ',';
    }
}
