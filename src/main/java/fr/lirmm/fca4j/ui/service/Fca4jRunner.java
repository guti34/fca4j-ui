/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.ui.service;

import fr.lirmm.fca4j.ui.util.AppPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Wraps les appels au JAR CLI fca4j via ProcessBuilder.
 * Par défaut, utilise le JAR embarqué dans les ressources.
 * Si l'utilisateur active l'option "Use external JAR" dans les préférences,
 * le chemin configuré est utilisé à la place.
 * Toutes les exécutions sont asynchrones pour ne pas bloquer le thread JavaFX.
 */
public class Fca4jRunner {

    private static final String EMBEDDED_JAR_RESOURCE = "/fr/lirmm/fca4j/ui/bin/fca4j.jar";

    private volatile Process currentProcess = null;
    private static Path embeddedJarPath;

    /** Résultat d'une exécution FCA4J. */
    public record RunResult(int exitCode, String stdout, String stderr) {
        public boolean isSuccess() { return exitCode == 0; }
    }

    // ── JAR embarqué ─────────────────────────────────────────────────────────

    /**
     * Extrait le JAR embarqué vers un répertoire temporaire (une seule fois).
     * @return le chemin vers le JAR extrait
     * @throws IOException si l'extraction échoue
     */
    private static synchronized Path getEmbeddedJar() throws IOException {
        if (embeddedJarPath != null && embeddedJarPath.toFile().exists())
            return embeddedJarPath;

        InputStream is = Fca4jRunner.class.getResourceAsStream(EMBEDDED_JAR_RESOURCE);
        if (is == null)
            throw new IOException("Embedded FCA4J JAR not found in resources: " + EMBEDDED_JAR_RESOURCE);

        Path tmpDir = Files.createTempDirectory("fca4j-embedded");
        embeddedJarPath = tmpDir.resolve("fca4j.jar");
        Files.copy(is, embeddedJarPath, StandardCopyOption.REPLACE_EXISTING);
        is.close();
        embeddedJarPath.toFile().deleteOnExit();
        tmpDir.toFile().deleteOnExit();
        return embeddedJarPath;
    }

    /**
     * Retourne true si le JAR embarqué est disponible dans les ressources.
     */
    public static boolean hasEmbeddedJar() {
        return Fca4jRunner.class.getResource(EMBEDDED_JAR_RESOURCE) != null;
    }

    // ── Résolution du JAR à utiliser ─────────────────────────────────────────

    /**
     * Détermine le chemin du JAR FCA4J à utiliser :
     * - si l'utilisateur a activé "use external JAR" et qu'un chemin est configuré, utiliser celui-ci
     * - sinon, utiliser le JAR embarqué
     */
    private String resolveFca4jJar() throws IOException {
        if (AppPreferences.isUseExternalFca4j()) {
            String externalPath = AppPreferences.getFca4jJarPath();
            if (externalPath != null && !externalPath.isBlank() && new File(externalPath).exists())
                return externalPath;
        }
        return getEmbeddedJar().toString();
    }

    /**
     * Retourne true si FCA4J est utilisable (JAR embarqué disponible ou JAR externe configuré).
     */
    public static boolean isConfigured() {
        if (hasEmbeddedJar()) return true;
        if (AppPreferences.isUseExternalFca4j()) {
            String path = AppPreferences.getFca4jJarPath();
            return path != null && !path.isBlank() && new File(path).exists();
        }
        return false;
    }

    // ── Exécution ────────────────────────────────────────────────────────────

    /**
     * Exécute une commande FCA4J de façon asynchrone.
     *
     * @param args   Arguments CLI (ex: ["LATTICE", "input.cxt", "-o", "out"])
     * @param onLine Callback appelé pour chaque ligne de stdout (peut être null)
     * @return       Future du résultat
     */
    public CompletableFuture<RunResult> run(List<String> args, Consumer<String> onLine) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String jarPath = resolveFca4jJar();

                List<String> command = new ArrayList<>();
                command.add(currentJavaExe());
                command.add("-jar");
                command.add(jarPath);
                command.addAll(args);

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(false);
                Process process = pb.start();
                this.currentProcess = process;

                StringBuilder stdoutBuf = new StringBuilder();
                StringBuilder stderrBuf = new StringBuilder();

                // Lire stderr en parallèle pour éviter les deadlocks
                Thread stderrThread = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getErrorStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null)
                            stderrBuf.append(line).append("\n");
                    } catch (Exception ignored) {}
                }, "fca4j-stderr");
                stderrThread.setDaemon(true);
                stderrThread.start();

                // Lire stdout sur le thread courant
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

                stderrThread.join();
                int exitCode = process.waitFor();
                this.currentProcess = null;
                return new RunResult(exitCode, stdoutBuf.toString(), stderrBuf.toString());

            } catch (Exception e) {
                this.currentProcess = null;
                return new RunResult(-1, "", e.getMessage());
            }
        });
    }

    /**
     * Retourne le chemin absolu vers le java(.exe) qui fait tourner cette JVM.
     */
    private static String currentJavaExe() {
        String javaHome = System.getProperty("java.home");
        String exe = System.getProperty("os.name", "").toLowerCase()
                         .contains("win") ? "java.exe" : "java";
        File candidate = new File(javaHome, "bin/" + exe);
        if (candidate.exists()) return candidate.getAbsolutePath();
        return "java";
    }

    public void cancel() {
        Process p = currentProcess;
        if (p != null && p.isAlive()) {
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
    /**
     * Retourne la version de FCA4J embarquée, lue depuis le package fca4j-core.
     * Tente successivement : Package.getImplementationVersion(),
     * pom.properties de Maven, puis "?" en dernier recours.
     */
    public static String getEmbeddedVersion() {
        // 1. Via le manifest (Implementation-Version)
        Package pkg = fr.lirmm.fca4j.core.IBinaryContext.class.getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null)
            return pkg.getImplementationVersion();

        // 2. Via pom.properties embarqué par Maven
        try (InputStream is = fr.lirmm.fca4j.core.IBinaryContext.class.getResourceAsStream(
                "/META-INF/maven/fr.lirmm.fca4j/fca4j-core/pom.properties")) {
            if (is != null) {
                java.util.Properties props = new java.util.Properties();
                props.load(is);
                String v = props.getProperty("version");
                if (v != null) return v;
            }
        } catch (Exception ignored) {}

        // 3. Fallback
        return "?";
    }}
