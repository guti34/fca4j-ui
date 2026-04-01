package fr.lirmm.fca4j.ui.controller;

import fr.lirmm.fca4j.core.BinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.std.BitSetFactory;
import fr.lirmm.fca4j.ui.service.ContextIOService;
import fr.lirmm.fca4j.ui.service.ContextIOService.ContextFormat;
import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.I18n;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Contrôleur de l'éditeur de contexte formel binaire. La colonne "Objets" est
 * figée dans une TableView séparée à gauche. La TableView droite contient les
 * colonnes attributs et scroll horizontalement.
 */
public class ContextEditorController implements Initializable {

	/**
	 * Représente une ligne de la TableView.
	 */
	private static class ContextRow {
		private final SimpleStringProperty objectName;
		private final ObservableList<SimpleBooleanProperty> cells;

		ContextRow(String name, ObservableList<SimpleBooleanProperty> cells) {
			this.objectName = new SimpleStringProperty(name);
			this.cells = cells;
		}

		String getName() {
			return objectName.get();
		}

		void setName(String n) {
			objectName.set(n);
		}

		SimpleStringProperty nameProperty() {
			return objectName;
		}

		ObservableList<SimpleBooleanProperty> getCells() {
			return cells;
		}
	}

	// ── Boutons toolbar ───────────────────────────────────────────────────────
	@FXML
	private Button btnNew;
	@FXML
	private Button btnOpen;
	@FXML
	private Button btnSave;
	@FXML
	private Button btnSaveAs;
	@FXML
	private Button btnAddObject;
	@FXML
	private Button btnAddAttr;
	@FXML
	private Button btnDelObject;
	@FXML
	private Button btnDelAttr;
	@FXML
	private Button btnRename;

	// ── Widgets FXML ──────────────────────────────────────────────────────────
	@FXML
	private Label contextNameLabel;
	@FXML
	private Label statsLabel;
	@FXML
	private TableView<ContextRow> objectsTable; // colonne figée
	@FXML
	private TableColumn<ContextRow, String> frozenObjCol;
	@FXML
	private TableView<ContextRow> tableView; // colonnes attributs
	@FXML
	private Label statusLabel;
	@FXML private Label fileNameLabel;
	@FXML private Label separatorLabel; 
	// ── Stockage des lignes ───────────────────────────────────────────────────
	private final List<ContextRow> rows = new ArrayList<>();

	// ── Services ──────────────────────────────────────────────────────────────
	private final ContextIOService ioService = new ContextIOService();

	private Runnable onLoadStart;
	private Runnable onLoadEnd;

	// ── État ──────────────────────────────────────────────────────────────────
	private IBinaryContext context;
	private Path currentFile;
	private boolean modified = false;
	private boolean fromFamily = false;
	private Consumer<IBinaryContext> onSaveCallback;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		tableView.setEditable(true);
		tableView.getSelectionModel().setCellSelectionEnabled(true);
		tableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		tableView.setPlaceholder(new Label(I18n.get("editor.empty")));

		// Table objets figée — non éditable, même sélection que tableView
		objectsTable.setEditable(false);
		objectsTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		objectsTable.setPlaceholder(new Label(""));
		// Masquer l'en-tête horizontal de objectsTable
		objectsTable.widthProperty().addListener((obs, o, n) -> hideHorizontalScrollBar(objectsTable));

