/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.ui.controller;

import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.I18n;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gère les menus de fichiers récents (contextes, familles, modèles).
 * Extrait de MainController pour réduire sa taille.
 */
public class RecentFilesManager {

    private final Menu recentContextMenu;
    private final Menu recentFamilyMenu;
    private final Menu recentModelMenu;

    private final Consumer<String> openContext;
    private final Consumer<String> openFamily;
    private final Consumer<String> openModel;

    /**
     * @param recentContextMenu menu FXML des contextes récents
     * @param recentFamilyMenu  menu FXML des familles récentes
     * @param recentModelMenu   menu FXML des modèles récents
     * @param openContext        callback pour ouvrir un contexte (reçoit l'entrée récente)
     * @param openFamily         callback pour ouvrir une famille (reçoit l'entrée récente)
     * @param openModel          callback pour ouvrir un modèle (reçoit l'entrée récente)
     */
    public RecentFilesManager(Menu recentContextMenu, Menu recentFamilyMenu, Menu recentModelMenu,
                              Consumer<String> openContext, Consumer<String> openFamily,
                              Consumer<String> openModel) {
        this.recentContextMenu = recentContextMenu;
        this.recentFamilyMenu = recentFamilyMenu;
        this.recentModelMenu = recentModelMenu;
        this.openContext = openContext;
        this.openFamily = openFamily;
        this.openModel = openModel;
    }

    /** Rafraîchit les trois menus de fichiers récents. */
    public void refresh() {
        buildMenu(recentContextMenu, AppPreferences.getRecentContexts(),
                openContext, AppPreferences::clearRecentContexts);
        buildMenu(recentFamilyMenu, AppPreferences.getRecentFamilies(),
                openFamily, AppPreferences::clearRecentFamilies);
        buildMenu(recentModelMenu, AppPreferences.getRecentModels(),
                openModel, AppPreferences::clearRecentModels);
    }

    private void buildMenu(Menu menu, List<String> entries,
                           Consumer<String> action, Runnable onClear) {
        menu.getItems().clear();
        if (entries.isEmpty()) {
            MenuItem empty = new MenuItem(I18n.get("menu.file.recent.empty"));
            empty.setDisable(true);
            menu.getItems().add(empty);
        } else {
            for (String entry : entries) {
                String path = AppPreferences.recentEntryPath(entry);
                File f = new File(path);
                String label = f.getName() + "   \u2014   ["
                        + shortenPath(f.getParent(), 40) + "]";
                MenuItem item = new MenuItem(label);
                item.setDisable(!f.exists());
                item.setOnAction(e -> {
                    action.accept(entry);
                    AppPreferences.setLastDirectory(f.getParent());
                });
                menu.getItems().add(item);
            }
            menu.getItems().add(new SeparatorMenuItem());
            MenuItem clearItem = new MenuItem(I18n.get("menu.file.recent.clear"));
            clearItem.setOnAction(e -> {
                onClear.run();
                refresh();
            });
            menu.getItems().add(clearItem);
        }
    }

    static String shortenPath(String path, int maxLen) {
        if (path == null) return "";
        if (path.length() <= maxLen) return path;
        return "..." + path.substring(path.length() - maxLen);
    }
}
