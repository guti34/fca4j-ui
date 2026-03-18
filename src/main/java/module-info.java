module fr.lirmm.fca4j.ui {
	requires fca4j.iset;
	requires fca4j.core;
	requires fca4j.io;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires java.prefs;        // Pour les préférences (chemin du JAR)
    requires java.desktop;      // Pour Desktop.browse() si besoin
    requires jdk.jsobject;   


    opens fr.lirmm.fca4j.ui to javafx.fxml;
    opens fr.lirmm.fca4j.ui.controller to javafx.fxml;
    
    exports fr.lirmm.fca4j.ui;
}