		setupToolbar();
		loadContext(ioService.createEmpty(I18n.get("editor.new.context.name")));
	}

	// ── Toolbar ───────────────────────────────────────────────────────────────

	private void setupToolbar() {
		setIconAndTooltip(btnNew, new FontIcon(Material2AL.FIBER_NEW), I18n.get("editor.tooltip.new"));
		setIconAndTooltip(btnOpen, new FontIcon(Material2AL.FOLDER_OPEN), I18n.get("editor.tooltip.open"));
		setIconAndTooltip(btnSave, new FontIcon(Material2MZ.SAVE), I18n.get("editor.tooltip.save"));
		setIconAndTooltip(btnSaveAs, new FontIcon(Material2MZ.SAVE_ALT), I18n.get("editor.tooltip.saveas"));
		setIconAndTooltip(btnAddObject, new FontIcon(Material2AL.ADD_BOX), I18n.get("editor.tooltip.add.object"));
		setIconAndTooltip(btnDelObject, new FontIcon(Material2AL.CHECK_BOX_OUTLINE_BLANK),
				I18n.get("editor.tooltip.del.object"));
		setIconAndTooltip(btnAddAttr, new FontIcon(Material2AL.ADD_CIRCLE), I18n.get("editor.tooltip.add.attr"));
		setIconAndTooltip(btnDelAttr, new FontIcon(Material2MZ.REMOVE_CIRCLE_OUTLINE),
				I18n.get("editor.tooltip.del.attr"));
		setIconAndTooltip(btnRename, new FontIcon(Material2AL.EDIT), I18n.get("editor.tooltip.rename"));
		// Icône plus petite que les autres boutons toolbar
//        	btnRename.getGraphic().setStyle("-fx-icon-size: 14px;");    
	}

	private void setIconAndTooltip(Button btn, FontIcon icon, String tooltipText) {
		icon.setIconSize(20);
		icon.setIconColor(javafx.scene.paint.Color.valueOf("#333333"));
		btn.setGraphic(icon);
		btn.setTooltip(new Tooltip(tooltipText));
	}

	// ── Chargement ────────────────────────────────────────────────────────────

	public void setOnLoadCallbacks(Runnable onStart, Runnable onEnd) {
		this.onLoadStart = onStart;
		this.onLoadEnd = onEnd;
	}

	public void loadContext(IBinaryContext ctx) {
		this.context = ctx;
		this.modified = false;
		if (onLoadStart != null)
			onLoadStart.run();
		java.util.concurrent.CompletableFuture.runAsync(() -> {
			// rebuildTable doit se faire sur le thread JavaFX
			Platform.runLater(() -> {
				rebuildTable();
				updateLabels();
				updateSaveButton();
				if (onLoadEnd != null)
					onLoadEnd.run();
			});
		});
	}

	public void loadContextFromFamily(IBinaryContext ctx, Consumer<IBinaryContext> onSaveCallback) {
		this.fromFamily = true;
		this.currentFile = null;
		this.onSaveCallback = onSaveCallback;
		loadContext(ctx);
		updateSaveButton();
	}

	// ── Construction de la TableView ──────────────────────────────────────────

	private void rebuildTable() {
		// Vider les deux tables
		tableView.getColumns().clear();
		tableView.getItems().clear();
		objectsTable.getItems().clear();
		rows.clear();

		int nbObj = context.getObjectCount();
		int nbAttr = context.getAttributeCount();

		// ── Colonne figée "Objet" dans objectsTable ───────────────────────────
		frozenObjCol.setText(I18n.get("editor.col.objects"));
		frozenObjCol.setCellValueFactory(data -> data.getValue().nameProperty());
		frozenObjCol.setCellFactory(col -> new TableCell<>() {
			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setText(null);
					setGraphic(null);
					return;
				}
				Label lbl = new Label(item);
				lbl.setMaxWidth(Double.MAX_VALUE);
				lbl.setOnMouseClicked(e -> {
					if (e.getClickCount() == 2) {
						ContextRow row = objectsTable.getItems().get(getIndex());
						promptRenameObjectRow(row);
					}
				});
				setGraphic(lbl);
				setText(null);
			}
		});

		// ── Colonnes attributs dans tableView ─────────────────────────────────
		for (int a = 0; a < nbAttr; a++) {
			final int attrIdx = a;
			TableColumn<ContextRow, Boolean> col = new TableColumn<>();

			double prefWidth = Math.max(50.0, Math.min(160.0, context.getAttributeName(a).length() * 8.0 + 16));
			col.setPrefWidth(prefWidth);
			col.setMinWidth(50.0);
			col.setMaxWidth(160.0);
			col.setEditable(true);
			col.setResizable(true);

			String attrName = context.getAttributeName(a);
			Label header = new Label(attrName);
			header.setMaxWidth(Double.MAX_VALUE);
			if (attrName.length() > 15)
				header.setTooltip(new Tooltip(attrName));

			MenuItem renameItem = new MenuItem(I18n.get("editor.menu.rename.attr"));
			MenuItem deleteItem = new MenuItem(I18n.get("editor.menu.delete.attr"));
			renameItem.setOnAction(e -> promptRenameAttribute(attrIdx));
			deleteItem.setOnAction(e -> deleteAttribute(attrIdx));
			ContextMenu ctxMenu = new ContextMenu(renameItem, deleteItem);

			header.setOnMouseClicked(e -> {
				if (e.getClickCount() == 2) {
					promptRenameAttribute(attrIdx);
				} else if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
					ctxMenu.show(header, e.getScreenX(), e.getScreenY());
				}
			});
			col.setGraphic(header);

			col.setCellValueFactory(data -> data.getValue().getCells().get(attrIdx));
			col.setCellFactory(CheckBoxTableCell.forTableColumn(col));

			tableView.getColumns().add(col);
		}

		// ── Construction des lignes ───────────────────────────────────────────
		ObservableList<ContextRow> items = FXCollections.observableArrayList();
		for (int o = 0; o < nbObj; o++) {
			ObservableList<SimpleBooleanProperty> cells = FXCollections.observableArrayList();
			for (int a = 0; a < nbAttr; a++) {
				final int ai = a;
				SimpleBooleanProperty prop = new SimpleBooleanProperty(context.get(o, a));
				prop.addListener((obs, oldVal, newVal) -> {
					int realObj = context.getObjectIndex(rows.stream().filter(r -> r.getCells().get(ai) == prop)
							.findFirst().map(ContextRow::getName).orElse(""));
					if (realObj >= 0)
						context.set(realObj, ai, newVal);
					markModified();
				});
				cells.add(prop);
			}
			ContextRow cr = new ContextRow(context.getObjectName(o), cells);
			rows.add(cr);
			items.add(cr);
		}

		// Les deux tables partagent la même liste d'items
		tableView.setItems(items);
		objectsTable.setItems(items);

		// Synchroniser les scrollbars verticaux après rendu
		Platform.runLater(this::syncScrollBars);
	}

	// ── Synchronisation du scroll vertical entre les deux tables ─────────────

	private void syncScrollBars() {
		ScrollBar leftScroll = getScrollBar(objectsTable, Orientation.VERTICAL);
		ScrollBar rightScroll = getScrollBar(tableView, Orientation.VERTICAL);
		if (leftScroll == null || rightScroll == null)
			return;

		// Liaison bidirectionnelle
		leftScroll.valueProperty().bindBidirectional(rightScroll.valueProperty());

		// Masquer la scrollbar de la table figée (inutile visuellement)
		leftScroll.setVisible(false);
		leftScroll.setPrefWidth(0);
		leftScroll.setMaxWidth(0);

		// Masquer aussi la scrollbar horizontale de la table figée
		hideHorizontalScrollBar(objectsTable);
	}

	private void hideHorizontalScrollBar(TableView<?> table) {
		ScrollBar hBar = getScrollBar(table, Orientation.HORIZONTAL);
		if (hBar != null) {
			hBar.setVisible(false);
			hBar.setPrefHeight(0);
			hBar.setMaxHeight(0);
		}
	}

	private ScrollBar getScrollBar(TableView<?> table, Orientation orientation) {
		for (Node node : table.lookupAll(".scroll-bar")) {
			if (node instanceof ScrollBar sb && sb.getOrientation() == orientation)
				return sb;
		}
		return null;
	}

	// ── Labels ────────────────────────────────────────────────────────────────

	private void updateLabels() {
	    // Nom du fichier
	    if (currentFile != null) {
	        fileNameLabel.setText(currentFile.getFileName().toString());
	        separatorLabel.setVisible(true);
	        separatorLabel.setManaged(true);
	    } else {
	        fileNameLabel.setText("");
	        separatorLabel.setVisible(false);
	        separatorLabel.setManaged(false);
	    }

	    // Nom du contexte
	    String name = (context.getName() != null && !context.getName().isBlank())
	        ? context.getName() : I18n.get("editor.new.context.name");
	    contextNameLabel.setText(name + (modified ? " *" : ""));
	    contextNameLabel.setStyle(
	        "-fx-font-weight: bold; -fx-text-fill: #0047B3;");

	    statsLabel.setText(I18n.get("editor.stats",
	        context.getObjectCount(), context.getAttributeCount()));
	    contextNameLabel.setOnMouseClicked(e -> {
	        if (e.getClickCount() == 2) onRenameContext();
	    });
	    contextNameLabel.setStyle(
	        "-fx-font-weight: bold; -fx-text-fill: #0047B3; -fx-cursor: hand;");
	}
	
	private void markModified() {
		if (!modified) {
			modified = true;
			Platform.runLater(this::updateLabels);
		}
	}

	// ── Actions toolbar ───────────────────────────────────────────────────────

	@FXML
	public void onNewContext() {
		if (!confirmDiscardChanges())
			return;
		fromFamily = false;
		onSaveCallback = null;
		String name = promptText(I18n.get("editor.dialog.new.title"), I18n.get("editor.dialog.new.prompt"),
				I18n.get("editor.new.context.name"), true);
		if (name == null)
			return;
		loadContext(ioService.createEmpty(name));
		currentFile = null;
		fileNameLabel.setText("");
		separatorLabel.setVisible(false);
		separatorLabel.setManaged(false);		
		statusLabel.setText("");
	}

	@FXML
	public void onOpen() {
		if (!confirmDiscardChanges())
			return;
		fromFamily = false;
		onSaveCallback = null;
		FileChooser fc = buildFileChooser(I18n.get("editor.open.title"), false);
		File f = fc.showOpenDialog(tableView.getScene().getWindow());
		if (f == null)
			return;
		Path path = f.toPath();
		AppPreferences.setLastDirectory(f.getParent());

		if (onLoadStart != null)
			onLoadStart.run();
		java.util.concurrent.CompletableFuture.supplyAsync(() -> {
			try {
				return ioService.read(path);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}).thenAccept(ctx -> Platform.runLater(() -> {
			if (onLoadEnd != null)
				onLoadEnd.run();
			loadContext(ctx);
			currentFile = path;
			statusLabel.setText(I18n.get("editor.status.loaded", f.getName()));
			if (onFileLoadedCallback != null)
				onFileLoadedCallback.accept(path.toString());

		})).exceptionally(ex -> {
			Platform.runLater(() -> {
				if (onLoadEnd != null)
					onLoadEnd.run();
				showError(I18n.get("editor.error.read.title"), ex.getCause().getMessage());
			});
			return null;
		});
	}

	@FXML
	private void onSave() {
		if (fromFamily) {
			modified = false;
			updateLabels();
			if (onSaveCallback != null) {
				onSaveCallback.accept(context);
				onSaveCallback = null;
				fromFamily = false;
				updateSaveButton();
			}
			loadContext(ioService.createEmpty(I18n.get("editor.new.context.name")));
			currentFile = null;
			statusLabel.setText("");
			return;
		}
		if (currentFile == null) {
			onSaveAs();
			return;
		}
		ContextFormat format = ContextFormat.fromFile(currentFile.toFile());
		if (format == ContextFormat.CSV) {
			String sep = askCsvSeparator();
			if (sep == null)
				return;
			ioService.setSeparator(sep.charAt(0));
		}
		saveToFile(currentFile, format);
	}

	@FXML
	private void onSaveAs() {
		Consumer<IBinaryContext> savedCallback = onSaveCallback;
		onSaveCallback = null;
		FileChooser fc = buildFileChooser(I18n.get("editor.saveas.title"), true);
		File f = fc.showSaveDialog(tableView.getScene().getWindow());
		if (f == null) {
			onSaveCallback = savedCallback;
			return;
		}

		currentFile = f.toPath();
		ContextFormat format = ContextFormat.fromFile(f);

		// Si CSV, proposer le séparateur
		if (format == ContextFormat.CSV) {
			String sep = askCsvSeparator();
			if (sep == null) {
				onSaveCallback = savedCallback;
				return;
			} // annulé
			ioService.setSeparator(sep.charAt(0));
		}

		saveToFile(currentFile, format);
		onSaveCallback = savedCallback;
		AppPreferences.setLastDirectory(f.getParent());
	}

	private String askCsvSeparator() {
		ChoiceDialog<String> dialog = new ChoiceDialog<>("COMMA", "COMMA", "SEMICOLON", "TAB");
		dialog.setTitle(I18n.get("editor.csv.separator.title"));
		dialog.setHeaderText(null);
		dialog.setContentText(I18n.get("editor.csv.separator.prompt"));

		// Libellés plus lisibles
		dialog.getItems().clear();
		dialog.getItems().addAll(I18n.get("separator.comma"), I18n.get("separator.semicolon"),
				I18n.get("separator.tab"));
		dialog.setSelectedItem(I18n.get("separator.comma"));

		return dialog.showAndWait().map(choice -> {
			if (choice.equals(I18n.get("separator.comma")))
				return ",";
			if (choice.equals(I18n.get("separator.semicolon")))
				return ";";
			if (choice.equals(I18n.get("separator.tab")))
				return "\t";
			return ",";
		}).orElse(null); // null = annulé
	}

	private void saveToFile(Path path, ContextFormat format) {
		try {
			ioService.write(context, path, format);
			modified = false;
			currentFile = path;
			updateLabels();
			statusLabel.setText(I18n.get("editor.status.saved",
				    path.getFileName().toString()));
			AppPreferences.setLastDirectory(path.getParent().toString());
			if (onFileLoadedCallback != null)
				onFileLoadedCallback.accept(path.toString());
		} catch (Exception e) {
			showError(I18n.get("editor.error.write.title"), e.getMessage());
		}
	}

	// ── Actions structure ─────────────────────────────────────────────────────

	@FXML
	private void onAddObject() {
		String name = promptText(I18n.get("editor.dialog.add.object.title"),
				I18n.get("editor.dialog.add.object.prompt"),
				I18n.get("editor.default.object.name") + (context.getObjectCount() + 1), true);
		if (name == null)
			return;
		ISet emptyIntent = ioService.getFactory().createSet();
		context.addObject(name, emptyIntent);
		rebuildTable();
		markModified();
		tableView.scrollTo(tableView.getItems().size() - 1);
	}

	@FXML
	private void onAddAttribute() {
		String name = promptText(I18n.get("editor.dialog.add.attr.title"),
				I18n.get("editor.dialog.add.attr.prompt", true),
				I18n.get("editor.default.attr.name") + (context.getAttributeCount() + 1), true);
		if (name == null)
			return;
		ISet emptyExtent = ioService.getFactory().createSet();
		context.addAttribute(name, emptyExtent);
		rebuildTable();
		markModified();
		tableView.scrollToColumn(tableView.getColumns().get(tableView.getColumns().size() - 1));
	}

	@FXML
	private void onRemoveObject() {
		int sel = objectsTable.getSelectionModel().getSelectedIndex();
		if (sel < 0)
			sel = tableView.getSelectionModel().getSelectedIndex();
		if (sel < 0) {
			showInfo(I18n.get("editor.select.object.first"));
			return;
		}
		ContextRow row = tableView.getItems().get(sel);
		String objName = row.getName();
		if (!confirmDelete(I18n.get("editor.confirm.delete.object", objName)))
			return;
		int realIdx = context.getObjectIndex(objName);
		if (realIdx >= 0)
			context.removeObject(realIdx);
		rebuildTable();
		markModified();
	}

	@FXML
	private void onRemoveAttribute() {
		if (context.getAttributeCount() == 0) {
			showInfo(I18n.get("editor.select.attr.first"));
			return;
		}
		javafx.scene.control.ChoiceDialog<String> dialog = new javafx.scene.control.ChoiceDialog<>(
				context.getAttributeName(0), java.util.stream.IntStream.range(0, context.getAttributeCount())
						.mapToObj(context::getAttributeName).collect(java.util.stream.Collectors.toList()));
		dialog.setTitle(I18n.get("editor.dialog.del.attr.title"));
		dialog.setHeaderText(null);
		dialog.setContentText(I18n.get("editor.dialog.del.attr.prompt"));
		dialog.showAndWait().ifPresent(name -> {
			int idx = context.getAttributeIndex(name);
			if (idx >= 0)
				deleteAttribute(idx);
		});
	}

	private void deleteAttribute(int attrIdx) {
		String attrName = context.getAttributeName(attrIdx);
		if (!confirmDelete(I18n.get("editor.confirm.delete.attr", attrName)))
			return;
		context.removeAttribute(attrIdx);
		rebuildTable();
		markModified();
	}

	@FXML
	private void onRenameContext() {
		String current = context.getName() != null ? context.getName() : "";
		String newName = promptText(I18n.get("editor.dialog.rename.context"), I18n.get("editor.dialog.name.prompt"),
				current, true);
		if (newName == null)
			return;
		context.setName(newName);
		updateLabels();
		markModified();
	}

	// ── Renommage ─────────────────────────────────────────────────────────────

	private void promptRenameObjectRow(ContextRow row) {
		String oldName = row.getName();
		String newName = promptText(I18n.get("editor.dialog.rename.object"), I18n.get("editor.dialog.name.prompt"),
				oldName, true);
		if (newName == null)
			return;
		int realIdx = context.getObjectIndex(oldName);
		if (realIdx >= 0)
			context.setObjectName(realIdx, newName);
		row.setName(newName);
		markModified();
	}

	private void promptRenameAttribute(int idx) {
		String newName = promptText(I18n.get("editor.dialog.rename.attr"), I18n.get("editor.dialog.name.prompt"),
				context.getAttributeName(idx), true);
		if (newName == null)
			return;
		context.setAttributeName(idx, newName);
		Label header = (Label) tableView.getColumns().get(idx).getGraphic();
		if (header != null) {
			header.setText(newName);
			header.setTooltip(newName.length() > 15 ? new Tooltip(newName) : null);
		}
		markModified();
	}

	// ── Callback onFileLoaded ─────────────────────────────────────────────────

	private Consumer<String> onFileLoadedCallback;

	public void setOnFileLoaded(Consumer<String> callback) {
		this.onFileLoadedCallback = callback;
	}

	// ── Utilitaires UI ────────────────────────────────────────────────────────

	private FileChooser buildFileChooser(String title, boolean forSave) {
		FileChooser fc = new FileChooser();
		fc.setTitle(title);
		fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
		fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("SLF (HTK)", "*.slf"),
				new FileChooser.ExtensionFilter("CEX (ConExp)", "*.cex"),
				new FileChooser.ExtensionFilter("CXT (Burmeister)", "*.cxt"),
				new FileChooser.ExtensionFilter("XML (Galicia)", "*.xml"),
				new FileChooser.ExtensionFilter("CSV", "*.csv"),
				new FileChooser.ExtensionFilter(I18n.get("filter.all"), "*.*"));
		return fc;
	}

	private String promptText(String title, String prompt, String defaultValue, boolean sanitize) {
		TextInputDialog dialog = new TextInputDialog(defaultValue);
		dialog.setTitle(title);
		dialog.setHeaderText(null);
		dialog.setContentText(prompt);

		if (sanitize) {
// Transformer les espaces en temps réel
			dialog.getEditor().textProperty().addListener((obs, old, val) -> {
				if (val.contains(" "))
					dialog.getEditor().setText(val.replace(" ", "_"));
			});
		}
		return dialog.showAndWait().filter(s -> !s.isBlank()).orElse(null);
	}

