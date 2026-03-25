package fr.lirmm.fca4j.ui.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Construit la liste d'arguments passés à FCA4J CLI
 * à partir des choix faits dans l'interface.
 */
public class CommandBuilder {

    // ── Champs communs ───────────────────────────────────────────────────────
    private String  command;
    private String  inputFile;
    private String  outputFile;
    private String  algorithm;
    private String  inputFormat;
    private String  outputFormat   = "XML";
    private String  implementation;
    private String  separator;
    private Integer timeout;
    private boolean verbose        = false;

    // ── LATTICE / AOCPOSET ───────────────────────────────────────────────────
    private String  dotFile;
    private String  displayMode    = "SIMPLIFIED";
    private boolean stability      = false;
    private Integer icebergPercent;
    private String  datalogFolder;
    private String  datalogFile;
    private boolean noDirectSiblings = false;

    // ── RULEBASIS ────────────────────────────────────────────────────────────
    private boolean clarify        = false;
    private String  closureMethod;          // BASIC | WITH_HISTORY
    private String  poolMode;               // MONO | FORKJOINPOOL | MULTITHREAD
    private Integer threadThreshold;        // -h
    private boolean sortBySupport  = false; // -b
    private String  reportFile;             // -r
    private String  implFolder;             // -folder

    // ── DBASIS ───────────────────────────────────────────────────────────────
    private Integer minimalSupport;         // -x

    // ── CLARIFY / REDUCE ─────────────────────────────────────────────────────
    private boolean clarifyObjects    = false; // -xo
    private boolean clarifyAttributes = false; // -xa
    private boolean groupByClasses    = false; // -u (REDUCE uniquement)

    // ── IRREDUCIBLE ───────────────────────────────────────────────────────────
    private boolean listObjects    = false; // -lobj
    private boolean listAttributes = false; // -lattr

    // ── BINARIZE ─────────────────────────────────────────────────────────────
    private java.util.List<String> excludeAttrs = new java.util.ArrayList<>(); // -excl
    private java.util.List<String> includeAttrs = new java.util.ArrayList<>(); // -incl

    // ── FAMILY_IMPORT ────────────────────────────────────────────────────────
    private String familyImportModelFormat; // -m JSON|XML
    // ── RCA ───────────────────────────────────────────────────────────────────
    private String  familyFormat;                    // -f
    private boolean rcaClean             = false;    // -clean
    private boolean rcaRenameRA          = false;    // -ra
    private boolean rcaRenameRAI         = false;    // -rai
    private boolean rcaRenameRI          = false;    // -ri
    private boolean rcaNativeOnly        = false;    // -na
    private Integer rcaLimit;                        // -x
    private boolean rcaStoreExtended     = false;    // -e
    private boolean rcaStoreExtendedSteps = false;   // -es
    private boolean rcaBuildDot          = false;    // -dot
    private String  rcaDisplayMode       = "SIMPLIFIED"; // -d
    private boolean rcaStability         = false;    // -sta
    private boolean rcaBuildJson         = false;    // -json
    private boolean rcaBuildXml          = false;    // -xml
    private boolean rcaFullExtents       = false;    // -fe
    private boolean rcaFullIntents       = false;    // -fi
    private String  rcaDatalogFolder;                // -cd
    private String  rcaDatalogFile;                  // -cdu
    private boolean rcaNoDirectSiblings  = false;    // -nds

