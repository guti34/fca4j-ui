package fr.lirmm.fca4j.ui.controller;

import fr.lirmm.fca4j.ui.model.CommandBuilder;
import fr.lirmm.fca4j.ui.util.I18n;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class ImportCommandController implements Initializable {

    @FXML private ToggleButton btnBinarize;
    @FXML private ToggleButton btnFamilyImport;
    @FXML private StackPane    binarizeContainer;
    @FXML private StackPane    familyImportContainer;

    private BinarizeController      binarizeController;
    private FamilyImportController  familyImportController;
    private Consumer<CommandBuilder> onRun;

    @Override
    public void initialize(URL location, ResourceBundle resources) {}

    public void configure(Consumer<CommandBuilder> onRun) {
        this.onRun = onRun;

        // Charger les deux sous-panneaux
        loadBinarize();
        loadFamilyImport();

        // Basculer l'affichage selon le bouton actif
        btnBinarize.selectedProperty().addListener((obs, old, val) -> {
            binarizeContainer.setVisible(val);
            binarizeContainer.setManaged(val);
            familyImportContainer.setVisible(!val);
            familyImportContainer.setManaged(!val);
        });
        // État initial
        binarizeContainer.setVisible(true);
        binarizeContainer.setManaged(true);
        familyImportContainer.setVisible(false);
        familyImportContainer.setManaged(false);
    }

    private void loadBinarize() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fr/lirmm/fca4j/ui/fxml/binarize.fxml"),
                I18n.getBundle());
            Node panel = loader.load();
            binarizeController = loader.getController();
            binarizeController.configure(null, onRun, null);
            binarizeContainer.getChildren().setAll(panel);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadFamilyImport() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fr/lirmm/fca4j/ui/fxml/family_import.fxml"),
                I18n.getBundle());
            Node panel = loader.load();
            familyImportController = loader.getController();
            familyImportController.configure(onRun);
            familyImportContainer.getChildren().setAll(panel);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void onRun() {
        if (btnBinarize.isSelected() && binarizeController != null)
            binarizeController.onRun();
        else if (familyImportController != null)
            familyImportController.onRun();
    }
}