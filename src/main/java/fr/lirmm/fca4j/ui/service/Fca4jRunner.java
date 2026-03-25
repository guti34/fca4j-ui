package fr.lirmm.fca4j.ui.service;

import fr.lirmm.fca4j.ui.util.AppPreferences;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Wraps les appels au JAR CLI fca4j via ProcessBuilder.
 * Toutes les exécutions sont asynchrones pour ne pas bloquer le thread JavaFX.
 */
public class Fca4jRunner {
	
	private volatile Process currentProcess = null;
	
    /** Résultat d'une exécution FCA4J. */
    public record RunResult(int exitCode, String stdout, String stderr) {
        public boolean isSuccess() { return exitCode == 0; }
    }

    /**
     * Exécute une commande FCA4J de façon asynchrone.
     *
     * @param args      Arguments CLI (ex: ["LATTICE", "input.cxt", "-o", "out"])
     * @param onLine    Callback appelé pour chaque ligne de stdout (peut être null)
     * @return          Future du résultat
     */
    public CompletableFuture<RunResult> run(List<String> args, Consumer<String> onLine) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String jarPath = AppPreferences.getFca4jJarPath();
                if (jarPath == null || jarPath.isBlank()) {
                    throw new IllegalStateException(
                        "Chemin du JAR FCA4J non configuré. " +
                        "Allez dans Préférences pour le définir."
                    );
                }

                List<String> command = new ArrayList<>();
                command.add("java");
                command.add("-jar");
                command.add(jarPath);
                command.addAll(args);

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(false);

                Process process = pb.start();
                this.currentProcess = process;
                // Lecture stdout
                StringBuilder stdoutBuf = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdoutBuf.append(line).append("\n");
                        if (onLine != null) {
                            final String l = line;
                            onLine.accept(l);
                        }
                    }
                }

                // Lecture stderr
                StringBuilder stderrBuf = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderrBuf.append(line).append("\n");
                    }
                }

                int exitCode = process.waitFor();
                this.currentProcess = null;
                return new RunResult(exitCode, stdoutBuf.toString(), stderrBuf.toString());

            } catch (Exception e) {
                this.currentProcess = null;
                return new RunResult(-1, "", e.getMessage());
            }
        });
    }
    public void cancel() {
        Process p = currentProcess;
        if (p != null && p.isAlive()) {
            // Tuer récursivement tous les processus enfants puis le parent
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
            currentProcess = null;
        }
    }
    public boolean isRunning() {
        return currentProcess != null && currentProcess.isAlive();
    }
    /** Raccourci sans callback de ligne. */
    public CompletableFuture<RunResult> run(List<String> args) {
        return run(args, null);
    }

    // ── Commandes FCA4J courantes ─────────────────────────────────────────────

    public CompletableFuture<RunResult> buildLattice(Path input, Path output, String... options) {
        List<String> args = new ArrayList<>(List.of("LATTICE", input.toString(), output.toString()));
        args.addAll(List.of(options));
        return run(args);
    }

    public CompletableFuture<RunResult> buildAocPoset(Path input, Path output, String... options) {
        List<String> args = new ArrayList<>(List.of("AOCPOSET", input.toString(), output.toString()));
        args.addAll(List.of(options));
        return run(args);
    }

    public CompletableFuture<RunResult> inspect(Path input) {
        return run(List.of("INSPECT", input.toString()));
    }

    public CompletableFuture<RunResult> ruleBasis(Path input, Path output, String... options) {
        List<String> args = new ArrayList<>(List.of("RULEBASIS", input.toString(), output.toString()));
        args.addAll(List.of(options));
        return run(args);
    }
}