package fr.lirmm.fca4j.ui.service;

import javafx.application.Platform;
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

    private final WebEngine webEngine;
    private Consumer<String> onNodeClick;

    public GraphRenderer(WebEngine webEngine) {
        this.webEngine = webEngine;
    }

    /** Callback déclenché quand l'utilisateur clique sur un nœud du treillis. */
    public void setOnNodeClick(Consumer<String> handler) {
        this.onNodeClick = handler;
    }

    /**
     * Rend un fichier .dot dans le WebEngine.
     * Exécuté hors du thread JavaFX pour ne pas bloquer l'UI.
     *
     * @param dotFile chemin vers le fichier .dot produit par FCA4J
     * @return        Future complété quand le rendu est chargé
     */
    public CompletableFuture<Void> render(Path dotFile) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Appel GraphViz : dot → SVG dans un fichier temporaire
                Path svgFile = Files.createTempFile("fca4j-graph-", ".svg");
                svgFile.toFile().deleteOnExit();

                String dotExecutable = AppPreferences.getDotPath();
                Process proc = new ProcessBuilder(
                    dotExecutable, "-Tsvg",
                    dotFile.toString(),
                    "-o", svgFile.toString()
                ).start();

                int exit = proc.waitFor();
                if (exit != 0) {
                    throw new RuntimeException(
                        "GraphViz a retourné le code " + exit +
                        ". Vérifiez le chemin de `dot` dans les préférences."
                    );
                }

                // 2. Lecture du SVG généré
                String svgContent = Files.readString(svgFile);
                return svgContent;

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(svgContent -> {
            // 3. Injection dans le WebEngine (doit se faire sur le thread JavaFX)
            Platform.runLater(() -> loadSvgInWebEngine(svgContent));
        });
    }

    private void loadSvgInWebEngine(String svgContent) {
        String html = buildHtml(svgContent);
        webEngine.loadContent(html, "text/html");

        // 4. Une fois le DOM chargé, on enregistre le bridge Java↔JS
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                installJsBridge();
            }
        });
    }

    private void installJsBridge() {
        JSObject window = (JSObject) webEngine.executeScript("window");
        window.setMember("javaApp", new JsBridge());
        // Active les gestionnaires de clics déclarés dans le HTML
        webEngine.executeScript("initNodeClicks()");
    }

    /**
     * Construit la page HTML qui enveloppe le SVG avec :
     * - zoom/pan via la molette et le glisser-déposer
     * - clic sur les nœuds remontant à Java
     */
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
              .node polygon, .node ellipse {
                transition: fill 0.15s;
              }
              .node:hover polygon, .node:hover ellipse {
                fill: #ffe082 !important;
                cursor: pointer;
              }
              .node.selected polygon, .node.selected ellipse {
                fill: #ffb300 !important;
              }
            </style>
            </head>
            <body>
            %s
            <script>
            function initNodeClicks() {
              var nodes = document.querySelectorAll('.node');
              nodes.forEach(function(node) {
                node.addEventListener('click', function() {
                  // Retrait de la sélection précédente
                  document.querySelectorAll('.node.selected')
                    .forEach(function(n) { n.classList.remove('selected'); });
                  node.classList.add('selected');

                  // Récupération du label (balise <title> générée par GraphViz)
                  var title = node.querySelector('title');
                  var label = title ? title.textContent.trim() : node.id;
                  if (window.javaApp) {
                    window.javaApp.onNodeClick(label);
                  }
                });
              });
            }

            // Zoom / Pan basique sur le SVG
            (function() {
              var svg = document.querySelector('svg');
              if (!svg) return;
              var scale = 1, tx = 0, ty = 0;
              var dragging = false, startX, startY, startTx, startTy;

              function applyTransform() {
                svg.style.transform =
                  'translate(' + tx + 'px,' + ty + 'px) scale(' + scale + ')';
                svg.style.transformOrigin = '50%% 50%%';
              }

              svg.addEventListener('wheel', function(e) {
                e.preventDefault();
                var delta = e.deltaY > 0 ? 0.9 : 1.1;
                scale = Math.min(Math.max(scale * delta, 0.1), 10);
                applyTransform();
              });

              svg.addEventListener('mousedown', function(e) {
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

    /**
     * Objet exposé au JavaScript via window.javaApp.
     * Les méthodes sont appelées depuis le thread WebKit — on repasse sur le thread JavaFX.
     */
    public class JsBridge {
        /** Appelé par le JS quand l'utilisateur clique sur un nœud. */
        public void onNodeClick(String nodeLabel) {
            Platform.runLater(() -> {
                if (onNodeClick != null) {
                    onNodeClick.accept(nodeLabel);
                }
            });
        }
    }
}