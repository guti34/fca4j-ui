/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.ui.controller;

import com.sun.net.httpserver.HttpServer;

import javafx.application.Platform;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Gère l'ouverture d'URLs dans le navigateur et les serveurs HTTP locaux
 * pour RCAViz et FCAvizIR.
 *
 * <p>Stratégie d'ouverture : on privilégie systématiquement le mécanisme
 * d'association du système (qui respecte le navigateur par défaut de
 * l'utilisateur), et on ne retombe sur des chemins codés en dur qu'en cas
 * d'échec avéré — c'est-à-dire code de sortie non nul, pas simplement
 * « le processus a démarré ».</p>
 */
public class BrowserLauncher {

    private enum Os { WINDOWS, MAC, LINUX }

    private static final Os OS = detectOs();

    /** Limite pratique de ShellExecuteW, utilisé par Desktop.browse() sous Windows. */
    private static final int SHELL_EXECUTE_LIMIT = 1800;

    /** Au-delà, on considère que le lanceur a rendu la main « assez longtemps ». */
    private static final long LAUNCHER_TIMEOUT_MS = 5_000L;

    private HttpServer rcavizServer;
    private int rcavizPort;
    private HttpServer fcavizirServer;
    private int fcavizirPort;

    /** Callback (titre, message) pour afficher une alerte en cas d'erreur. */
    private final BiConsumer<String, String> onError;

    /**
     * @param onError callback appelé en cas d'erreur (titre, message).
     *                Il sera toujours invoqué sur le thread JavaFX.
     */
    public BrowserLauncher(BiConsumer<String, String> onError) {
        this.onError = onError;
    }

    // ── API publique ─────────────────────────────────────────────────────────

