package fr.lirmm.fca4j.ui.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link CommandBuilder}.
 */
class CommandBuilderTest {

    // ── Validations ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("build() sans commande lève IllegalStateException")
    void buildWithoutCommand() {
        CommandBuilder b = new CommandBuilder().inputFile("input.cxt");
        assertThrows(IllegalStateException.class, b::build);
    }

    @Test
    @DisplayName("build() sans fichier d'entrée lève IllegalStateException")
    void buildWithoutInput() {
        CommandBuilder b = new CommandBuilder().command("LATTICE");
        assertThrows(IllegalStateException.class, b::build);
    }

    // ── LATTICE ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("LATTICE minimal : commande + input seulement")
    void latticeMinimal() {
        List<String> args = new CommandBuilder()
                .command("LATTICE").inputFile("ctx.cxt")
                .build();
        assertEquals(List.of("LATTICE", "ctx.cxt"), args);
    }

    @Test
    @DisplayName("LATTICE complet avec output, algo, DOT, stability, iceberg")
    void latticeFull() {
        List<String> args = new CommandBuilder()
                .command("LATTICE").inputFile("ctx.cxt").outputFile("out.xml")
                .algorithm("ICEBERG").icebergPercent(25)
                .dotFile("graph.dot").displayMode("FULL").stability(true)
                .build();

        assertTrue(args.contains("LATTICE"));
        assertTrue(args.contains("ctx.cxt"));
        assertTrue(args.contains("out.xml"));
        assertEquals("ICEBERG", args.get(args.indexOf("-a") + 1));
        assertEquals("25", args.get(args.indexOf("-p") + 1));
        assertEquals("graph.dot", args.get(args.indexOf("-g") + 1));
        assertEquals("FULL", args.get(args.indexOf("-d") + 1));
        assertTrue(args.contains("-sta"));
    }

    @Test
    @DisplayName("LATTICE : le format de sortie XML par défaut n'est pas émis")
    void latticeDefaultOutputFormatOmitted() {
        List<String> args = new CommandBuilder()
                .command("LATTICE").inputFile("ctx.cxt").outputFormat("XML")
                .build();
        assertFalse(args.contains("-o"));
    }

    @Test
    @DisplayName("LATTICE : format de sortie JSON est émis")
    void latticeNonDefaultOutputFormat() {
        List<String> args = new CommandBuilder()
                .command("LATTICE").inputFile("ctx.cxt").outputFormat("JSON")
                .build();
        assertEquals("JSON", args.get(args.indexOf("-o") + 1));
    }

    // ── AOCPOSET ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AOCPOSET avec algorithme HERMES")
    void aocposet() {
        List<String> args = new CommandBuilder()
                .command("AOCPOSET").inputFile("ctx.cxt").algorithm("HERMES")
                .build();
        assertEquals("AOCPOSET", args.get(0));
        assertEquals("HERMES", args.get(args.indexOf("-a") + 1));
    }

    // ── RULEBASIS ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RULEBASIS : format TXT par défaut non émis")
    void rulebasisDefaultFormat() {
        List<String> args = new CommandBuilder()
                .command("RULEBASIS").inputFile("ctx.cxt").outputFormat("TXT")
                .build();
        assertFalse(args.contains("-o"));
    }

    @Test
    @DisplayName("RULEBASIS complet avec toutes les options")
    void rulebasisFull() {
        List<String> args = new CommandBuilder()
                .command("RULEBASIS").inputFile("ctx.cxt").outputFile("rules.txt")
                .algorithm("LINCBOPRUNING")
                .clarify(true).closureMethod("WITH_HISTORY")
                .poolMode("FORKJOINPOOL").threadThreshold(100)
                .sortBySupport(true)
                .reportFile("report.txt").implFolder("/tmp/results")
                .build();

        assertTrue(args.contains("-clarify"));
        assertEquals("WITH_HISTORY", args.get(args.indexOf("-c") + 1));
        assertEquals("FORKJOINPOOL", args.get(args.indexOf("-t") + 1));
        assertEquals("100", args.get(args.indexOf("-h") + 1));
        assertTrue(args.contains("-b"));
        assertEquals("report.txt", args.get(args.indexOf("-r") + 1));
        assertEquals("/tmp/results", args.get(args.indexOf("-folder") + 1));
    }

