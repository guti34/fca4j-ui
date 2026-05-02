/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.ui;

/**
 * Point d'entrée non-JavaFX pour le fat JAR.
 * Contourne la vérification du module path par le launcher JavaFX.
 */
public class Launcher {
    public static void main(String[] args) {
        MainApp.main(args);
    }
}