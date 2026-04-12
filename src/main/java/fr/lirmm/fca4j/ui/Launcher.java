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