    // ── Setters fluents ──────────────────────────────────────────────────────
    public CommandBuilder command(String v)         { this.command = v;           return this; }
    public CommandBuilder inputFile(String v)       { this.inputFile = v;         return this; }
    public CommandBuilder outputFile(String v)      { this.outputFile = v;        return this; }
    public CommandBuilder algorithm(String v)       { this.algorithm = v;         return this; }
    public CommandBuilder inputFormat(String v)     { this.inputFormat = v;       return this; }
    public CommandBuilder outputFormat(String v)    { this.outputFormat = v;      return this; }
    public CommandBuilder implementation(String v)  { this.implementation = v;    return this; }
    public CommandBuilder separator(String v)       { this.separator = v;         return this; }
    public CommandBuilder timeout(int v)            { this.timeout = v;           return this; }
    public CommandBuilder verbose(boolean v)        { this.verbose = v;           return this; }
    // LATTICE / AOCPOSET
    public CommandBuilder dotFile(String v)         { this.dotFile = v;           return this; }
    public CommandBuilder displayMode(String v)     { this.displayMode = v;       return this; }
    public CommandBuilder stability(boolean v)      { this.stability = v;         return this; }
    public CommandBuilder icebergPercent(int v)     { this.icebergPercent = v;    return this; }
    public CommandBuilder datalogFolder(String v)   { this.datalogFolder = v;     return this; }
    public CommandBuilder datalogFile(String v)     { this.datalogFile = v;       return this; }
    public CommandBuilder noDirectSiblings(boolean v){ this.noDirectSiblings = v; return this; }
    // RULEBASIS
    public CommandBuilder clarify(boolean v)        { this.clarify = v;           return this; }
    public CommandBuilder closureMethod(String v)   { this.closureMethod = v;     return this; }
    public CommandBuilder poolMode(String v)        { this.poolMode = v;          return this; }
    public CommandBuilder threadThreshold(int v)    { this.threadThreshold = v;   return this; }
    public CommandBuilder sortBySupport(boolean v)  { this.sortBySupport = v;     return this; }
    public CommandBuilder reportFile(String v)      { this.reportFile = v;        return this; }
    public CommandBuilder implFolder(String v)      { this.implFolder = v;        return this; }
    // DBASIS
    public CommandBuilder minimalSupport(int v)     { this.minimalSupport = v;    return this; }
    // CLARIFY / REDUCE
    public CommandBuilder clarifyObjects(boolean v)    { this.clarifyObjects = v;    return this; }
    public CommandBuilder clarifyAttributes(boolean v) { this.clarifyAttributes = v; return this; }
    public CommandBuilder groupByClasses(boolean v)    { this.groupByClasses = v;    return this; }
    // IRREDUCIBLE
    public CommandBuilder listObjects(boolean v)       { this.listObjects = v;       return this; }
    public CommandBuilder listAttributes(boolean v)    { this.listAttributes = v;    return this; }
    // RCA
    public CommandBuilder familyFormat(String v)           { this.familyFormat = v;           return this; }
    public CommandBuilder rcaClean(boolean v)              { this.rcaClean = v;               return this; }
    public CommandBuilder rcaRenameRA(boolean v)           { this.rcaRenameRA = v;            return this; }
    public CommandBuilder rcaRenameRAI(boolean v)          { this.rcaRenameRAI = v;           return this; }
    public CommandBuilder rcaRenameRI(boolean v)           { this.rcaRenameRI = v;            return this; }
    public CommandBuilder rcaNativeOnly(boolean v)         { this.rcaNativeOnly = v;          return this; }
    public CommandBuilder rcaLimit(int v)                  { this.rcaLimit = v;               return this; }
    public CommandBuilder rcaStoreExtended(boolean v)      { this.rcaStoreExtended = v;       return this; }
    public CommandBuilder rcaStoreExtendedEachStep(boolean v){ this.rcaStoreExtendedSteps = v; return this; }
    public CommandBuilder rcaBuildDot(boolean v)           { this.rcaBuildDot = v;            return this; }
    public CommandBuilder rcaBuildJson(boolean v)          { this.rcaBuildJson = v;           return this; }
    public CommandBuilder rcaBuildXml(boolean v)           { this.rcaBuildXml = v;            return this; }
    public CommandBuilder rcaFullExtents(boolean v)        { this.rcaFullExtents = v;         return this; }
    public CommandBuilder rcaFullIntents(boolean v)        { this.rcaFullIntents = v;         return this; }
    public CommandBuilder rcaDisplayMode(String v)         { this.rcaDisplayMode = v;          return this; }
    public CommandBuilder rcaStability(boolean v)          { this.rcaStability = v;            return this; }
    public CommandBuilder rcaDatalogFolder(String v)       { this.rcaDatalogFolder = v;        return this; }
    public CommandBuilder rcaDatalogFile(String v)         { this.rcaDatalogFile = v;          return this; }
    public CommandBuilder rcaNoDirectSiblings(boolean v)   { this.rcaNoDirectSiblings = v;     return this; }
    // BINARIZE
    public CommandBuilder excludeAttrs(java.util.List<String> v) { this.excludeAttrs = v; return this; }
    public CommandBuilder includeAttrs(java.util.List<String> v) { this.includeAttrs = v; return this; }
    // IMPORT
    public CommandBuilder familyImportModelFormat(String v) {
        this.familyImportModelFormat = v; return this;
    }
    // ── Construction ─────────────────────────────────────────────────────────

