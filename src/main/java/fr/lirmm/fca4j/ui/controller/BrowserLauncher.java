/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.ui.controller;

import com.sun.net.httpserver.HttpServer;

import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.BrowserRegistry;
import fr.lirmm.fca4j.ui.util.BrowserRegistry.Browser;
import fr.lirmm.fca4j.ui.util.BrowserRegistry.Os;

import javafx.application.Platform;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Gère l'ouverture d'URLs dans le navigateur et les serveurs HTTP locaux
 * pour RCAViz et FCAvizIR.
 *
 * <h2>Choix du navigateur</h2>
 * L'utilisateur peut imposer un navigateur dans les préférences. À défaut,
 * on passe par l'association du système, qui respecte son navigateur par
 * défaut. La détection et le lancement sont délégués à
 * {@link BrowserRegistry}.
 *
 * <h2>Passage de données à RCAViz / FCAvizIR</h2>
 * Les deux outils sont servis en HTTPS et récupèrent le fichier via un
 * paramètre {@code ?data=http://127.0.0.1:PORT/...}. Il s'agit donc d'une
 * sous-ressource http chargée depuis une page https (« mixed content »).
 * La spécification W3C exempte les adresses de bouclage, et Chrome, Firefox
 * et Edge appliquent cette exemption — <b>mais pas WebKit</b>
 * (bug WebKit 171934, toujours ouvert). Sous Safari la requête est rejetée
 * avec « due to access control checks » et la page reste vide.
 *
 * <p>Conséquence : pour ces deux URLs seulement, et uniquement lorsque
 * l'utilisateur n'a pas imposé de navigateur, on tente d'abord un navigateur
 * non-WebKit sous macOS. Si aucun n'est disponible — ou si l'utilisateur a
 * explicitement choisi un navigateur WebKit — on bascule en dépôt manuel :
 * le fichier est révélé dans le Finder, à déposer dans la page.</p>
 */
public class BrowserLauncher {

    private HttpServer rcavizServer;
    private int rcavizPort;
    private HttpServer fcavizirServer;
    private int fcavizirPort;

    /** Callback (titre, message) pour afficher une alerte. */
    private final BiConsumer<String, String> onError;

    /** Trace optionnelle (console applicative). */
    private volatile Consumer<String> logger = msg -> { };

    public BrowserLauncher(BiConsumer<String, String> onError) {
        this.onError = onError;
    }

    /**
     * Branche une trace pour diagnostiquer les échanges avec le serveur local.
     * Typiquement : {@code browserLauncher.setLogger(this::appendConsole)}.
     */
    public void setLogger(Consumer<String> logger) {
        this.logger = (logger != null) ? logger : msg -> { };
    }

    // ── Ouverture d'URL, usage général ───────────────────────────────────────

    /**
     * Ouvre une URL dans le navigateur choisi par l'utilisateur, ou à défaut
     * dans le navigateur par défaut du système.
     * <p>Appel <b>asynchrone</b> : ne bloque jamais le thread JavaFX.</p>
     */
    public void openUrlWithFallback(String url) {
        runAsync(() -> {
            if (!openUrlBlocking(url)) {
                reportError("Browser", "Impossible d'ouvrir le navigateur pour :\n" + url);
            }
        });
    }

    /** Variante <b>bloquante</b> : à réserver aux appels hors thread JavaFX. */
    public boolean openUrl(String url) {
        return openUrlBlocking(url);
    }

    private boolean openUrlBlocking(String url) {
        Browser preferred = resolvePreferred();
        if (preferred != null) {
            if (BrowserRegistry.launch(preferred, url)) {
                log("ouverture dans " + preferred.name());
                return true;
            }
            log("échec du lancement de " + preferred.name()
                + ", retour au navigateur par défaut");
        }
        if (BrowserRegistry.launchSystemDefault(url)) return true;
        return BrowserRegistry.desktopBrowse(url);
    }

    /**
     * Navigateur imposé dans les préférences.
     *
     * @return null si aucun choix, ou si le navigateur choisi a disparu
     */
    private Browser resolvePreferred() {
        String id = AppPreferences.getPreferredBrowserId();
        if (id == null || id.isBlank()) return null;

        Browser browser = BrowserRegistry.byId(id);
        if (browser == null) {
            log("le navigateur choisi dans les préférences n'est plus installé ("
                + id + ") — utilisation du navigateur par défaut");
        }
        return browser;
    }

    // ── RCAViz ───────────────────────────────────────────────────────────────

    public void openInRcaviz(Path jsonFile) {
        runAsync(() -> {
            try {
                int port = startServer(jsonFile, "application/json", true);
                String url = "https://rcaviz.lirmm.fr/?data=http://" + loopbackHost()
                    + ":" + port + "/" + encodeFileName(jsonFile);
                log("RCAViz : serveur local sur " + loopbackHost() + ":" + port
                    + " pour " + jsonFile.getFileName());
                openVisualizationUrl("RCAViz", url, jsonFile);
            } catch (Exception e) {
                reportError("RCAViz", String.valueOf(e.getMessage()));
            }
        });
    }

    // ── FCAvizIR ─────────────────────────────────────────────────────────────

    public void openInFcavizir(Path txtFile) {
        runAsync(() -> {
            try {
                int port = startServer(txtFile, "text/plain; charset=utf-8", false);
                String url = "https://fcavizir.lirmm.fr/?data=http://" + loopbackHost()
                    + ":" + port + "/" + encodeFileName(txtFile);
                log("FCAvizIR : serveur local sur " + loopbackHost() + ":" + port
                    + " pour " + txtFile.getFileName());
                openVisualizationUrl("FCAvizIR", url, txtFile);
            } catch (Exception e) {
                reportError("FCAvizIR", String.valueOf(e.getMessage()));
            }
        });
    }

    /**
     * Ouvre une URL de visualisation, qui dépend d'un chargement de
     * sous-ressource depuis le bouclage local.
     *
     * @param tool     nom de l'outil, pour les messages
     * @param url      URL complète avec le paramètre data
     * @param dataFile fichier à révéler en cas de dépôt manuel
     */
    private void openVisualizationUrl(String tool, String url, Path dataFile) {
        Browser preferred = resolvePreferred();

        // 1. Choix explicite de l'utilisateur : il fait foi.
        if (preferred != null) {
            boolean launched = BrowserRegistry.launch(preferred, url);
            if (!launched) {
                log("échec du lancement de " + preferred.name());
                if (!openUrlBlocking(url)) {
                    reportError(tool, "Impossible d'ouvrir le navigateur pour :\n" + url);
                }
                return;
            }
            log("ouverture dans " + preferred.name());
            if (preferred.webkit()) {
                // On a lancé quand même, mais le chargement va échouer.
                fallbackToManualDrop(tool, preferred.name(), dataFile);
            }
            return;
        }

        // 2. Mode automatique. Sous macOS, le navigateur par défaut est
        //    fréquemment Safari, qui bloquerait la requête vers le bouclage.
        if (BrowserRegistry.OS == Os.MAC) {
            Browser alternative = firstNonWebKit();
            if (alternative != null && BrowserRegistry.launch(alternative, url)) {
                log("navigateur retenu pour la visualisation : " + alternative.name());
                return;
            }
            log(tool + " : aucun navigateur non-WebKit détecté, passage en dépôt manuel");
            openUrlBlocking(url);
            fallbackToManualDrop(tool, "Safari", dataFile);
            return;
        }

        // 3. Windows et Linux : le navigateur par défaut convient.
        if (!openUrlBlocking(url)) {
            reportError(tool, "Impossible d'ouvrir le navigateur pour :\n" + url);
        }
    }

    private Browser firstNonWebKit() {
        for (Browser b : BrowserRegistry.installed()) {
            if (!b.webkit()) return b;
        }
        return null;
    }

    /**
     * Mode dégradé : le navigateur utilisé ne peut pas charger les données
     * depuis le serveur local. On révèle le fichier pour que l'utilisateur
     * le dépose lui-même dans la page.
     */
    private void fallbackToManualDrop(String tool, String browserName, Path dataFile) {
        revealInFileManager(dataFile);
        reportError(tool,
            browserName + " ne peut pas charger les données depuis le serveur local\n"
            + "(restriction WebKit sur le contenu mixte, bug 171934).\n\n"
            + "Le fichier suivant a été révélé dans l'explorateur :\n"
            + dataFile.getFileName() + "\n\n"
            + "Déposez-le dans la page " + tool + " pour poursuivre, ou choisissez\n"
            + "Chrome, Firefox ou Edge dans Fichier > Préférences.");
    }

    /** Ouvre l'explorateur de fichiers en sélectionnant le fichier. */
    private void revealInFileManager(Path file) {
        try {
            Path absolute = file.toAbsolutePath();
            switch (BrowserRegistry.OS) {
                case MAC ->
                    BrowserRegistry.runAndWait(null, "/usr/bin/open", "-R", absolute.toString());
                case WINDOWS ->
                    BrowserRegistry.runDetached(null, "explorer.exe", "/select," + absolute);
                default -> {
                    Path parent = absolute.getParent();
                    String exe = BrowserRegistry.findOnPath("xdg-open");
                    if (exe != null && parent != null) {
                        BrowserRegistry.runAndWait(BrowserRegistry::sanitizeLinuxEnv,
                                                   exe, parent.toString());
                    }
                }
            }
        } catch (Exception e) {
            log("révélation du fichier impossible : " + e.getMessage());
        }
    }

    /**
     * Le serveur sert le même fichier quel que soit le chemin demandé ; on peut
     * donc encoder librement le nom, ce qui évite qu'un espace ou un accent
     * ne casse l'URL transmise à l'outil.
     */
    private static String encodeFileName(Path file) {
        return URLEncoder.encode(file.getFileName().toString(), StandardCharsets.UTF_8)
                         .replace("+", "%20");
    }

    // ── Serveurs HTTP locaux ─────────────────────────────────────────────────

    private synchronized int startServer(Path file, String contentType, boolean isRcaviz)
            throws Exception {
        if (isRcaviz) {
            if (rcavizServer != null) rcavizServer.stop(0);
            rcavizServer = createFileServer(file, contentType);
            rcavizPort = rcavizServer.getAddress().getPort();
            return rcavizPort;
        }
        if (fcavizirServer != null) fcavizirServer.stop(0);
        fcavizirServer = createFileServer(file, contentType);
        fcavizirPort = fcavizirServer.getAddress().getPort();
        return fcavizirPort;
    }

    private HttpServer createFileServer(Path file, String contentType)
            throws Exception {
        // Bouclage uniquement : le fichier de contexte n'a aucune raison
        // d'être exposé au réseau local, et cela évite la demande
        // d'autorisation du pare-feu applicatif macOS.
        HttpServer server = HttpServer.create(
            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);

        server.createContext("/", exchange -> {
            String method = exchange.getRequestMethod();
            log("HTTP " + method + " " + exchange.getRequestURI()
                + "  Origin=" + header(exchange.getRequestHeaders().getFirst("Origin"))
                + "  UA=" + header(exchange.getRequestHeaders().getFirst("User-Agent")));

            var headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");
            headers.add("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
            headers.add("Access-Control-Allow-Headers", "*");
            headers.add("Cache-Control", "no-store");

            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            byte[] bytes;
            try {
                bytes = Files.readAllBytes(file);
            } catch (IOException e) {
                log("lecture impossible : " + e.getMessage());
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
                return;
            }

            headers.add("Content-Type", contentType);

            if ("HEAD".equals(method)) {
                headers.add("Content-Length", String.valueOf(bytes.length));
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }

            // -1 et non 0 pour un corps vide : 0 signifierait « longueur inconnue ».
            exchange.sendResponseHeaders(200, bytes.length == 0 ? -1 : bytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            log("  -> " + bytes.length + " octets servis");
        });

        server.setExecutor(null);
        server.start();
        return server;
    }

    /** Arrête les serveurs HTTP locaux. À appeler dans shutdown(). */
    public synchronized void stopServers() {
        if (rcavizServer != null) rcavizServer.stop(0);
        if (fcavizirServer != null) fcavizirServer.stop(0);
    }

    /** Adresse littérale de bouclage, crochetée si IPv6. */
    private static String loopbackHost() {
        InetAddress addr = InetAddress.getLoopbackAddress();
        String host = addr.getHostAddress();
        return (addr instanceof Inet6Address) ? "[" + host + "]" : host;
    }

    private static String header(String value) {
        return (value == null) ? "-" : value;
    }

    // ── Utilitaires internes ─────────────────────────────────────────────────

    private static void runAsync(Runnable task) {
        Thread t = new Thread(task, "browser-launcher");
        t.setDaemon(true);
        t.start();
    }

    private void log(String message) {
        Consumer<String> l = logger;
        if (l == null) return;
        if (Platform.isFxApplicationThread()) {
            l.accept("[viz] " + message);
        } else {
            Platform.runLater(() -> l.accept("[viz] " + message));
        }
    }

    private void reportError(String title, String message) {
        if (onError == null) return;
        if (Platform.isFxApplicationThread()) {
            onError.accept(title, message);
        } else {
            Platform.runLater(() -> onError.accept(title, message));
        }
    }
}
