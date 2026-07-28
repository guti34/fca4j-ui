/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.ui.util;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Détection des navigateurs installés et primitives de lancement.
 *
 * <p>La détection est mise en cache après le premier appel : elle interroge la
 * base de registre sous Windows et Spotlight sous macOS, ce qui prend quelques
 * centaines de millisecondes. Appeler {@link #refresh()} pour la relancer.</p>
 *
 * <p><b>Attention WebKit.</b> Les navigateurs marqués {@link Browser#webkit()}
 * refusent de charger une sous-ressource http:// depuis une page https://, même
 * sur l'adresse de bouclage (bug WebKit 171934). RCAViz et FCAvizIR, qui
 * récupèrent leurs données via un serveur local, ne fonctionnent pas avec
 * eux.</p>
 */
public final class BrowserRegistry {

    public enum Os { WINDOWS, MAC, LINUX }

    /** Manière de lancer un navigateur. */
    public enum Kind {
        /** Association du système : on ne nomme aucun navigateur. */
        SYSTEM_DEFAULT,
        /** macOS : identifiant de bundle, lancé via {@code open -b}. */
        MAC_BUNDLE,
        /** Windows / Linux : chemin absolu d'un exécutable. */
        EXECUTABLE
    }

    /**
     * Description d'un navigateur sélectionnable.
     *
     * @param id      identifiant stable, persisté dans les préférences
     * @param name    libellé affiché ; vide pour le navigateur par défaut
     * @param kind    mode de lancement
     * @param target  identifiant de bundle ou chemin de l'exécutable
     * @param webkit  true si le moteur est WebKit (incompatible RCAViz/FCAvizIR)
     */
    public record Browser(String id, String name, Kind kind, String target, boolean webkit) {

        /** Entrée « navigateur par défaut du système ». */
        public static Browser systemDefault() {
            return new Browser("", "", Kind.SYSTEM_DEFAULT, "", false);
        }

        public boolean isSystemDefault() {
            return kind == Kind.SYSTEM_DEFAULT;
        }
    }

    public static final Os OS = detectOs();

    /** Délai au-delà duquel on considère qu'un lanceur a fait son travail. */
    private static final long LAUNCHER_TIMEOUT_MS = 5_000L;

    private static volatile List<Browser> cache;

    private BrowserRegistry() { }

    // ── Détection ────────────────────────────────────────────────────────────

    /**
     * Liste des navigateurs installés, sans l'entrée « par défaut ».
     * Peut être longue à s'exécuter au premier appel : à lancer hors du
     * thread JavaFX.
     */
    public static List<Browser> installed() {
        List<Browser> local = cache;
        if (local == null) {
            synchronized (BrowserRegistry.class) {
                local = cache;
                if (local == null) {
                    local = switch (OS) {
                        case WINDOWS -> detectWindows();
                        case MAC     -> detectMac();
                        case LINUX   -> detectLinux();
                    };
                    cache = local;
                }
            }
        }
        return local;
    }

    /** Force une nouvelle détection au prochain appel. */
    public static void refresh() {
        cache = null;
    }

    /**
     * Retrouve un navigateur par son identifiant persisté.
     *
     * @return le navigateur, ou {@code null} s'il n'est plus installé
     */
    public static Browser byId(String id) {
        if (id == null || id.isBlank()) return null;
        for (Browser b : installed()) {
            if (b.id().equals(id)) return b;
        }
        return null;
    }

    // ── Windows ──────────────────────────────────────────────────────────────

    /**
     * Énumère {@code Clients\StartMenuInternet}, l'emplacement où tout
     * navigateur Windows correctement installé se déclare.
     */
    private static List<Browser> detectWindows() {
        Map<String, Browser> byExe = new LinkedHashMap<>();
        List<String> roots = List.of(
            "HKLM\\SOFTWARE\\Clients\\StartMenuInternet",
            "HKLM\\SOFTWARE\\WOW6432Node\\Clients\\StartMenuInternet",
            "HKCU\\SOFTWARE\\Clients\\StartMenuInternet");

        for (String root : roots) {
            for (String sub : regSubKeys(root)) {
                String key = root + "\\" + sub;
                String exe = extractExecutable(regQuery(key + "\\shell\\open\\command", null));
                if (exe == null || !new File(exe).isFile()) continue;

                String canonical = new File(exe).getAbsolutePath();
                if (byExe.containsKey(canonical.toLowerCase(Locale.ROOT))) continue;

                String name = regQuery(key, null);
                if (name == null || name.isBlank()) name = sub;

                byExe.put(canonical.toLowerCase(Locale.ROOT),
                    new Browser("exe:" + canonical, name, Kind.EXECUTABLE, canonical, false));
            }
        }

        if (byExe.isEmpty()) {
            // Repli si le registre est illisible (poste verrouillé, profil exotique).
            for (String path : List.of(
                    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                    "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
                    "C:\\Program Files\\Mozilla Firefox\\firefox.exe",
                    "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe")) {
                if (new File(path).isFile()) {
                    byExe.put(path.toLowerCase(Locale.ROOT),
                        new Browser("exe:" + path, new File(path).getName(),
                                    Kind.EXECUTABLE, path, false));
                }
            }
        }
        return List.copyOf(byExe.values());
    }

    // ── macOS ────────────────────────────────────────────────────────────────

    /** (identifiant de bundle, nom d'application, moteur WebKit). */
    private static final List<String[]> MAC_KNOWN = List.of(
        new String[]{"com.google.Chrome",          "Google Chrome",   "false"},
        new String[]{"org.mozilla.firefox",        "Firefox",         "false"},
        new String[]{"com.microsoft.edgemac",      "Microsoft Edge",  "false"},
        new String[]{"com.brave.Browser",          "Brave Browser",   "false"},
        new String[]{"com.vivaldi.Vivaldi",        "Vivaldi",         "false"},
        new String[]{"com.operasoftware.Opera",    "Opera",           "false"},
        new String[]{"company.thebrowser.Browser", "Arc",             "false"},
        new String[]{"org.chromium.Chromium",      "Chromium",        "false"},
        new String[]{"com.apple.Safari",           "Safari",          "true"}
    );

    private static List<Browser> detectMac() {
        List<Browser> list = new ArrayList<>();
        for (String[] entry : MAC_KNOWN) {
            String bundleId = entry[0];
            String appName  = entry[1];
            boolean webkit  = Boolean.parseBoolean(entry[2]);
            if (macAppExists(bundleId, appName)) {
                list.add(new Browser("bundle:" + bundleId, appName,
                                     Kind.MAC_BUNDLE, bundleId, webkit));
            }
        }
        return List.copyOf(list);
    }

    private static boolean macAppExists(String bundleId, String appName) {
        // Spotlight d'abord : trouve l'application où qu'elle soit installée.
        String found = capture("/usr/bin/mdfind",
            "kMDItemCFBundleIdentifier == '" + bundleId + "'");
        if (found != null && !found.isBlank()) return true;

        // Repli si Spotlight est désactivé ou l'index incomplet.
        String home = System.getProperty("user.home", "");
        for (String dir : List.of("/Applications", home + "/Applications",
                                  "/System/Applications",
                                  "/System/Cryptexes/App/System/Applications")) {
            if (Files.isDirectory(Path.of(dir, appName + ".app"))) return true;
        }
        return false;
    }

    // ── Linux ────────────────────────────────────────────────────────────────

    /** (nom d'exécutable, libellé, moteur WebKit). */
    private static final List<String[]> LINUX_KNOWN = List.of(
        new String[]{"google-chrome-stable", "Google Chrome",    "false"},
        new String[]{"google-chrome",        "Google Chrome",    "false"},
        new String[]{"chromium",             "Chromium",         "false"},
        new String[]{"chromium-browser",     "Chromium",         "false"},
        new String[]{"firefox",              "Firefox",          "false"},
        new String[]{"firefox-esr",          "Firefox ESR",      "false"},
        new String[]{"microsoft-edge",       "Microsoft Edge",   "false"},
        new String[]{"microsoft-edge-stable","Microsoft Edge",   "false"},
        new String[]{"brave-browser",        "Brave Browser",    "false"},
        new String[]{"vivaldi-stable",       "Vivaldi",          "false"},
        new String[]{"opera",                "Opera",            "false"},
        new String[]{"epiphany",             "GNOME Web",        "true"},
        new String[]{"epiphany-browser",     "GNOME Web",        "true"}
    );

    private static List<Browser> detectLinux() {
        Map<String, Browser> byName = new LinkedHashMap<>();
        for (String[] entry : LINUX_KNOWN) {
            String exe = findOnPath(entry[0]);
            if (exe == null) continue;
            // Chrome et Chromium se déclinent en plusieurs noms d'exécutable :
            // on ne garde que le premier trouvé pour chaque libellé.
            byName.putIfAbsent(entry[1],
                new Browser("exe:" + exe, entry[1], Kind.EXECUTABLE, exe,
                            Boolean.parseBoolean(entry[2])));
        }
        return List.copyOf(byName.values());
    }

    // ── Lancement ────────────────────────────────────────────────────────────

    /**
     * Lance une URL dans un navigateur nommé.
     *
     * @return true si le navigateur a effectivement été lancé
     */
    public static boolean launch(Browser browser, String url) {
        if (browser == null || browser.isSystemDefault()) return false;
        return switch (browser.kind()) {
            case MAC_BUNDLE -> runAndWait(null, "/usr/bin/open", "-b", browser.target(), url);
            case EXECUTABLE -> (OS == Os.LINUX)
                ? runDetached(BrowserRegistry::sanitizeLinuxEnv, browser.target(), url)
                : runDetached(null, browser.target(), url);
            default -> false;
        };
    }

    /** Ouvre l'URL via l'association du système, en respectant le défaut. */
    public static boolean launchSystemDefault(String url) {
        return switch (OS) {
            case MAC     -> runAndWait(null, "/usr/bin/open", url);
            case LINUX   -> launchLinuxDefault(url);
            case WINDOWS -> launchWindowsDefault(url);
        };
    }

    private static boolean launchLinuxDefault(String url) {
        for (String[] cmd : List.of(
                new String[]{"xdg-open", url},
                new String[]{"gio", "open", url},
                new String[]{"kde-open5", url},
                new String[]{"gnome-open", url},
                new String[]{"x-www-browser", url},
                new String[]{"sensible-browser", url})) {
            String exe = findOnPath(cmd[0]);
            if (exe == null) continue;
            String[] resolved = cmd.clone();
            resolved[0] = exe;
            if (runAndWait(BrowserRegistry::sanitizeLinuxEnv, resolved)) return true;
        }
        return false;
    }

    private static boolean launchWindowsDefault(String url) {
        // Desktop.browse passe par ShellExecuteW et respecte donc l'association
        // système, mais plafonne aux alentours de 2048 caractères.
        if (url.length() <= 1800 && desktopBrowse(url)) return true;

        String exe = extractExecutable(defaultBrowserCommandWindows());
        if (exe != null && new File(exe).isFile() && runDetached(null, exe, url)) return true;

        return url.length() <= 1800
            && runAndWait(null, "rundll32.exe", "url.dll,FileProtocolHandler", url);
    }

    private static String defaultBrowserCommandWindows() {
        String progId = regQuery(
            "HKCU\\Software\\Microsoft\\Windows\\Shell\\Associations"
                + "\\UrlAssociations\\https\\UserChoice", "ProgId");
        if (progId == null || progId.isBlank()) return null;
        return regQuery("HKCR\\" + progId + "\\shell\\open\\command", null);
    }

    public static boolean desktopBrowse(String url) {
        try {
            if (!java.awt.Desktop.isDesktopSupported()) return false;
            java.awt.Desktop d = java.awt.Desktop.getDesktop();
            if (!d.isSupported(java.awt.Desktop.Action.BROWSE)) return false;
            d.browse(new URI(url));
            return true;
        } catch (Throwable t) {
            // Throwable : NoClassDefFoundError si java.desktop est absent du runtime
            return false;
        }
    }

    // ── Primitives système ───────────────────────────────────────────────────

    /**
     * Nettoie l'environnement transmis à un processus fils sous Linux :
     * jetons d'activation périmés (qui font refuser la mise au premier plan
     * par le gestionnaire de fenêtres) et variables injectées par le lanceur
     * jpackage.
     */
    public static void sanitizeLinuxEnv(Map<String, String> env) {
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

    /**
     * Lance un <i>lanceur</i> (open, xdg-open, rundll32…) et attend son code de
     * sortie. Ces commandes rendent la main quasi immédiatement, contrairement
     * au lancement direct d'un navigateur.
     */
    public static boolean runAndWait(Consumer<Map<String, String>> envCustomizer,
                                     String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (envCustomizer != null) envCustomizer.accept(pb.environment());
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();
            if (!p.waitFor(LAUNCHER_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                return true;   // ne rend pas la main : c'est le navigateur lui-même
            }
            return p.exitValue() == 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** Lance un processus sans attendre sa terminaison. */
    public static boolean runDetached(Consumer<Map<String, String>> envCustomizer,
                                      String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (envCustomizer != null) envCustomizer.accept(pb.environment());
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            pb.start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Exécute une commande et retourne sa sortie standard, ou null en cas d'échec. */
    private static String capture(String... cmd) {
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
            return (p.exitValue() == 0) ? out : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Valeur d'une clé de registre ; {@code valueName} à null pour la valeur par défaut. */
    public static String regQuery(String key, String valueName) {
        String out = (valueName == null)
            ? capture("reg.exe", "query", key, "/ve")
            : capture("reg.exe", "query", key, "/v", valueName);
        if (out == null) return null;

        for (String line : out.split("\\R")) {
            int idx = line.indexOf("REG_EXPAND_SZ");
            int len = 13;
            if (idx < 0) {
                idx = line.indexOf("REG_SZ");
                len = 6;
            }
            if (idx < 0) continue;
            return line.substring(idx + len).trim();
        }
        return null;
    }

    /** Noms des sous-clés immédiates d'une clé de registre. */
    private static List<String> regSubKeys(String key) {
        String out = capture("reg.exe", "query", key);
        if (out == null) return List.of();

        List<String> names = new ArrayList<>();
        for (String raw : out.split("\\R")) {
            String line = raw.trim();
            if (!line.startsWith("HKEY_")) continue;
            int slash = line.lastIndexOf('\\');
            if (slash < 0 || slash == line.length() - 1) continue;
            String name = line.substring(slash + 1);
            // La clé interrogée elle-même est réaffichée en tête.
            if (key.endsWith("\\" + name) || key.equals(name)) continue;
            names.add(name);
        }
        return names;
    }

    /** Extrait le chemin de l'exécutable d'une ligne de commande du registre. */
    public static String extractExecutable(String command) {
        if (command == null) return null;
        String c = command.trim();
        if (c.isEmpty()) return null;
        if (c.startsWith("\"")) {
            int end = c.indexOf('"', 1);
            return end > 1 ? c.substring(1, end) : null;
        }
        int sp = c.indexOf(' ');
        return sp > 0 ? c.substring(0, sp) : c;
    }

    /** Cherche un exécutable dans le PATH. */
    public static String findOnPath(String name) {
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
}