//Surcharge existante inchangée pour compatibilité
	private String promptText(String title, String prompt, String defaultValue) {
		return promptText(title, prompt, defaultValue, false);
	}

	public boolean confirmDiscardChanges() {
		if (!modified)
			return true;
		Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.setTitle(I18n.get("editor.confirm.discard.title"));
		alert.setHeaderText(null);
		alert.setContentText(I18n.get("editor.confirm.discard.detail"));
		return alert.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
	}

	private boolean confirmDelete(String message) {
		Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.setTitle(I18n.get("editor.confirm.delete.title"));
		alert.setHeaderText(null);
		alert.setContentText(message);
		return alert.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
	}

	private void showError(String title, String msg) {
		Alert a = new Alert(Alert.AlertType.ERROR);
		a.setTitle(title);
		a.setHeaderText(null);
		a.setContentText(msg);
		a.showAndWait();
	}

	private void showInfo(String msg) {
		Alert a = new Alert(Alert.AlertType.INFORMATION);
		a.setTitle(I18n.get("app.title"));
		a.setHeaderText(null);
		a.setContentText(msg);
		a.showAndWait();
	}

	private void updateSaveButton() {
		if (fromFamily) {
			// Mode famille : Save = "Retour à la famille"
			FontIcon icon = new FontIcon(Material2AL.ARROW_BACK);
			icon.setIconSize(20);
			icon.setIconColor(javafx.scene.paint.Color.valueOf("#0047B3"));
			btnSave.setGraphic(icon);
			btnSave.setTooltip(new Tooltip(I18n.get("editor.tooltip.save.to.family")));
			btnSave.setStyle("-fx-background-color: #e8f0fe; -fx-border-color: #0047B3; "
					+ "-fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;");
		} else {
			// Mode normal : Save standard
			FontIcon icon = new FontIcon(Material2MZ.SAVE);
			icon.setIconSize(20);
			icon.setIconColor(javafx.scene.paint.Color.valueOf("#333333"));
			btnSave.setGraphic(icon);
			btnSave.setTooltip(new Tooltip(I18n.get("editor.tooltip.save")));
			btnSave.setStyle("");
		}
	}
	// ── Accesseurs ────────────────────────────────────────────────────────────

	public IBinaryContext getContext() {
		return context;
	}

	public void openFile(Path path) {
		openFile(path, "COMMA");
	}

	public void openFile(Path path, String separator) {
		if (!"COMMA".equals(separator)) {
			char sep = switch (separator) {
			case "SEMICOLON" -> ';';
			case "TAB" -> '\t';
			default -> ',';
			};
			ioService.setSeparator(sep);
		}
		if (onLoadStart != null)
			onLoadStart.run();
		java.util.concurrent.CompletableFuture.supplyAsync(() -> {
			try {
				return ioService.read(path);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}).thenAccept(ctx -> Platform.runLater(() -> {
			if (onLoadEnd != null)
				onLoadEnd.run();
			loadContext(ctx);
			currentFile = path;
		    statusLabel.setText(I18n.get("editor.status.loaded", 
		            path.getFileName().toString()));        
			if (onFileLoadedCallback != null)
				onFileLoadedCallback.accept(path.toString());
		})).exceptionally(ex -> {
			Platform.runLater(() -> {
				if (onLoadEnd != null)
					onLoadEnd.run();
				showError(I18n.get("editor.error.read.title"), ex.getCause().getMessage());
			});
			return null;
		});
	}
}