    public List<String> build() {
        if (command == null || command.isBlank())
            throw new IllegalStateException("Commande non définie");
        if (inputFile == null || inputFile.isBlank())
            throw new IllegalStateException("Fichier d'entrée non défini");

        List<String> args = new ArrayList<>();

        // Positionnels
        args.add(command);
        args.add(inputFile);
        if (outputFile != null && !outputFile.isBlank())
            args.add(outputFile);

        // Algorithme (-a pour LATTICE/AOCPOSET/RULEBASIS ; -t pour pool RULEBASIS/DBASIS)
        if (algorithm != null && !algorithm.isBlank())
            add(args, "-a", algorithm);

        // Options LATTICE uniquement
        if (icebergPercent != null)
            add(args, "-p", String.valueOf(icebergPercent));

        // Format d'entrée
        if (inputFormat != null && !inputFormat.isBlank())
            add(args, "-i", inputFormat);

        // Format de sortie — on émet uniquement si différent du défaut de la commande
        String defaultOut = defaultOutputFormat();
        if (outputFormat != null && !outputFormat.equals(defaultOut))
            add(args, "-o", outputFormat);

        // DOT (LATTICE / AOCPOSET)
        if (dotFile != null && !dotFile.isBlank()) {
            add(args, "-g", dotFile);
            if (!"SIMPLIFIED".equals(displayMode))
                add(args, "-d", displayMode);
            if (stability) args.add("-sta");
        }

        // Implémentation interne
        if (implementation != null && !implementation.isBlank()
                && !"BITSET".equals(implementation))
            add(args, "-m", implementation);

        // Séparateur CSV
        if (separator != null && !separator.isBlank() && !"COMMA".equals(separator))
            add(args, "-s", separator);

        // Datalog (LATTICE / AOCPOSET)
        if (datalogFolder != null && !datalogFolder.isBlank())
            add(args, "-cd", datalogFolder);
        if (datalogFile != null && !datalogFile.isBlank())
            add(args, "-cdu", datalogFile);
        if (noDirectSiblings) args.add("-nds");

        // ── Options RULEBASIS ────────────────────────────────────────────────
        if (clarify) args.add("-clarify");

        if (closureMethod != null && !closureMethod.isBlank()
                && !"BASIC".equals(closureMethod))
            add(args, "-c", closureMethod);

        if (poolMode != null && !poolMode.isBlank()
                && !"MONO".equals(poolMode))
            add(args, "-t", poolMode);

        if (threadThreshold != null && threadThreshold != 50)
            add(args, "-h", String.valueOf(threadThreshold));

        if (sortBySupport) args.add("-b");

        if (reportFile != null && !reportFile.isBlank())
            add(args, "-r", reportFile);

        if (implFolder != null && !implFolder.isBlank())
            add(args, "-folder", implFolder);

        // ── Options DBASIS ───────────────────────────────────────────────────
        if (minimalSupport != null && minimalSupport > 0)
            add(args, "-x", String.valueOf(minimalSupport));

        // ── Options CLARIFY / REDUCE ──────────────────────────────────────────
        if (clarifyObjects)    args.add("-xo");
        if (clarifyAttributes) args.add("-xa");
        if (groupByClasses)    args.add("-u");

        // ── Options IRREDUCIBLE ───────────────────────────────────────────────
        if (listObjects)    args.add("-lobj");
        if (listAttributes) args.add("-lattr");

        // ── Options BINARIZE ──────────────────────────────────────────────────
        for (String attr : excludeAttrs) { args.add("-excl"); args.add(attr); }
        for (String attr : includeAttrs) { args.add("-incl"); args.add(attr); }
        
     // ── Options FAMILY_IMPORT ─────────────────────────────────────────────────
        if (familyImportModelFormat != null
                && !familyImportModelFormat.isBlank()
                && !"JSON".equals(familyImportModelFormat))
            add(args, "-m", familyImportModelFormat);
        
        // ── Options RCA ───────────────────────────────────────────────────────
        if (familyFormat != null && !familyFormat.isBlank() && !"RCFT".equals(familyFormat))
            add(args, "-f", familyFormat);
        if (rcaClean)              args.add("-clean");
        if (rcaRenameRA)           args.add("-ra");
        if (rcaRenameRAI)          args.add("-rai");
        if (rcaRenameRI)           args.add("-ri");
        if (rcaNativeOnly)         args.add("-na");
        if (rcaLimit != null && rcaLimit > 0) add(args, "-x", String.valueOf(rcaLimit));
        if (rcaStoreExtended)      args.add("-e");
        if (rcaStoreExtendedSteps) args.add("-es");
        if (rcaBuildDot) {
            args.add("-dot");
            if (rcaDisplayMode != null && !"SIMPLIFIED".equals(rcaDisplayMode))
                add(args, "-d", rcaDisplayMode);
            if (rcaStability) args.add("-sta");
        }
        if (rcaBuildJson)          args.add("-json");
        if (rcaBuildXml)           args.add("-xml");
        if (rcaFullExtents)        args.add("-fe");
        if (rcaFullIntents)        args.add("-fi");
        if (rcaDatalogFolder != null && !rcaDatalogFolder.isBlank())
            add(args, "-cd", rcaDatalogFolder);
        if (rcaDatalogFile != null && !rcaDatalogFile.isBlank())
            add(args, "-cdu", rcaDatalogFile);
        if (rcaNoDirectSiblings)   args.add("-nds");

        // Timeout
        if (timeout != null && timeout > 0)
            add(args, "-timeout", String.valueOf(timeout));

        // Verbose
        if (verbose) args.add("-v");

        return args;
    }

    public String toDisplayString() {
        return "java -jar fca4j.jar " + String.join(" ", build());
    }

    // ── Utilitaires privés ────────────────────────────────────────────────────

    private static void add(List<String> args, String flag, String value) {
        args.add(flag);
        args.add(value);
    }

    /** Retourne le format de sortie par défaut selon la commande. */
    private String defaultOutputFormat() {
        return switch (command) {
            case "RULEBASIS", "DBASIS" -> "TXT";
            default                    -> "XML";
        };
    }
}
