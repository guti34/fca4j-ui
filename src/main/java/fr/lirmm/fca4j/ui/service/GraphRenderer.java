package fr.lirmm.fca4j.ui.service;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import netscape.javascript.JSObject;
import fr.lirmm.fca4j.ui.util.AppPreferences;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Orchestre le rendu d'un fichier .dot en SVG interactif via GraphViz,
 * puis l'injecte dans un WebEngine JavaFX avec support des clics sur les nœuds.
 */
public class GraphRenderer {

    private final WebEngine      webEngine;
    private Consumer<String>     onNodeClick;
    private Path                 currentDotFile = null;
    private volatile Process currentDotProcess = null;
    private boolean magnifierActive = false;
    
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
    public boolean isRendering() {
        return currentDotProcess != null && currentDotProcess.isAlive();
    }
    public Path getCurrentDotFile() { return currentDotFile; }

    public void setOnNodeClick(Consumer<String> handler) {
        this.onNodeClick = handler;
    }
    public void toggleMagnifier() {
        Platform.runLater(() -> {
            try {
                // Vérifier que la fonction existe avant de l'appeler
                Object exists = webEngine.executeScript(
                    "typeof toggleMagnifier === 'function'");
                if (Boolean.TRUE.equals(exists)) {
                    magnifierActive = (boolean) webEngine.executeScript("toggleMagnifier()");
                }
            } catch (Exception e) {
                System.err.println("[GraphRenderer] toggleMagnifier: " + e.getMessage());
            }
        });
    }
    public boolean isMagnifierActive() { return magnifierActive; }
    public CompletableFuture<Void> render(Path dotFile) {
        this.currentDotFile = dotFile;
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path svgFile = Files.createTempFile("fca4j-graph-", ".svg");
                svgFile.toFile().deleteOnExit();

                String dotExecutable = AppPreferences.getDotPath();
                Process proc = new ProcessBuilder(
                    dotExecutable, "-Tsvg",
                    dotFile.toString(),
                    "-o", svgFile.toString()
                ).start();
                this.currentDotProcess = proc;
                int exit = proc.waitFor();
                this.currentDotProcess = null;
                if (exit != 0) {
                    throw new RuntimeException(
                        "GraphViz a retourné le code " + exit +
                        ". Vérifiez le chemin de `dot` dans les préférences."
                    );
                }

                return Files.readString(svgFile);

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(svgContent ->
            Platform.runLater(() -> loadSvgInWebEngine(svgContent))
        );
    }

