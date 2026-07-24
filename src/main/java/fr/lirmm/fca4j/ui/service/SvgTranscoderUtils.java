/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.ui.service;

import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.fop.svg.PDFTranscoder;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Conversion SVG -> PNG / PDF sans dépendre du rendu interne de "dot"
 * (-Tpng / -Tpdf).
 *
 * <p>Le SVG produit par Graphviz (dot -Tsvg) est toujours correct, y compris
 * pour les caractères Unicode utilisés par {@link DotDisplayPreprocessor}
 * pour représenter les opérateurs de fr.lirmm.fca4j.core.operator, car le
 * rendu des glyphes est différé au moment de l'affichage.</p>
 *
 * <p>En revanche, "dot -Tpng" et "dot -Tpdf" résolvent eux-mêmes les glyphes
 * au moment de la génération, via un moteur (GD / PostScript "core", ou
 * Cairo+Pango) dont le support Unicode dépend de la plateforme et de
 * l'installation locale de Graphviz.</p>
 *
 * <p>Cette classe convertit directement le SVG en PNG ou PDF en utilisant
 * Apache Batik (rendu SVG en Java2D, via le système de polices de la JVM)
 * et Apache FOP pour la sortie PDF. Le résultat est indépendant de la
 * configuration Graphviz de la machine et cohérent sur Windows, Linux et
 * macOS.</p>
 *
 * <p>Dépendances Maven à ajouter au pom.xml de fca4j-ui :</p>
 * <pre>{@code
 * <dependency>
 *     <groupId>org.apache.xmlgraphics</groupId>
 *     <artifactId>batik-transcoder</artifactId>
 *     <version>1.19</version>
 * </dependency>
 * <dependency>
 *     <groupId>org.apache.xmlgraphics</groupId>
 *     <artifactId>fop</artifactId>
 *     <version>2.11</version>
 * </dependency>
 * }</pre>
 */
public final class SvgTranscoderUtils {

    private SvgTranscoderUtils() {
    }

    /**
     * Convertit un fichier SVG en PNG.
     *
     * @param svgFile fichier SVG source (produit par dot -Tsvg)
     * @param pngFile fichier PNG à générer
     * @param widthPx largeur cible en pixels, ou {@code null} pour conserver
     *                la taille intrinsèque du SVG (hauteur déduite au même ratio)
     */
    public static void svgToPng(File svgFile, File pngFile, Float widthPx) throws IOException {
        PNGTranscoder transcoder = new PNGTranscoder();

        // Fond blanc opaque : le SVG de dot a un fond transparent par défaut
        // pour les zones non couvertes par les rectangles de nœuds/cluster.
        transcoder.addTranscodingHint(PNGTranscoder.KEY_BACKGROUND_COLOR, Color.WHITE);

        if (widthPx != null) {
            transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, widthPx);
        }

        try (OutputStream ostream = new FileOutputStream(pngFile)) {
            TranscoderInput input = new TranscoderInput(svgFile.toURI().toString());
            TranscoderOutput output = new TranscoderOutput(ostream);
            transcoder.transcode(input, output);
        } catch (TranscoderException e) {
            throw new IOException("Échec de la conversion SVG -> PNG (" + svgFile + ")", e);
        }
    }

    /**
     * Convertit un fichier SVG en PDF.
     *
     * @param svgFile fichier SVG source (produit par dot -Tsvg)
     * @param pdfFile fichier PDF à générer
     */
    public static void svgToPdf(File svgFile, File pdfFile) throws IOException {
        PDFTranscoder transcoder = new PDFTranscoder();

        try (OutputStream ostream = new FileOutputStream(pdfFile)) {
            TranscoderInput input = new TranscoderInput(svgFile.toURI().toString());
            TranscoderOutput output = new TranscoderOutput(ostream);
            transcoder.transcode(input, output);
        } catch (TranscoderException e) {
            throw new IOException("Échec de la conversion SVG -> PDF (" + svgFile + ")", e);
        }
    }
}