    @Test
    @DisplayName("RULEBASIS : threshold par défaut 50 non émis")
    void rulebasisDefaultThreshold() {
        List<String> args = new CommandBuilder()
                .command("RULEBASIS").inputFile("ctx.cxt")
                .poolMode("FORKJOINPOOL").threadThreshold(50)
                .build();
        assertFalse(args.contains("-h"));
    }

    // ── DBASIS ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DBASIS avec support minimal")
    void dbasis() {
        List<String> args = new CommandBuilder()
                .command("DBASIS").inputFile("ctx.cxt").minimalSupport(10)
                .build();
        assertEquals("10", args.get(args.indexOf("-x") + 1));
    }

    @Test
    @DisplayName("DBASIS : support minimal 0 non émis")
    void dbasisZeroSupport() {
        List<String> args = new CommandBuilder()
                .command("DBASIS").inputFile("ctx.cxt").minimalSupport(0)
                .build();
        assertFalse(args.contains("-x"));
    }

    // ── CLARIFY / REDUCE ─────────────────────────────────────────────────────

    @Test
    @DisplayName("CLARIFY avec objets et attributs")
    void clarify() {
        List<String> args = new CommandBuilder()
                .command("CLARIFY").inputFile("ctx.cxt")
                .clarifyObjects(true).clarifyAttributes(true)
                .build();
        assertTrue(args.contains("-xo"));
        assertTrue(args.contains("-xa"));
    }

    @Test
    @DisplayName("REDUCE avec groupByClasses")
    void reduce() {
        List<String> args = new CommandBuilder()
                .command("REDUCE").inputFile("ctx.cxt")
                .clarifyObjects(true).groupByClasses(true)
                .build();
        assertTrue(args.contains("-xo"));
        assertTrue(args.contains("-u"));
    }

    // ── IRREDUCIBLE ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("IRREDUCIBLE avec listes objets et attributs")
    void irreducible() {
        List<String> args = new CommandBuilder()
                .command("IRREDUCIBLE").inputFile("ctx.cxt")
                .listObjects(true).listAttributes(true)
                .build();
        assertTrue(args.contains("-lobj"));
        assertTrue(args.contains("-lattr"));
    }

    // ── BINARIZE ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("BINARIZE avec include et exclude")
    void binarize() {
        List<String> args = new CommandBuilder()
                .command("BINARIZE").inputFile("data.csv")
                .excludeAttrs(List.of("id", "name"))
                .includeAttrs(List.of("color"))
                .build();

        int excl1 = args.indexOf("-excl");
        assertEquals("id", args.get(excl1 + 1));
        assertEquals("-excl", args.get(excl1 + 2));
        assertEquals("name", args.get(excl1 + 3));
        assertEquals("color", args.get(args.indexOf("-incl") + 1));        
    }

    // ── Options communes ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Format d'entrée explicite")
    void inputFormat() {
        List<String> args = new CommandBuilder()
                .command("LATTICE").inputFile("ctx.csv").inputFormat("CSV")
                .build();
        assertEquals("CSV", args.get(args.indexOf("-i") + 1));
    }

    @Test
    @DisplayName("Séparateur COMMA par défaut non émis")
    void separatorDefault() {
        List<String> args = new CommandBuilder()
                .command("LATTICE").inputFile("ctx.csv").separator("COMMA")
                .build();
        assertFalse(args.contains("-s"));
    }

    @Test
    @DisplayName("Séparateur SEMICOLON émis")
    void separatorSemicolon() {
        List<String> args = new CommandBuilder()
                .command("LATTICE").inputFile("ctx.csv").separator("SEMICOLON")
                .build();
        assertEquals("SEMICOLON", args.get(args.indexOf("-s") + 1));
    }

    @Test
    @DisplayName("Implémentation BITSET par défaut non émise")
    void implementationDefault() {
        List<String> args = new CommandBuilder()
                .command("LATTICE").inputFile("ctx.cxt").implementation("BITSET")
                .build();
        assertFalse(args.contains("-m"));
    }

    @Test
    @DisplayName("Implémentation non-default émise")
    void implementationNonDefault() {
        List<String> args = new CommandBuilder()
                .command("LATTICE").inputFile("ctx.cxt").implementation("ROARING_BITMAP")
                .build();
        assertEquals("ROARING_BITMAP", args.get(args.indexOf("-m") + 1));
    }

