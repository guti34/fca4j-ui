/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.ui.util;

import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * Icône "badge" : rectangle noir arrondi avec le nom du format (SVG/PNG/PDF)
 * en gras, blanc, centré. Version simplifiée sans papier corné — à la
 * taille des autres icônes de la toolbar (~20px), le détail du coin corné
 * n'apporte rien et prend de la place utile au texte.
 *
 * <p>Hérite de {@link Region} avec une taille préférée/min/max fixée
 * explicitement (voir la version précédente pour le piège JavaFX évité :
 * un Group transformé ne doit jamais être utilisé directement comme
 * graphic d'un Button sans encapsulation de ce type).</p>
 */
public class FileTypeIcon extends Region {

    public FileTypeIcon(String label, double height, Color color) {
        double h = height;
        double w = h * 1.9;

        Rectangle bg = new Rectangle(w, h);
        bg.setArcWidth(h * 0.4);
        bg.setArcHeight(h * 0.4);
        bg.setFill(color);

        Text text = new Text(label);
        text.setFont(Font.font("Arial", FontWeight.BLACK, h * 0.6));
        text.setFill(Color.WHITE);
        text.setTextOrigin(VPos.CENTER);
        text.setX((w - text.getLayoutBounds().getWidth()) / 2);
        text.setY(h / 2.0 + 1);

        Group art = new Group(bg, text);
        art.setManaged(false);

        setPrefSize(w, h);
        setMinSize(w, h);
        setMaxSize(w, h);

        getChildren().add(art);
    }

    /** Couleur par défaut alignée sur le gris de la toolbar (#444444). */
    public FileTypeIcon(String label, double height) {
        this(label, height, Color.web("#444444"));
    }
}
