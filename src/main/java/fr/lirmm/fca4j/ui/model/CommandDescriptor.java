package fr.lirmm.fca4j.ui.model;

import java.util.List;

/**
 * Décrit une commande FCA4J : ses algorithmes, formats, et options disponibles.
 */
public class CommandDescriptor {

    public enum InputFormat  { CXT, SLF, XML, CEX, CSV }
    public enum OutputFormat { XML, JSON, TXT, DATALOG }
    public enum DisplayMode  { FULL, SIMPLIFIED, MINIMAL }
    public enum Separator    { COMMA, SEMICOLON, TAB }

    /** Famille de commande : détermine quel panneau FXML charger. */
    public enum CommandFamily { LATTICE_AOC, RULE_BASIS }

    private final String        name;
    private final String        description;
    private final List<String>  algorithms;
    private final String        defaultAlgorithm;
    private final boolean       hasIcebergPercent;
    private final CommandFamily family;

    public CommandDescriptor(String name, String description,
                             List<String> algorithms, String defaultAlgorithm,
                             boolean hasIcebergPercent, CommandFamily family) {
        this.name              = name;
        this.description       = description;
        this.algorithms        = algorithms;
        this.defaultAlgorithm  = defaultAlgorithm;
        this.hasIcebergPercent = hasIcebergPercent;
        this.family            = family;
    }

    public String        getName()             { return name; }
    public String        getDescription()      { return description; }
    public List<String>  getAlgorithms()       { return algorithms; }
    public String        getDefaultAlgorithm() { return defaultAlgorithm; }
    public boolean       hasIcebergPercent()   { return hasIcebergPercent; }
    public CommandFamily getFamily()           { return family; }

    // ── Descripteurs ─────────────────────────────────────────────────────────

    public static final CommandDescriptor LATTICE = new CommandDescriptor(
        "LATTICE",
        "Construit un treillis de concepts.",
        List.of("AUTO", "ADD_EXTENT", "ADD_INTENT", "ICEBERG"),
        "AUTO", true, CommandFamily.LATTICE_AOC
    );

    public static final CommandDescriptor AOCPOSET = new CommandDescriptor(
        "AOCPOSET",
        "Construit le sous-ordre AOC-poset.",
        List.of("HERMES", "ARES", "CERES", "PLUTON"),
        "HERMES", false, CommandFamily.LATTICE_AOC
    );

    public static final CommandDescriptor RULEBASIS = new CommandDescriptor(
        "RULEBASIS",
        "Calcule la base canonique d'implications (Duquenne-Guigues).",
        List.of("LINCBOPRUNING", "LINCBO"),
        "LINCBOPRUNING", false, CommandFamily.RULE_BASIS
    );

    public static final CommandDescriptor DBASIS = new CommandDescriptor(
        "DBASIS",
        "Calcule la base directe ordonnée d'implications (D-Basis).",
        List.of("MULTITHREAD", "MONO"),
        "MULTITHREAD", false, CommandFamily.RULE_BASIS
    );

    /** Retourne le descripteur pour un nom de commande, ou null. */
    public static CommandDescriptor forName(String name) {
        return switch (name) {
            case "LATTICE"   -> LATTICE;
            case "AOCPOSET"  -> AOCPOSET;
            case "RULEBASIS" -> RULEBASIS;
            case "DBASIS"    -> DBASIS;
            default          -> null;
        };
    }
}