    @Test
    @DisplayName("Timeout > 0 émis, timeout 0 non émis")
    void timeout() {
        List<String> args1 = new CommandBuilder()
                .command("LATTICE").inputFile("ctx.cxt").timeout(120)
                .build();
        assertEquals("120", args1.get(args1.indexOf("-timeout") + 1));

        List<String> args2 = new CommandBuilder()
                .command("LATTICE").inputFile("ctx.cxt").timeout(0)
                .build();
        assertFalse(args2.contains("-timeout"));
    }

    @Test
    @DisplayName("Verbose activé")
    void verbose() {
        List<String> args = new CommandBuilder()
                .command("LATTICE").inputFile("ctx.cxt").verbose(true)
                .build();
        assertTrue(args.contains("-v"));
    }

    @Test
    @DisplayName("Verbose désactivé par défaut")
    void verboseDefault() {
        List<String> args = new CommandBuilder()
                .command("LATTICE").inputFile("ctx.cxt")
                .build();
        assertFalse(args.contains("-v"));
    }

    // ── Datalog ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Options Datalog folder, file, nds")
    void datalog() {
        List<String> args = new CommandBuilder()
                .command("LATTICE").inputFile("ctx.cxt")
                .datalogFolder("/tmp/dl").datalogFile("all.dlgp")
                .noDirectSiblings(true)
                .build();
        assertEquals("/tmp/dl", args.get(args.indexOf("-cd") + 1));
        assertEquals("all.dlgp", args.get(args.indexOf("-cdu") + 1));
        assertTrue(args.contains("-nds"));
    }

    // ── RCA ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RCA avec options principales")
    void rca() {
        List<String> args = new CommandBuilder()
                .command("RCA").inputFile("family.rcft")
                .rcaClean(true).rcaRenameRA(true).rcaNativeOnly(true)
                .rcaLimit(5).rcaStoreExtended(true)
                .rcaBuildDot(true).rcaDisplayMode("FULL").rcaStability(true)
                .rcaBuildJson(true).rcaBuildXml(true)
                .rcaFullExtents(true).rcaFullIntents(true)
                .build();

        assertTrue(args.contains("-clean"));
        assertTrue(args.contains("-ra"));
        assertTrue(args.contains("-na"));
        assertEquals("5", args.get(args.indexOf("-x") + 1));
        assertTrue(args.contains("-e"));
        assertTrue(args.contains("-dot"));
        assertEquals("FULL", args.get(args.indexOf("-d") + 1));
        assertTrue(args.contains("-sta"));
        assertTrue(args.contains("-json"));
        assertTrue(args.contains("-xml"));
        assertTrue(args.contains("-fe"));
        assertTrue(args.contains("-fi"));
    }

    @Test
    @DisplayName("RCA : format famille non-default émis")
    void rcaFamilyFormat() {
        List<String> args = new CommandBuilder()
                .command("RCA").inputFile("family.rcfal").familyFormat("RCFAL")
                .build();
        assertEquals("RCFAL", args.get(args.indexOf("-f") + 1));
    }

    @Test
    @DisplayName("RCA : format RCFT par défaut non émis")
    void rcaFamilyFormatDefault() {
        List<String> args = new CommandBuilder()
                .command("RCA").inputFile("family.rcft").familyFormat("RCFT")
                .build();
        assertFalse(args.contains("-f"));
    }

    // ── FAMILY_IMPORT ────────────────────────────────────────────────────────

    @Test
    @DisplayName("FAMILY_IMPORT : format JSON par défaut non émis")
    void familyImportDefaultFormat() {
        List<String> args = new CommandBuilder()
                .command("FAMILY_IMPORT").inputFile("model.json")
                .familyImportModelFormat("JSON")
                .build();
        assertFalse(args.contains("-m"));
    }

    @Test
    @DisplayName("FAMILY_IMPORT : format XML émis")
    void familyImportXmlFormat() {
        List<String> args = new CommandBuilder()
                .command("FAMILY_IMPORT").inputFile("model.xml")
                .familyImportModelFormat("XML")
                .build();
        assertEquals("XML", args.get(args.indexOf("-m") + 1));
    }

    // ── toDisplayString ──────────────────────────────────────────────────────

    @Test
    @DisplayName("toDisplayString commence par java -jar fca4j.jar")
    void displayString() {
        String display = new CommandBuilder()
                .command("LATTICE").inputFile("ctx.cxt")
                .toDisplayString();
        assertTrue(display.startsWith("java -jar fca4j.jar LATTICE ctx.cxt"));
    }
}