    private void loadSvgInWebEngine(String svgContent) {
        String html = buildHtml(svgContent);

        // Listener à usage unique — retiré dès le premier SUCCEEDED
        // Évite l'accumulation de listeners et les appels parasites
        ChangeListener<Worker.State>[] listenerHolder = new ChangeListener[1];
        listenerHolder[0] = (obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                webEngine.getLoadWorker().stateProperty()
                    .removeListener(listenerHolder[0]);
                installJsBridge();
            } else if (newState == Worker.State.FAILED) {
                webEngine.getLoadWorker().stateProperty()
                    .removeListener(listenerHolder[0]);
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

    private String buildHtml(String svgContent) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="UTF-8">
            <style>
              body { margin: 0; background: #fafafa; overflow: hidden; }
              svg  { width: 100%%; height: 100vh; cursor: grab; }
              svg:active { cursor: grabbing; }
              .node polygon, .node ellipse { transition: fill 0.15s; }
              .node:hover polygon, .node:hover ellipse {
                fill: #ffe082 !important; cursor: pointer;
              }
              .node.selected polygon, .node.selected ellipse {
                fill: #ffb300 !important;
              }
              #magnifier {
                position: fixed;
                width: 220px; height: 220px;
                border-radius: 50%%;
                border: 3px solid #0047B3;
                box-shadow: 0 4px 16px #00000066;
                pointer-events: none;
                display: none;
                overflow: hidden;
                background: white;
                z-index: 9999;
              }
            </style>
            </head>
            <body>
            %s
            <div id="magnifier"></div>
            <script>
            // ── Variables zoom/pan globales (mises a jour par la IIFE) ────
            var currentScale = 1, currentTx = 0, currentTy = 0;

            // ── Loupe ─────────────────────────────────────────────────────
            var magnifierActive = false;
            var magnifier = document.getElementById('magnifier');

            function toggleMagnifier() {
              magnifierActive = !magnifierActive;
              if (magnifierActive) {
                document.addEventListener('mousemove', onMagnifierMove);
                document.body.style.cursor = 'crosshair';
              } else {
                magnifier.style.display = 'none';
                document.removeEventListener('mousemove', onMagnifierMove);
                document.body.style.cursor = '';
              }
              return magnifierActive;
            }

            function onMagnifierMove(e) {
              // Facteur adaptatif : garantit un zoom absolu minimum lisible
              // quelle que soit la valeur de zoom courante du graphe.
              // cibleAbsolue = niveau de zoom absolu minimal dans la loupe.
              var cibleAbsolue = 4;
              var zoomFactor = Math.max(2, cibleAbsolue / currentScale);
              // Plafonner pour eviter un zoom absurde si on est deja tres zoome
              zoomFactor = Math.min(zoomFactor, 10);
              var totalZoom = currentScale * zoomFactor;

              var size = 220;
              var half = size / 2;
              magnifier.style.display = 'block';
              magnifier.style.left = (e.clientX - half) + 'px';
              magnifier.style.top  = (e.clientY - half) + 'px';

              var svg = document.querySelector('svg');
              if (!svg) return;

              // transformOrigin = centre de la fenetre ('50%% 50%%')
              var centerX = window.innerWidth  / 2;
              var centerY = window.innerHeight / 2;

              // Coordonnees dans l'espace SVG original (avant zoom/pan courant)
              var rx = (e.clientX - centerX - currentTx) / currentScale + centerX;
              var ry = (e.clientY - centerY - currentTy) / currentScale + centerY;

              // Dimensions du SVG dans son espace original
              var rect  = svg.getBoundingClientRect();
              var origW = rect.width  / currentScale;
              var origH = rect.height / currentScale;

              magnifier.innerHTML = '';
              var clone = svg.cloneNode(true);
              clone.style.transform       = '';
              clone.style.position        = 'absolute';
              clone.style.width           = origW + 'px';
              clone.style.height          = origH + 'px';
              clone.style.transformOrigin = '0 0';
              clone.style.transform       = 'scale(' + totalZoom + ')';
              // Centrer le point pointe au milieu de la loupe
              clone.style.left = (half - rx * totalZoom) + 'px';
              clone.style.top  = (half - ry * totalZoom) + 'px';
              magnifier.appendChild(clone);
            }

            // ── Clics sur les noeuds ──────────────────────────────────────
            function initNodeClicks() {
              var nodes = document.querySelectorAll('.node');
              nodes.forEach(function(node) {
                node.addEventListener('click', function() {
                  document.querySelectorAll('.node.selected')
                    .forEach(function(n) { n.classList.remove('selected'); });
                  node.classList.add('selected');
                  var title = node.querySelector('title');
                  var label = title ? title.textContent.trim() : node.id;
                  if (window.javaApp) {
                    window.javaApp.onNodeClick(label);
                  }
                });
              });
            }

            // ── Zoom / Pan ────────────────────────────────────────────────
            (function() {
              var svg = document.querySelector('svg');
              if (!svg) return;
              var scale = 1, tx = 0, ty = 0;
              var dragging = false, startX, startY, startTx, startTy;

              function applyTransform() {
                svg.style.transform =
                  'translate(' + tx + 'px,' + ty + 'px) scale(' + scale + ')';
                svg.style.transformOrigin = '50%% 50%%';
                // Exposer aux variables globales pour la loupe
                currentScale = scale;
                currentTx    = tx;
                currentTy    = ty;
              }

              svg.addEventListener('wheel', function(e) {
                e.preventDefault();
                var delta = e.deltaY > 0 ? 0.9 : 1.1;
                scale = Math.min(Math.max(scale * delta, 0.1), 10);
                applyTransform();
              });

              svg.addEventListener('mousedown', function(e) {
                if (magnifierActive) return;
                dragging = true; startX = e.clientX; startY = e.clientY;
                startTx = tx; startTy = ty;
              });
              document.addEventListener('mousemove', function(e) {
                if (!dragging) return;
                tx = startTx + (e.clientX - startX);
                ty = startTy + (e.clientY - startY);
                applyTransform();
              });
              document.addEventListener('mouseup', function() { dragging = false; });
            })();
            </script>
            </body>
            </html>
            """.formatted(svgContent);
    }

    public class JsBridge {
        public void onNodeClick(String nodeLabel) {
            Platform.runLater(() -> {
                if (onNodeClick != null) onNodeClick.accept(nodeLabel);
            });
        }
    }

    public void clear() {
        currentDotFile = null;
        webEngine.loadContent(
            "<html><body style='background:#f8f9fa;'></body></html>");
    }
}
