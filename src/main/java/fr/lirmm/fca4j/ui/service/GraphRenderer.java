/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.ui.service;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import netscape.javascript.JSObject;
import fr.lirmm.fca4j.ui.util.AppPreferences;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Orchestre le rendu d'un fichier .dot en SVG interactif via GraphViz, puis
 * l'injecte dans un WebEngine JavaFX avec support des clics sur les nœuds.
 */
public class GraphRenderer {

	private final WebEngine webEngine;
	private Consumer<String> onNodeClick;
	private Path currentDotFile = null;
	private volatile Process currentDotProcess = null;
	private boolean magnifierActive = false;
	private Consumer<String> onConsoleMessage;
	
	public GraphRenderer(WebEngine webEngine) {
		this.webEngine = webEngine;
	}

	public void cancel() {
		Process p = currentDotProcess;
		if (p != null && p.isAlive()) {
			p.descendants().forEach(ProcessHandle::destroyForcibly);
			p.destroyForcibly();
			currentDotProcess = null;
		}
	}
	public void setOnConsoleMessage(Consumer<String> handler) {
	    this.onConsoleMessage = handler;
	}

	private void log(String msg) {
	    if (onConsoleMessage != null)
	        Platform.runLater(() -> onConsoleMessage.accept(msg));
	}
	public boolean isRendering() {
		return currentDotProcess != null && currentDotProcess.isAlive();
	}

	public Path getCurrentDotFile() {
		return currentDotFile;
	}

	public void setOnNodeClick(Consumer<String> handler) {
		this.onNodeClick = handler;
	}

	public void toggleMagnifier() {
		Platform.runLater(() -> {
			try {
				// Vérifier que la fonction existe avant de l'appeler
				Object exists = webEngine.executeScript("typeof toggleMagnifier === 'function'");
				if (Boolean.TRUE.equals(exists)) {
					magnifierActive = (boolean) webEngine.executeScript("toggleMagnifier()");
				}
			} catch (Exception e) {
				System.err.println("[GraphRenderer] toggleMagnifier: " + e.getMessage());
			}
		});
	}

	public boolean isMagnifierActive() {
		return magnifierActive;
	}

	public CompletableFuture<Void> render(Path dotFile) {
	    this.currentDotFile = dotFile;
	    return renderWithOptions(dotFile, false)
	        .exceptionally(ex -> {
	            String msg = ex.getCause() != null
	                ? ex.getCause().getMessage() : ex.getMessage();
	            if (msg != null && msg.contains("code 3")) {
	                log("[GraphViz] Graphe complexe — nouvel essai en mode mémoire réduite...");
	                renderWithOptions(dotFile, true)
	                    .exceptionally(ex2 -> {
	                        log("[GraphViz] " + ex2.getCause().getMessage());
	                        return null;
	                    });
	            } else {
	                log("[GraphViz] " + msg);
	            }
	            return null;
	        });
	}
	private CompletableFuture<Void> renderWithOptions(Path dotFile, boolean largeMode) {
	    return CompletableFuture.supplyAsync(() -> {
	        try {
	            Path svgFile = Files.createTempFile("fca4j-graph-", ".svg");
	            svgFile.toFile().deleteOnExit();

	            List<String> cmd = new ArrayList<>();
	            cmd.add(AppPreferences.getDotPath());
	            cmd.add("-Tsvg");
	            if (largeMode) {
	                cmd.add("-Gnslimit=2");
	                cmd.add("-Gnslimit1=2");
	                cmd.add("-Gmaxiter=500");
	            }
	            cmd.add(dotFile.toString());
	            cmd.add("-o");
	            cmd.add(svgFile.toString());

	            Process proc = new ProcessBuilder(cmd).start();
	            this.currentDotProcess = proc;
	            int exit = proc.waitFor();
	            this.currentDotProcess = null;
	            if (exit != 0) throw new RuntimeException(
	                "GraphViz a retourné le code " + exit
	                + (exit == 3 ? " — mémoire insuffisante." 
	                             : ". Vérifiez le chemin de `dot` dans les préférences."));
	            return Files.readString(svgFile);
	        } catch (Exception e) {
	            throw new RuntimeException(e);
	        }
	    }).thenAccept(svg -> Platform.runLater(() -> loadSvgInWebEngine(svg)));
	}
	private void loadSvgInWebEngine(String svgContent) {
		String html = buildHtml(svgContent);

		// Listener à usage unique — retiré dès le premier SUCCEEDED
		// Évite l'accumulation de listeners et les appels parasites
		ChangeListener<Worker.State>[] listenerHolder = new ChangeListener[1];
		listenerHolder[0] = (obs, oldState, newState) -> {
			if (newState == Worker.State.SUCCEEDED) {
				webEngine.getLoadWorker().stateProperty().removeListener(listenerHolder[0]);
				installJsBridge();
			} else if (newState == Worker.State.FAILED) {
				webEngine.getLoadWorker().stateProperty().removeListener(listenerHolder[0]);
			}
		};
		webEngine.getLoadWorker().stateProperty().addListener(listenerHolder[0]);

		webEngine.loadContent(html, "text/html");
	}

	private void installJsBridge() {
		try {
			JSObject window = (JSObject) webEngine.executeScript("window");
			window.setMember("javaApp", new JsBridge());
			webEngine.executeScript("initNodeClicks()");
		} catch (Exception e) {
			System.err.println("[GraphRenderer] installJsBridge error: " + e.getMessage());
		}
	}

	private static final String HTML_TEMPLATE;

	static {
	    try (var is = GraphRenderer.class.getResourceAsStream(
	            "/fr/lirmm/fca4j/ui/html/graph-viewer.html")) {
	        HTML_TEMPLATE = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
	    } catch (Exception e) {
	        throw new ExceptionInInitializerError(
	            "Cannot load graph-viewer.html: " + e.getMessage());
	    }
	}

	private String buildHtml(String svgContent) {
	    return HTML_TEMPLATE.replace("{{SVG_CONTENT}}", svgContent);
	}
	
	public class JsBridge {
		public void onNodeClick(String nodeLabel) {
			Platform.runLater(() -> {
				if (onNodeClick != null)
					onNodeClick.accept(nodeLabel);
			});
		}
	}

	public void clear() {
		currentDotFile = null;
		webEngine.loadContent("<html><body style='background:#f8f9fa;'></body></html>");
	}
}