    /**
     * Ouvre une URL dans le navigateur par défaut de l'utilisateur.
     * <p>L'appel est <b>asynchrone</b> : il retourne immédiatement et ne bloque
     * jamais le thread d'application JavaFX. Une éventuelle erreur est signalée
     * via le callback {@code onError}.</p>
     */
    public void openUrlWithFallback(String url) {
        Thread t = new Thread(() -> {
            if (!openUrlBlocking(url)) {
                reportError("Browser", "Impossible d'ouvrir le navigateur pour :\n" + url);
            }
        }, "browser-launcher");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Variante <b>bloquante</b> : à réserver aux appels hors thread JavaFX
     * (tests, tâches de fond).
     *
     * @return true si un navigateur a effectivement été lancé
     */
    public boolean openUrl(String url) {
        return openUrlBlocking(url);
    }

    // ── Aiguillage par plateforme ────────────────────────────────────────────

    private boolean openUrlBlocking(String url) {
        switch (OS) {
            case MAC:   return openMac(url);
            case LINUX: return openLinux(url);
            default:    return openWindows(url);
        }
    }

    // ── macOS ────────────────────────────────────────────────────────────────

    private boolean openMac(String url) {
        // Chemin absolu : quand l'app est lancée par le Finder / LaunchServices,
        // le PATH hérité est minimal et parfois vide.
        // `open <url>` respecte le navigateur par défaut et met la fenêtre au premier plan.
        if (runAndWait(null, "/usr/bin/open", url)) {
            return true;
        }
        // Repli si aucune association http(s) n'est enregistrée (cas rare).
        if (runAndWait(null, "/usr/bin/open", "-a", "Safari", url)) {
            return true;
        }
        return desktopBrowse(url);
    }

    // ── Linux ────────────────────────────────────────────────────────────────

    private boolean openLinux(String url) {
        List<String[]> launchers = new ArrayList<>();
        launchers.add(new String[]{"xdg-open", url});
        launchers.add(new String[]{"gio", "open", url});
        launchers.add(new String[]{"kde-open5", url});
        launchers.add(new String[]{"gnome-open", url});
        launchers.add(new String[]{"x-www-browser", url});
        launchers.add(new String[]{"sensible-browser", url});

        for (String[] cmd : launchers) {
            String exe = findOnPath(cmd[0]);
            if (exe == null) continue;
            String[] resolved = cmd.clone();
            resolved[0] = exe;
            if (runAndWait(BrowserLauncher::sanitizeLinuxEnv, resolved)) {
                return true;
            }
        }
        return desktopBrowse(url);
    }

    /**
     * Nettoie l'environnement transmis au navigateur.
     *
     * <p>Deux familles de variables sont problématiques :</p>
     * <ul>
     *   <li>{@code DESKTOP_STARTUP_ID} / {@code XDG_ACTIVATION_TOKEN} : hérités de
     *       notre propre lancement, ils sont périmés. Le gestionnaire de fenêtres
     *       refuse alors d'activer la fenêtre du navigateur (protection contre le
     *       vol de focus) : l'URL s'ouvre mais reste en arrière-plan.</li>
     *   <li>{@code LD_LIBRARY_PATH} &amp; consorts : injectés par le lanceur
     *       jpackage, ils pointent vers les bibliothèques embarquées de l'app et
     *       peuvent faire échouer ou dégrader le démarrage du navigateur.</li>
     * </ul>
     */
    private static void sanitizeLinuxEnv(Map<String, String> env) {
        env.remove("DESKTOP_STARTUP_ID");
        env.remove("XDG_ACTIVATION_TOKEN");
        env.remove("LD_LIBRARY_PATH");
        env.remove("LD_PRELOAD");
        env.remove("GTK_PATH");
        env.remove("GIO_MODULE_DIR");
        env.remove("GDK_PIXBUF_MODULE_FILE");
        env.remove("GSETTINGS_SCHEMA_DIR");
        env.remove("JAVA_HOME");
    }

    // ── Windows ──────────────────────────────────────────────────────────────

    private boolean openWindows(String url) {
        boolean shortUrl = url.length() <= SHELL_EXECUTE_LIMIT;

        // Cas nominal : Desktop.browse() passe par ShellExecuteW et respecte
        // donc le navigateur par défaut.
        if (shortUrl && desktopBrowse(url)) {
            return true;
        }

        // URL trop longue (ou AWT indisponible) : on résout nous-mêmes le
        // navigateur par défaut via la base de registre, puis on le lance en
        // argv — ce qui échappe à la limite de 2048 caractères.
        String exe = findDefaultBrowserExe();
        if (exe != null && new File(exe).isFile() && runDetached(exe, url)) {
            return true;
        }

        for (String candidate : windowsFallbackExes()) {
            if (new File(candidate).isFile() && runDetached(candidate, url)) {
                return true;
            }
        }

        // Dernier recours : le gestionnaire de protocole, sans passer par cmd.exe
        // (dont les règles de quoting sont incompatibles avec ProcessBuilder).
        return shortUrl
            && runAndWait(null, "rundll32.exe", "url.dll,FileProtocolHandler", url);
    }

    /**
     * Résout l'exécutable du navigateur par défaut via
     * {@code UrlAssociations\https\UserChoice} puis {@code shell\open\command}.
     *
     * @return le chemin de l'exécutable, ou null si non déterminable
     */
    private static String findDefaultBrowserExe() {
        String progId = regQuery(
            "HKCU\\Software\\Microsoft\\Windows\\Shell\\Associations"
                + "\\UrlAssociations\\https\\UserChoice",
            "ProgId");
        if (progId == null || progId.isBlank()) return null;

        String command = regQuery("HKCR\\" + progId + "\\shell\\open\\command", null);
        if (command == null || command.isBlank()) return null;

        return extractExecutable(command);
    }

    /** Extrait le chemin de l'exécutable d'une ligne de commande du registre. */
    private static String extractExecutable(String command) {
        String c = command.trim();
        if (c.startsWith("\"")) {
            int end = c.indexOf('"', 1);
            return end > 1 ? c.substring(1, end) : null;
        }
        int sp = c.indexOf(' ');
        return sp > 0 ? c.substring(0, sp) : c;
    }

    /**
     * Interroge le registre. {@code valueName} à null pour la valeur par défaut.
     */
    private static String regQuery(String key, String valueName) {
        List<String> cmd = new ArrayList<>(List.of("reg.exe", "query", key));
        if (valueName == null) {
            cmd.add("/ve");
        } else {
            cmd.add("/v");
            cmd.add(valueName);
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out;
            try (var in = p.getInputStream()) {
                out = new String(in.readAllBytes(), consoleCharset());
            }
            if (!p.waitFor(LAUNCHER_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                return null;
            }
            for (String line : out.split("\\R")) {
                int idx = line.indexOf("REG_SZ");
                if (idx < 0) idx = line.indexOf("REG_EXPAND_SZ");
                if (idx < 0) continue;
                int typeLen = line.startsWith("REG_EXPAND_SZ", idx) ? 13 : 6;
                return line.substring(idx + typeLen).trim();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // registre inaccessible : on laissera jouer les replis
        }
        return null;
    }

    private static List<String> windowsFallbackExes() {
        List<String> list = new ArrayList<>();
        String local = System.getenv("LOCALAPPDATA");
        list.add("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe");
        list.add("C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe");
        if (local != null) {
            list.add(local + "\\Google\\Chrome\\Application\\chrome.exe");
        }
        list.add("C:\\Program Files\\Mozilla Firefox\\firefox.exe");
        list.add("C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe");
        return list;
    }

    // ── Primitives d'exécution ───────────────────────────────────────────────

    /**
     * Lance un <i>lanceur</i> (open, xdg-open, rundll32…) et attend son code de
     * sortie. Ces commandes rendent la main quasi immédiatement : leur code de
     * sortie est donc exploitable, contrairement au lancement direct d'un
     * navigateur (qui reste vivant tant que la fenêtre est ouverte).
     */
    private static boolean runAndWait(Consumer<Map<String, String>> envCustomizer,
                                      String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (envCustomizer != null) envCustomizer.accept(pb.environment());
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();
            if (!p.waitFor(LAUNCHER_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                // Ne rend pas la main : c'est probablement le navigateur lui-même.
                return true;
            }
            return p.exitValue() == 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** Lance un exécutable de navigateur sans attendre sa terminaison. */
    private static boolean runDetached(String... cmd) {
        try {
            new ProcessBuilder(cmd)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean desktopBrowse(String url) {
        try {
            if (!java.awt.Desktop.isDesktopSupported()) return false;
            java.awt.Desktop d = java.awt.Desktop.getDesktop();
            if (!d.isSupported(java.awt.Desktop.Action.BROWSE)) return false;
            d.browse(new URI(url));
            return true;
        } catch (Throwable t) {
            // Throwable et non Exception : NoClassDefFoundError si le runtime
            // jlink a été construit sans le module java.desktop.
            return false;
        }
    }

    private static String findOnPath(String name) {
        String path = System.getenv("PATH");
        if (path == null) return null;
        for (String dir : path.split(File.pathSeparator)) {
            if (dir.isBlank()) continue;
            File f = new File(dir, name);
            if (f.isFile() && f.canExecute()) return f.getAbsolutePath();
        }
        return null;
    }

    private static Os detectOs() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return Os.WINDOWS;
        if (os.contains("mac") || os.contains("darwin")) return Os.MAC;
        return Os.LINUX;
    }

    private static Charset consoleCharset() {
        try {
            String enc = System.getProperty("native.encoding");
            if (enc != null) return Charset.forName(enc);
        } catch (Exception ignored) {
            // jeu de caractères inconnu
        }
        return Charset.defaultCharset();
    }

    private void reportError(String title, String message) {
        if (onError == null) return;
        if (Platform.isFxApplicationThread()) {
            onError.accept(title, message);
        } else {
            Platform.runLater(() -> onError.accept(title, message));
        }
    }

    // ── RCAViz ───────────────────────────────────────────────────────────────

    public void openInRcaviz(Path jsonFile) {
        try {
            startServer(jsonFile, "application/json", true);
            String url = "https://rcaviz.lirmm.fr/?data=http://localhost:"
                + rcavizPort + "/" + encodeFileName(jsonFile);
            openUrlWithFallback(url);
        } catch (Exception e) {
            reportError("RCAViz", String.valueOf(e.getMessage()));
        }
    }

    // ── FCAvizIR ─────────────────────────────────────────────────────────────

    public void openInFcavizir(Path txtFile) {
        try {
            startServer(txtFile, "text/plain; charset=utf-8", false);
            String url = "https://fcavizir.lirmm.fr/?data=http://localhost:"
                + fcavizirPort + "/" + encodeFileName(txtFile);
            openUrlWithFallback(url);
        } catch (Exception e) {
            reportError("FCAvizIR", String.valueOf(e.getMessage()));
        }
    }

    /**
     * Le serveur sert le même fichier quel que soit le chemin demandé ; on peut
     * donc encoder librement le nom, ce qui évite qu'un espace ou un accent ne
     * casse l'URL transmise à RCAViz / FCAvizIR.
     */
    private static String encodeFileName(Path file) {
        return URLEncoder.encode(file.getFileName().toString(), StandardCharsets.UTF_8)
                         .replace("+", "%20");
    }

    // ── Serveurs HTTP locaux ─────────────────────────────────────────────────

    private void startServer(Path file, String contentType, boolean isRcaviz)
            throws Exception {
        if (isRcaviz) {
            if (rcavizServer != null) rcavizServer.stop(0);
            rcavizServer = createFileServer(file, contentType);
            rcavizPort = rcavizServer.getAddress().getPort();
        } else {
            if (fcavizirServer != null) fcavizirServer.stop(0);
            fcavizirServer = createFileServer(file, contentType);
            fcavizirPort = fcavizirServer.getAddress().getPort();
        }
    }

    private HttpServer createFileServer(Path file, String contentType)
            throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            exchange.getResponseHeaders().add("Content-Type", contentType);
            byte[] bytes = Files.readAllBytes(file);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.setExecutor(null);
        server.start();
        return server;
    }

    /** Arrête les serveurs HTTP locaux. À appeler dans shutdown(). */
    public void stopServers() {
        if (rcavizServer != null) rcavizServer.stop(0);
        if (fcavizirServer != null) fcavizirServer.stop(0);
    }
}
