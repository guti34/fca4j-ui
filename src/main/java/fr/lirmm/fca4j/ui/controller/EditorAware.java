package fr.lirmm.fca4j.ui.controller;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Interface implémentée par tous les panneaux de commande
 * qui disposent d'un bouton "Ouvrir dans l'éditeur".
 */
public interface EditorAware {
    void setOpenInEditor(Consumer<Path> openInEditor);
}
