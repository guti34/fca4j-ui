package fr.lirmm.fca4j.ui.service;

import fr.lirmm.fca4j.cli.io.*;
import fr.lirmm.fca4j.core.BinaryContext;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.iset.std.BitSetFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;

/**
 * Facade autour des readers/writers de fca4j-io.
 * Gère la détection automatique du format selon l'extension.
 */
public class ContextIOService {

    public enum ContextFormat {
        CXT, CEX, SLF, CSV, XML;

        /** Détecte le format depuis l'extension du fichier. */
        public static ContextFormat fromFile(File file) {
            String name = file.getName().toLowerCase();
            if (name.endsWith(".cxt"))  return CXT;
            if (name.endsWith(".cex"))  return CEX;
            if (name.endsWith(".slf"))  return SLF;
            if (name.endsWith(".csv"))  return CSV;
            if (name.endsWith(".xml"))  return XML;
            return CXT; // défaut
        }

        public String getExtension() {
            return switch (this) {
                case CXT -> ".cxt";
                case CEX -> ".cex";
                case SLF -> ".slf";
                case CSV -> ".csv";
                case XML -> ".xml";
            };
        }
    }

    private final ISetFactory factory;
    private char separator = ',';

    public ContextIOService() {
        this.factory = new BitSetFactory();
    }
    // for write usage only, read use detection
    public void setSeparator(char separator) {
        this.separator = separator;
    }

    // ── Lecture ───────────────────────────────────────────────────────────────

    /**
     * Lit un contexte formel depuis un fichier.
     * Le format est déduit automatiquement de l'extension.
     */
    public IBinaryContext read(Path path) throws Exception {
        return read(path.toFile(), ContextFormat.fromFile(path.toFile()));
    }

    /**
     * Lit un contexte formel depuis un fichier avec format explicite.
     */
    public IBinaryContext read(Path path, ContextFormat format) throws Exception {
        return read(path.toFile(), format);
    }

    private IBinaryContext read(File file, ContextFormat format) throws Exception {
        switch (format) {
            case CXT:return CXTReader.read(file, factory);
            case CEX:return ConExpReader.read(file, factory).get(0);
            case SLF:return SLFReader.read(file, factory);
            case CSV:{
                char sep = CSVUtilities.detectSeparator(file);
                IBinaryContext ctx = MyCSVReader.read(file, sep, factory);
                ctx.setName(file.getName().replaceFirst("\\.[^.]+$", ""));
                return ctx;
            }
            case XML:return GaliciaXMLReader.read(file, factory);
            default: return null;
        }
    }

    // ── Écriture ──────────────────────────────────────────────────────────────

    /**
     * Écrit un contexte formel dans un fichier.
     * Le format est déduit automatiquement de l'extension.
     */
    public void write(IBinaryContext context, Path path) throws Exception {
        write(context, path.toFile(), ContextFormat.fromFile(path.toFile()));
    }

    /**
     * Écrit un contexte formel dans un fichier avec format explicite.
     */
    public void write(IBinaryContext context, Path path, ContextFormat format) throws Exception {
        write(context, path.toFile(), format);
    }

    private void write(IBinaryContext context, File file, ContextFormat format) throws Exception {
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        switch (format) {
            case CXT -> CXTWriter.writeContext(writer, context);
            case CEX -> ConExpWriter.writeContext(writer, context);
            case SLF -> SLFWriter.writeContext(writer, context);
            case CSV -> MyCSVWriter.writeContext(writer, context, separator);
            case XML -> GaliciaWriter.write(writer, context);
        }
    }

    // ── Création d'un contexte vide ───────────────────────────────────────────

    /**
     * Crée un contexte formel vide avec le nom donné.
     */
    public IBinaryContext createEmpty(String name) {
        return new BinaryContext(0, 0, name, factory);
    }

    public ISetFactory getFactory() {
        return factory;
    }
}
