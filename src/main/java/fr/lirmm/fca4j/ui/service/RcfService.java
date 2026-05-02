/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.ui.service;

import fr.lirmm.fca4j.cli.io.RCFTReader;
import fr.lirmm.fca4j.cli.io.RCFTWriter;
import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.iset.std.BitSetFactory;
import fr.lirmm.fca4j.util.ConceptOrderFinder;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Facade autour des readers/writers RCFT et de la commande FAMILY CLI.
 * Gère la lecture/écriture de RCAFamily et les opérations via FCA4J.
 */
public class RcfService {

    public enum FamilyFormat { RCFT, RCFGZ, RCFAL }

    // ── Lecture ───────────────────────────────────────────────────────────────

    public RCAFamily read(Path path) throws Exception {
    	FamilyFormat format=suggestFamilyFormat(path.getFileName().toString());
        return read(path,format);
    }

    public RCAFamily read(Path path, FamilyFormat format) throws Exception {
        return switch (format) {
            case RCFT  -> RCFTReader.read(path.toString(), false);
            case RCFGZ -> RCFTReader.read(path.toString(), true);
            case RCFAL -> throw new UnsupportedOperationException(
                "RCFAL format not yet supported for direct reading — use FAMILY CLI");
        };
    }
	/**
	 * Suggest family format.
	 *
	 * @param filename the filename
	 * @return the suggested family format
	 */
	protected static FamilyFormat suggestFamilyFormat(String filename) {
		int beginIndex = filename.lastIndexOf('.');
		if (beginIndex >= 0) {
			switch (filename.substring(beginIndex + 1).toUpperCase()) {
			case "RCF":
			case "RCFT":
				return FamilyFormat.RCFT;
			case "RCFGZ":
			case "RCFTGZ":
				return FamilyFormat.RCFGZ;
			case "JSON":
			case "RCFAL":
			case "RCFTAL":
				return FamilyFormat.RCFAL;
			}
		}
		return null;
	}

    // ── Écriture ──────────────────────────────────────────────────────────────

    public void write(RCAFamily family, Path path) throws Exception {
    	FamilyFormat format=suggestFamilyFormat(path.getFileName().toString());
        write(family, path, format);
    }

    public void write(RCAFamily family, Path path, FamilyFormat format) throws Exception {
        switch (format) {
            case RCFT  -> RCFTWriter.write(family, path.toString(), false, null);
            case RCFGZ -> RCFTWriter.write(family, path.toString(), true,  null);
            case RCFAL -> throw new UnsupportedOperationException(
                "RCFAL write not yet supported directly — use FAMILY CLI");
        }
    }

    // ── Création d'une famille vide ───────────────────────────────────────────

    public RCAFamily createEmpty(String name) {
        return new RCAFamily(name, new BitSetFactory());
    }

    // ── Opérations FAMILY via CLI ─────────────────────────────────────────────

    /**
     * Invoque la commande FAMILY via le JAR CLI pour les opérations complexes
     * (IMPORT avec opérateur de scaling, EXPORT, RENAME).
     * Les opérations simples (add/remove) sont gérées directement via l'API RCAFamily.
     */
    public CompletableFuture<Fca4jRunner.RunResult> familyImport(
            Path familyFile, Path contextFile,
            String contextName, String sourceName, String targetName,
            String operator, String contextFormat,
            Fca4jRunner runner) {

        List<String> args = new ArrayList<>(List.of(
            "FAMILY", familyFile.toString(), contextFile.toString(),
            "-a", "IMPORT",
            "-n", contextName,
            "-source", sourceName,
            "-target", targetName,
            "-op", operator
        ));
        if (contextFormat != null && !contextFormat.isBlank())
            args.addAll(List.of("-x", contextFormat));

        return runner.run(args);
    }

    public CompletableFuture<Fca4jRunner.RunResult> familyRename(
            Path familyFile, String oldName, String newName,
            Fca4jRunner runner) {

        return runner.run(List.of(
            "FAMILY", familyFile.toString(),
            "-a", "RENAME",
            "-n", oldName,
            "-new", newName
        ));
    }

    public CompletableFuture<Fca4jRunner.RunResult> familyExport(
            Path familyFile, String contextName,
            Path outputFile, String contextFormat,
            Fca4jRunner runner) {

        List<String> args = new ArrayList<>(List.of(
            "FAMILY", familyFile.toString(),
            "-a", "EXPORT",
            "-n", contextName
        ));
        if (outputFile != null)   args.add(outputFile.toString());
        if (contextFormat != null) args.addAll(List.of("-x", contextFormat));
        return runner.run(args);
    }

    // ── Formats ───────────────────────────────────────────────────────────────

    public enum FamilyFormatHelper {;
        public static FamilyFormat fromFile(File f) {
            String name = f.getName().toLowerCase();
            if (name.endsWith(".rcfgz")) return FamilyFormat.RCFGZ;
            if (name.endsWith(".rcfal")) return FamilyFormat.RCFAL;
            return FamilyFormat.RCFT;
        }

        public static String extension(FamilyFormat fmt) {
            return switch (fmt) {
                case RCFT  -> ".rcft";
                case RCFGZ -> ".rcfgz";
                case RCFAL -> ".rcfal";
            };
        }
    }
}
