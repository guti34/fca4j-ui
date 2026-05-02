/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.ui.controller;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Gère l'ouverture d'URLs dans le navigateur et les serveurs HTTP locaux
 * pour RCAViz et FCAvizIR.
 * Extrait de MainController pour réduire sa taille.
 */
public class BrowserLauncher {

    private HttpServer rcavizServer;
    private int rcavizPort;
    private HttpServer fcavizirServer;
    private int fcavizirPort;

    /** Callback (titre, message) pour afficher une alerte en cas d'erreur. */
    private final BiConsumer<String, String> onError;

    /**
     * @param onError callback appelé en cas d'erreur (titre, message)
     */
    public BrowserLauncher(BiConsumer<String, String> onError) {
        this.onError = onError;
    }

    // ── Ouverture d'URL dans le navigateur ───────────────────────────────────

    /**
     * Lance le navigateur via ProcessBuilder pour contourner la limite
     * de 2048 chars de ShellExecuteW (Desktop.browse sur Windows).
     * Tente Chrome, Firefox, Edge dans cet ordre.
     *
     * @return true si un navigateur a été lancé, false sinon
     */
    public boolean openUrl(String url) {
        String os = System.getProperty("os.name", "").toLowerCase();
        List<String[]> candidates = new ArrayList<>();

        if (os.contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            candidates.add(new String[]{
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe", url});
            candidates.add(new String[]{
                "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe", url});
            if (local != null)
                candidates.add(new String[]{
                    local + "\\Google\\Chrome\\Application\\chrome.exe", url});
            candidates.add(new String[]{
                "C:\\Program Files\\Mozilla Firefox\\firefox.exe", url});
            candidates.add(new String[]{
                "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe", url});
        } else if (os.contains("mac")) {
            candidates.add(new String[]{"open", "-a", "Google Chrome", url});
            candidates.add(new String[]{"open", url});
        } else {
            // Linux : lancer dans un thread séparé pour ne pas bloquer JavaFX
            new Thread(() -> {
                try {
                    new ProcessBuilder("xdg-open", url)
                        .directory(new java.io.File("/"))
                        .redirectErrorStream(true)
                        .start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
            return true;
        }

        for (String[] cmd : candidates) {
            try {
                java.io.File exe = new java.io.File(cmd[0]);
                if (cmd[0].contains(java.io.File.separator) && !exe.exists())
                    continue;
                new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();
                return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    /**
     * Ouvre une URL dans le navigateur avec fallback sur Desktop.browse().
     */
    public void openUrlWithFallback(String url) {
        if (!openUrl(url)) {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            } catch (Exception e) {
                onError.accept("Browser", e.getMessage());
            }
        }
    }

    // ── RCAViz ───────────────────────────────────────────────────────────────

    public void openInRcaviz(Path jsonFile) {
        try {
            startServer(jsonFile, "application/json", true);
            String url = "https://rcaviz.lirmm.fr/?data=http://localhost:"
                + rcavizPort + "/" + jsonFile.getFileName();
            openUrlWithFallback(url);
        } catch (Exception e) {
            onError.accept("RCAViz", e.getMessage());
        }
    }

    // ── FCAvizIR ─────────────────────────────────────────────────────────────

    public void openInFcavizir(Path txtFile) {
        try {
            startServer(txtFile, "text/plain; charset=utf-8", false);
            String url = "https://fcavizir.lirmm.fr/?data=http://localhost:"
                + fcavizirPort + "/" + txtFile.getFileName();
            openUrlWithFallback(url);
        } catch (Exception e) {
            onError.accept("FCAvizIR", e.getMessage());
        }
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
