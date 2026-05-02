/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.ui.controller;

import fr.lirmm.fca4j.ui.model.ImportModel;
import fr.lirmm.fca4j.ui.model.ImportModel.FormalContextDef;
import fr.lirmm.fca4j.ui.model.ImportModel.RelationalContextDef;
import fr.lirmm.fca4j.ui.service.ImportModelService;
import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.I18n;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/**
 * Éditeur de modèle JSON pour la commande FAMILY_IMPORT.
 */
public class ModelEditorController implements Initializable {

	// ── Classes internes ──────────────────────────────────────────────────────

	/** Ligne de la table des attributs d'un contexte formel. */
	private static class AttrRow {
		final int index;
		final String name;
		final SimpleBooleanProperty isKey;
		final SimpleBooleanProperty isBin;

		AttrRow(int index, String name, boolean key, boolean bin) {
			this.index = index;
			this.name = name;
			this.isKey = new SimpleBooleanProperty(key);
			this.isBin = new SimpleBooleanProperty(bin);
		}
	}

	/** Ligne d'une table de sélection de clés (mode transactionnel). */
	private static class KeyRow {
		final int index;
		final String name;
		final SimpleBooleanProperty selected;

		KeyRow(int index, String name, boolean sel) {
			this.index = index;
			this.name = name;
			this.selected = new SimpleBooleanProperty(sel);
		}
	}

	/** Paire de clés source ↔ target (mode direct). */
	private static class PairRow {
		int srcIdx, tgtIdx;
		String srcName, tgtName;

		PairRow(int srcIdx, String srcName, int tgtIdx, String tgtName) {
			this.srcIdx = srcIdx;
			this.srcName = srcName;
			this.tgtIdx = tgtIdx;
			this.tgtName = tgtName;
		}
	}

	// ── Toolbar ───────────────────────────────────────────────────────────────
	@FXML
	private Button btnNew;
	@FXML
	private Button btnOpen;
	@FXML
	private Button btnSave;
	@FXML
	private Button btnSaveAs;
	@FXML
	private Button btnAddContext;
	@FXML
	private Button btnAddRelation;

	// ── Labels toolbar ────────────────────────────────────────────────────────
	@FXML
	private Label modelNameLabel;
	@FXML
	private Label statsLabel;
	@FXML
	private Label statusLabel;

	// ── Zone contextes formels ────────────────────────────────────────────────
	@FXML
	private TableView<FormalContextDef> contextsTable;
	@FXML
	private TableColumn<FormalContextDef, String> ctxNomCol;
	@FXML
	private TableColumn<FormalContextDef, String> ctxPathCol;

	// ── Zone attributs ────────────────────────────────────────────────────────
	@FXML
	private Label attrPaneTitle;
	@FXML
	private Label csvPathLabel;
	@FXML
	private Button btnBrowseCsv;

	@FXML
	private TableView<AttrRow> attrsTable;
	@FXML
	private TableColumn<AttrRow, Number> attrIdxCol;
	@FXML
	private TableColumn<AttrRow, String> attrNameCol;
	@FXML
	private TableColumn<AttrRow, Boolean> attrKeyCol;
	@FXML
	private TableColumn<AttrRow, Boolean> attrBinCol;

	// ── Zone relations ────────────────────────────────────────────────────────
	@FXML
	private TableView<RelationalContextDef> relationsTable;
	@FXML
	private TableColumn<RelationalContextDef, String> relNomCol;
	@FXML
	private TableColumn<RelationalContextDef, String> relSourceCol;
	@FXML
	private TableColumn<RelationalContextDef, String> relTargetCol;
	@FXML
	private TableColumn<RelationalContextDef, String> relQuantCol;

	// ── Zone jointure ─────────────────────────────────────────────────────────
	@FXML
	private Label joinPaneTitle;
	@FXML
	private TextField transactionFileField;
	@FXML
	private Button btnBrowseTransaction;
	@FXML
	private Button btnClearTransaction;

	// Vue A : mode transactionnel
	@FXML
	private HBox viewTransactional;
	@FXML
	private Label sourceKeysLabel;
	@FXML
	private Label targetKeysLabel;
	@FXML
	private TableView<KeyRow> sourceKeysTable;
	@FXML
	private TableColumn<KeyRow, Number> srcKeyIdxCol;
	@FXML
	private TableColumn<KeyRow, String> srcKeyNameCol;
	@FXML
	private TableColumn<KeyRow, Boolean> srcKeySelCol;
	@FXML
	private TableView<KeyRow> targetKeysTable;
	@FXML
	private TableColumn<KeyRow, Number> tgtKeyIdxCol;
	@FXML
	private TableColumn<KeyRow, String> tgtKeyNameCol;
	@FXML
	private TableColumn<KeyRow, Boolean> tgtKeySelCol;

	// Vue B : mode direct
	@FXML
	private VBox viewDirect;
	@FXML
	private Button btnAddPair;
	@FXML
	private Button btnRemovePair;
	@FXML
	private TableView<PairRow> pairsTable;
	@FXML
	private TableColumn<PairRow, String> pairSrcCol;
	@FXML
	private TableColumn<PairRow, String> pairTgtCol;

	// ── État ──────────────────────────────────────────────────────────────────
	private ImportModel model;
	private Path currentFile;
	private boolean modified = false;
	private FormalContextDef selectedFc = null;
	private RelationalContextDef selectedRc = null;
	private final ImportModelService service = new ImportModelService();
	private Consumer<Path> onFileOpened;

	// ── Initialisation ────────────────────────────────────────────────────────

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		setupToolbar();
		setupContextsTable();
		setupAttrsTable();
		setupRelationsTable();
		setupJoinPane();
		setupContextMenus();
		loadEmpty();
	}

	// ── Toolbar ───────────────────────────────────────────────────────────────

	private void setupToolbar() {
		setIcon(btnNew, new FontIcon(Material2AL.FIBER_NEW), I18n.get("button.new"));
		setIcon(btnOpen, new FontIcon(Material2AL.FOLDER_OPEN), I18n.get("button.open"));
		setIcon(btnSave, new FontIcon(Material2MZ.SAVE), I18n.get("button.save"));
		setIcon(btnSaveAs, new FontIcon(Material2MZ.SAVE_ALT), I18n.get("button.saveas"));
		setIcon(btnAddContext, new FontIcon(Material2MZ.TABLE_ROWS), I18n.get("model.btn.add.context"));
		setIcon(btnAddRelation, new FontIcon(Material2AL.LINK), I18n.get("model.btn.add.relation"));
		setIcon(btnBrowseCsv, new FontIcon(Material2AL.FOLDER_OPEN), I18n.get("model.btn.browse.csv"));
		btnBrowseCsv.setDisable(true);
	}

	private void setIcon(Button btn, FontIcon icon, String tooltip) {
		if (btn == null)
			return;
		icon.setIconSize(20);
		icon.setIconColor(javafx.scene.paint.Color.valueOf("#333333"));
		btn.setGraphic(icon);
		btn.setText("");
		btn.setTooltip(new Tooltip(tooltip));
	}

	// ── Table contextes formels ───────────────────────────────────────────────

	private void setupContextsTable() {
		ctxNomCol.setText(I18n.get("family.col.context"));
		ctxPathCol.setText(I18n.get("model.col.path"));
		ctxNomCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().nom));
		ctxPathCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().path));

		contextsTable.getSelectionModel().selectedItemProperty().addListener((obs, old, fc) -> {
			selectedFc = fc;
			refreshAttrsTable(fc);
			btnBrowseCsv.setDisable(fc == null);
			attrPaneTitle
					.setText(fc == null ? I18n.get("model.attrs.title") : I18n.get("model.attrs.title.for", fc.nom));
		});

		contextsTable.setRowFactory(tv -> {
			TableRow<FormalContextDef> row = new TableRow<>();
			row.setOnMouseClicked(e -> {
				if (e.getClickCount() == 2 && !row.isEmpty())
					editFormalContextMeta(row.getItem());
			});
			return row;
		});
	}

	// ── Table attributs synchronisée ──────────────────────────────────────────

	private void setupAttrsTable() {
		attrIdxCol.setText(I18n.get("model.col.idx"));
		attrNameCol.setText(I18n.get("model.col.attr.name"));
		attrKeyCol.setText(I18n.get("model.col.is.key"));
		attrBinCol.setText(I18n.get("model.col.binarize"));

		attrIdxCol.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().index));
		attrNameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().name));

		attrKeyCol.setCellValueFactory(d -> d.getValue().isKey);
		attrKeyCol.setCellFactory(col -> {
			var c = new CheckBoxTableCell<AttrRow, Boolean>();
			c.setEditable(true);
			return c;
		});
		attrKeyCol.setEditable(true);

		attrBinCol.setCellValueFactory(d -> d.getValue().isBin);
		attrBinCol.setCellFactory(col -> {
			var c = new CheckBoxTableCell<AttrRow, Boolean>();
			c.setEditable(true);
			return c;
		});
		attrBinCol.setEditable(true);

		attrsTable.setEditable(true);
	}

	private void refreshAttrsTable(FormalContextDef fc) {
		attrsTable.getItems().clear();
		csvPathLabel.setText("");
		if (fc == null)
			return;

		List<String> headers = loadCsvHeaders(fc);
		List<AttrRow> rows = buildAttrRows(fc, headers);
		for (AttrRow row : rows) {
			row.isKey.addListener((obs, old, val) -> syncFcFromAttrs(fc));
			row.isBin.addListener((obs, old, val) -> syncFcFromAttrs(fc));
		}
		attrsTable.getItems().setAll(rows);

		Path resolved = resolveCsvPath(fc.path);
		if (resolved != null && resolved.toFile().exists())
			csvPathLabel.setText(resolved.toString());
		else if (!fc.path.isBlank())
			csvPathLabel.setText(fc.path + "  ⚠ " + I18n.get("model.csv.not.found"));
	}

	private List<String> loadCsvHeaders(FormalContextDef fc) {
		if (fc.path.isBlank())
			return List.of();
		Path resolved = resolveCsvPath(fc.path);
		if (resolved == null || !resolved.toFile().exists())
			return List.of();
		try {
			return service.readCsvHeaders(resolved);
		} catch (Exception e) {
			return List.of();
		}
	}

	private List<String> loadCsvHeadersFromPath(String csvPath) {
		Path resolved = resolveCsvPath(csvPath);
		if (resolved == null || !resolved.toFile().exists())
			return List.of();
		try {
			return service.readCsvHeaders(resolved);
		} catch (Exception e) {
			return List.of();
		}
	}

	private List<AttrRow> buildAttrRows(FormalContextDef fc, List<String> headers) {
		List<AttrRow> rows = new ArrayList<>();
		if (!headers.isEmpty()) {
			for (int i = 0; i < headers.size(); i++)
				rows.add(new AttrRow(i, headers.get(i), fc.attrID.contains(i), fc.attr.contains(i)));
		} else {
			Set<Integer> all = new TreeSet<>();
			all.addAll(fc.attrID);
			all.addAll(fc.attr);
			for (int i : all)
				rows.add(new AttrRow(i, "col_" + i, fc.attrID.contains(i), fc.attr.contains(i)));
		}
		return rows;
	}

	private void syncFcFromAttrs(FormalContextDef fc) {
		List<Integer> newAttrID = new ArrayList<>();
		List<Integer> newAttr = new ArrayList<>();
		for (AttrRow row : attrsTable.getItems()) {
			if (row.isKey.get())
				newAttrID.add(row.index);
			if (row.isBin.get())
				newAttr.add(row.index);
		}
		fc.attrID = newAttrID;
		fc.attr = newAttr;
		contextsTable.refresh();
		markModified();
	}

	@FXML
	private void onBrowseCsv() {
		if (selectedFc == null)
			return;
		FileChooser fc = new FileChooser();
		fc.setTitle(I18n.get("model.browse.csv.title"));
		fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
		fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("CSV", "*.csv"),
				new FileChooser.ExtensionFilter(I18n.get("filter.all"), "*.*"));
		File f = fc.showOpenDialog(attrsTable.getScene().getWindow());
		if (f == null)
			return;
		selectedFc.path = relativizeIfPossible(f.toPath());
		AppPreferences.setLastDirectory(f.getParent());
		contextsTable.refresh();
		refreshAttrsTable(selectedFc);
		markModified();
	}

	// ── Table relations ───────────────────────────────────────────────────────

	private void setupRelationsTable() {
		relNomCol.setText(I18n.get("family.col.relation"));
		relSourceCol.setText(I18n.get("family.col.source"));
		relTargetCol.setText(I18n.get("family.col.target"));
		relQuantCol.setText(I18n.get("family.col.operator"));

		relNomCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().nom));
		relSourceCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().source));
		relTargetCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().target));
		relQuantCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().quantif));

		relationsTable.setRowFactory(tv -> {
			TableRow<RelationalContextDef> row = new TableRow<>();
			row.setOnMouseClicked(e -> {
				if (e.getClickCount() == 2 && !row.isEmpty())
					editRelationalContext(row.getItem());
			});
			return row;
		});
	}

	// ── Zone jointure ─────────────────────────────────────────────────────────

	private void setupJoinPane() {
		setIcon(btnBrowseTransaction, new FontIcon(Material2AL.FOLDER_OPEN), I18n.get("model.browse.csv.title"));
		setIcon(btnClearTransaction, new FontIcon(Material2AL.CLEAR), I18n.get("model.join.clear.transaction"));
		setIcon(btnAddPair, new FontIcon(Material2AL.ADD), I18n.get("model.join.add.pair"));
		setIcon(btnRemovePair, new FontIcon(Material2MZ.REMOVE), I18n.get("model.join.remove.pair"));

		// Vue A : colonnes des tables transactionnelles
		srcKeyIdxCol.setText("#");
		srcKeyNameCol.setText(I18n.get("model.col.attr.name"));
		srcKeySelCol.setText("✓");
		srcKeyIdxCol.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().index));
		srcKeyNameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().name));
		srcKeySelCol.setCellValueFactory(d -> d.getValue().selected);
		srcKeySelCol.setCellFactory(col -> new CheckBoxTableCell<>());
		srcKeySelCol.setEditable(true);
		sourceKeysTable.setEditable(true);

		tgtKeyIdxCol.setText("#");
		tgtKeyNameCol.setText(I18n.get("model.col.attr.name"));
		tgtKeySelCol.setText("✓");
		tgtKeyIdxCol.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().index));
		tgtKeyNameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().name));
		tgtKeySelCol.setCellValueFactory(d -> d.getValue().selected);
		tgtKeySelCol.setCellFactory(col -> new CheckBoxTableCell<>());
		tgtKeySelCol.setEditable(true);
		targetKeysTable.setEditable(true);

		// Vue B : table des paires
		pairSrcCol.setText(I18n.get("family.col.source"));
		pairTgtCol.setText(I18n.get("family.col.target"));
		pairSrcCol
				.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().srcIdx + " — " + d.getValue().srcName));
		pairTgtCol
				.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().tgtIdx + " — " + d.getValue().tgtName));

		setJoinPaneDisabled(true);
		switchJoinView(true); // Vue A par défaut

		relationsTable.getSelectionModel().selectedItemProperty().addListener((obs, old, rc) -> {
			selectedRc = rc;
			refreshJoinPane(rc);
		});
	}

	private void setJoinPaneDisabled(boolean disabled) {
		transactionFileField.setDisable(disabled);
		btnBrowseTransaction.setDisable(disabled);
		btnClearTransaction.setDisable(disabled);
		sourceKeysTable.setDisable(disabled);
		targetKeysTable.setDisable(disabled);
		pairsTable.setDisable(disabled);
		btnAddPair.setDisable(disabled);
		btnRemovePair.setDisable(disabled);
	}

	/** Bascule entre Vue A (transactionnel) et Vue B (direct). */
	private void switchJoinView(boolean transactional) {
		viewTransactional.setVisible(transactional);
		viewTransactional.setManaged(transactional);
		viewDirect.setVisible(!transactional);
		viewDirect.setManaged(!transactional);
	}

	private void refreshJoinPane(RelationalContextDef rc) {
		sourceKeysTable.getItems().clear();
		targetKeysTable.getItems().clear();
		pairsTable.getItems().clear();

		if (rc == null) {
			joinPaneTitle.setText(I18n.get("model.join.title"));
			transactionFileField.setText("");
			if (sourceKeysLabel != null)
				sourceKeysLabel.setText("");
			if (targetKeysLabel != null)
				targetKeysLabel.setText("");
			setJoinPaneDisabled(true);
			switchJoinView(true);
			return;
		}

		setJoinPaneDisabled(false);
		joinPaneTitle.setText(I18n.get("model.join.title.for", rc.nom));
		transactionFileField.setText(rc.path == null ? "" : rc.path);

		boolean hasTransaction = rc.path != null && !rc.path.isBlank();
		switchJoinView(hasTransaction);

		if (hasTransaction) {
			// ── Vue A : mode transactionnel ───────────────────────────────
			List<String> transHeaders = loadCsvHeadersFromPath(rc.path);

			FormalContextDef srcFc = model.getFormalContextByName(rc.source);
			FormalContextDef tgtFc = model.getFormalContextByName(rc.target);
			List<String> srcH = srcFc != null ? loadCsvHeaders(srcFc) : List.of();
			List<String> tgtH = tgtFc != null ? loadCsvHeaders(tgtFc) : List.of();

			List<KeyRow> srcRows = new ArrayList<>();
			List<KeyRow> tgtRows = new ArrayList<>();
			for (int i = 0; i < transHeaders.size(); i++) {
				String col = transHeaders.get(i);
				boolean inSrc = srcH.contains(col);
				boolean inTgt = tgtH.contains(col);

				KeyRow sr = new KeyRow(i, inSrc ? col : col + " ⚠", rc.sourceKeys.contains(i));
				sr.selected.addListener((obs, old, val) -> syncRcTransactional(rc));
				srcRows.add(sr);

				KeyRow tr = new KeyRow(i, inTgt ? col : col + " ⚠", rc.targetKeys.contains(i));
				tr.selected.addListener((obs, old, val) -> syncRcTransactional(rc));
				tgtRows.add(tr);
			}
			sourceKeysTable.getItems().setAll(srcRows);
			targetKeysTable.getItems().setAll(tgtRows);
			sourceKeysLabel.setText(I18n.get("model.join.source.keys") + " → " + rc.source);
			targetKeysLabel.setText(I18n.get("model.join.target.keys") + " → " + rc.target);

		} else {
			// ── Vue B : mode direct — paires source ↔ target ─────────────
			FormalContextDef srcFc = model.getFormalContextByName(rc.source);
			FormalContextDef tgtFc = model.getFormalContextByName(rc.target);
			List<String> srcH = srcFc != null ? loadCsvHeaders(srcFc) : List.of();
			List<String> tgtH = tgtFc != null ? loadCsvHeaders(tgtFc) : List.of();

			int pairCount = Math.min(rc.sourceKeys.size(), rc.targetKeys.size());
			for (int i = 0; i < pairCount; i++) {
				int si = rc.sourceKeys.get(i);
				int ti = rc.targetKeys.get(i);
				pairsTable.getItems().add(new PairRow(si, si < srcH.size() ? srcH.get(si) : "col_" + si, ti,
						ti < tgtH.size() ? tgtH.get(ti) : "col_" + ti));
			}
		}
	}

	private void syncRcTransactional(RelationalContextDef rc) {
		rc.sourceKeys.clear();
		rc.targetKeys.clear();
		for (KeyRow row : sourceKeysTable.getItems())
			if (row.selected.get())
				rc.sourceKeys.add(row.index);
		for (KeyRow row : targetKeysTable.getItems())
			if (row.selected.get())
				rc.targetKeys.add(row.index);
		markModified();
	}

	private void syncRcDirect(RelationalContextDef rc) {
		rc.sourceKeys.clear();
		rc.targetKeys.clear();
		for (PairRow row : pairsTable.getItems()) {
			rc.sourceKeys.add(row.srcIdx);
			rc.targetKeys.add(row.tgtIdx);
		}
		markModified();
	}

	@FXML
	private void onBrowseTransaction() {
		if (selectedRc == null)
			return;
		FileChooser fc = new FileChooser();
		fc.setTitle(I18n.get("model.browse.csv.title"));
		fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
		fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
		File f = fc.showOpenDialog(relationsTable.getScene().getWindow());
		if (f == null)
			return;
		selectedRc.path = relativizeIfPossible(f.toPath());
		AppPreferences.setLastDirectory(f.getParent());
		relationsTable.refresh();
		refreshJoinPane(selectedRc);
		markModified();
	}

	@FXML
	private void onClearTransaction() {
		if (selectedRc == null)
			return;
		selectedRc.path = "";
		relationsTable.refresh();
		refreshJoinPane(selectedRc); // bascule automatiquement vers Vue B
		markModified();
	}

	@FXML
	private void onAddPair() {
		if (selectedRc == null)
			return;
		FormalContextDef srcFc = model.getFormalContextByName(selectedRc.source);
		FormalContextDef tgtFc = model.getFormalContextByName(selectedRc.target);
		List<String> srcH = srcFc != null ? loadCsvHeaders(srcFc) : List.of();
		List<String> tgtH = tgtFc != null ? loadCsvHeaders(tgtFc) : List.of();

		if (srcH.isEmpty() && tgtH.isEmpty()) {
			showInfo(I18n.get("model.join.no.csv.loaded"));
			return;
		}

		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.setTitle(I18n.get("model.join.add.pair"));
		dialog.setHeaderText(null);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
		grid.setHgap(12);
		grid.setVgap(8);
		grid.setPadding(new javafx.geometry.Insets(12));

		ComboBox<String> srcCombo = new ComboBox<>();
		for (int i = 0; i < srcH.size(); i++)
			srcCombo.getItems().add(i + " — " + srcH.get(i));
		if (!srcCombo.getItems().isEmpty())
			srcCombo.setValue(srcCombo.getItems().get(0));

		ComboBox<String> tgtCombo = new ComboBox<>();
		for (int i = 0; i < tgtH.size(); i++)
			tgtCombo.getItems().add(i + " — " + tgtH.get(i));
		if (!tgtCombo.getItems().isEmpty())
			tgtCombo.setValue(tgtCombo.getItems().get(0));

		grid.add(new Label(I18n.get("family.col.source")), 0, 0);
		grid.add(srcCombo, 1, 0);
		grid.add(new Label(I18n.get("family.col.target")), 0, 1);
		grid.add(tgtCombo, 1, 1);
		dialog.getDialogPane().setContent(grid);

		dialog.showAndWait().ifPresent(btn -> {
			if (btn != ButtonType.OK)
				return;
			int si = srcCombo.getSelectionModel().getSelectedIndex();
			int ti = tgtCombo.getSelectionModel().getSelectedIndex();
			if (si < 0 || ti < 0)
				return;
			pairsTable.getItems().add(new PairRow(si, srcH.get(si), ti, tgtH.get(ti)));
			syncRcDirect(selectedRc);
		});
	}

	@FXML
	private void onRemovePair() {
		int sel = pairsTable.getSelectionModel().getSelectedIndex();
		if (sel >= 0) {
			pairsTable.getItems().remove(sel);
			syncRcDirect(selectedRc);
		}
	}

	// ── Édition contexte formel ───────────────────────────────────────────────

	private void editFormalContextMeta(FormalContextDef fc) {
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.setTitle(I18n.get("model.dialog.edit.context"));
		dialog.setHeaderText(null);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.setPadding(new javafx.geometry.Insets(12));

		TextField nameField = new TextField(fc.nom);
		nameField.textProperty().addListener((obs, old, val) -> {
			if (val.contains(" "))
				nameField.setText(val.replace(" ", "_"));
		});
		TextField pathField = new TextField(fc.path);

		javafx.scene.Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);
		Runnable validate = () -> okBtn.setDisable(nameField.getText().isBlank() || pathField.getText().isBlank());
		validate.run();
		nameField.textProperty().addListener((obs, old, val) -> validate.run());
		pathField.textProperty().addListener((obs, old, val) -> validate.run());
		grid.add(new Label(I18n.get("model.col.name")), 0, 0);
		grid.add(nameField, 1, 0);
		grid.add(new Label(I18n.get("model.col.path")), 0, 1);
		grid.add(pathField, 1, 1);
		dialog.getDialogPane().setContent(grid);
		Platform.runLater(nameField::requestFocus);

		dialog.showAndWait().ifPresent(btn -> {
			if (btn != ButtonType.OK)
				return;
			String oldName = fc.nom;
			fc.nom = nameField.getText().trim();
			fc.path = pathField.getText().trim();
			if (!fc.nom.equals(oldName)) {
				for (RelationalContextDef rc : model.getRelationsUsing(oldName)) {
					if (rc.source.equals(oldName))
						rc.source = fc.nom;
					if (rc.target.equals(oldName))
						rc.target = fc.nom;
				}
				relationsTable.refresh();
			}
			contextsTable.refresh();
			refreshAttrsTable(fc);
			markModified();
		});
	}

	// ── Édition relation ──────────────────────────────────────────────────────

	private void editRelationalContext(RelationalContextDef rc) {
		showRelationalContextDialog(rc);
	}

	private void showRelationalContextDialog(RelationalContextDef existing) {
		List<String> contextNames = new ArrayList<>();
		model.getFormalContexts().forEach(fc -> contextNames.add(fc.nom));

		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.setTitle(existing == null ? I18n.get("model.dialog.add.relation") : I18n.get("model.menu.edit"));
		dialog.setHeaderText(null);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.setPadding(new javafx.geometry.Insets(12));

		TextField nameField = new TextField(existing != null ? existing.nom : "");
		nameField.textProperty().addListener((obs, old, val) -> {
			if (val.contains(" "))
				nameField.setText(val.replace(" ", "_"));
		});

		ComboBox<String> srcCombo = new ComboBox<>();
		srcCombo.getItems().addAll(contextNames);
		srcCombo.setValue(existing != null ? existing.source : contextNames.get(0));

		ComboBox<String> tgtCombo = new ComboBox<>();
		tgtCombo.getItems().addAll(contextNames);
		tgtCombo.setValue(existing != null ? existing.target
				: (contextNames.size() > 1 ? contextNames.get(1) : contextNames.get(0)));

		ComboBox<String> quantCombo = new ComboBox<>();
		quantCombo.getItems().addAll("exist", "existForall", "existContains", "equality");
		quantCombo.setValue(existing != null ? existing.quantif : "exist");

		javafx.scene.Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);
		okBtn.setDisable(nameField.getText().isBlank());
		nameField.textProperty().addListener((obs, old, val) -> okBtn.setDisable(val.isBlank()));

		int row = 0;
		grid.add(new Label(I18n.get("model.col.name")), 0, row);
		grid.add(nameField, 1, row++);
		grid.add(new Label(I18n.get("family.col.source")), 0, row);
		grid.add(srcCombo, 1, row++);
		grid.add(new Label(I18n.get("family.col.target")), 0, row);
		grid.add(tgtCombo, 1, row++);
		grid.add(new Label(I18n.get("family.col.operator")), 0, row);
		grid.add(quantCombo, 1, row);
		dialog.getDialogPane().setContent(grid);
		Platform.runLater(nameField::requestFocus);

		dialog.showAndWait().ifPresent(btn -> {
			if (btn != ButtonType.OK)
				return;
			if (existing != null) {
				existing.nom = nameField.getText().trim();
				existing.source = srcCombo.getValue();
				existing.target = tgtCombo.getValue();
				existing.quantif = quantCombo.getValue();
				relationsTable.refresh();
			} else {
				RelationalContextDef rc = new RelationalContextDef();
				rc.nom = nameField.getText().trim();
				rc.source = srcCombo.getValue();
				rc.target = tgtCombo.getValue();
				rc.quantif = quantCombo.getValue();
				model.addRelationalContext(rc);
				relationsTable.getItems().setAll(model.getRelationalContexts());
				relationsTable.getSelectionModel().select(rc);
			}
			markModified();
		});
	}

	// ── Menus contextuels ─────────────────────────────────────────────────────

	private void setupContextMenus() {
		ContextMenu ctxMenu = new ContextMenu();
		MenuItem editCtx = new MenuItem(I18n.get("model.menu.edit"));
		MenuItem addCtx = new MenuItem(I18n.get("model.btn.add.context"));
		MenuItem removeCtx = new MenuItem(I18n.get("family.menu.remove"));
		editCtx.setOnAction(e -> {
			FormalContextDef sel = contextsTable.getSelectionModel().getSelectedItem();
			if (sel != null)
				editFormalContextMeta(sel);
		});
		addCtx.setOnAction(e -> onAddContext());
		removeCtx.setOnAction(e -> removeSelectedContext());
		ctxMenu.getItems().addAll(editCtx, new SeparatorMenuItem(), addCtx, new SeparatorMenuItem(), removeCtx);
		contextsTable.setContextMenu(ctxMenu);

		ContextMenu relMenu = new ContextMenu();
		MenuItem editRel = new MenuItem(I18n.get("model.menu.edit"));
		MenuItem addRel = new MenuItem(I18n.get("model.btn.add.relation"));
		MenuItem removeRel = new MenuItem(I18n.get("family.menu.remove"));
		editRel.setOnAction(e -> {
			RelationalContextDef sel = relationsTable.getSelectionModel().getSelectedItem();
			if (sel != null)
				editRelationalContext(sel);
		});
		addRel.setOnAction(e -> onAddRelation());
		removeRel.setOnAction(e -> removeSelectedRelation());
		relMenu.getItems().addAll(editRel, new SeparatorMenuItem(), addRel, new SeparatorMenuItem(), removeRel);
		relationsTable.setContextMenu(relMenu);
	}

	// ── Chargement / Sauvegarde ───────────────────────────────────────────────

	private void loadEmpty() {
		model = service.createEmpty();
		currentFile = null;
		modified = false;
		selectedFc = null;
		selectedRc = null;
		refreshAll();
		statusLabel.setText("");
	}

	public void loadModel(Path path) {
		try {
			model = service.read(path);
			currentFile = path;
			modified = false;
			selectedFc = null;
			selectedRc = null;
			refreshAll();
			statusLabel.setText(I18n.get("model.status.loaded", path.getFileName()));
			if (onFileOpened != null)
				onFileOpened.accept(path);
		} catch (Exception e) {
			showError(I18n.get("model.error.read"), e.getMessage());
		}
	}

	public void openFile(Path path) {
		loadModel(path);
	}

	private void saveToFile(Path path) {
		try {
			currentFile = path;
			relativizeAllPaths();
			service.write(model, path);
			modified = false;
			modelNameLabel.setText(path.getFileName().toString());
			statusLabel.setText(I18n.get("model.status.saved", path.getFileName()));
			if (onFileOpened != null)
				onFileOpened.accept(path);
		} catch (Exception e) {
			showError(I18n.get("model.error.write"), e.getMessage());
		}
	}

	/** Convertit tous les chemins absolus en chemins relatifs au fichier modèle. */
	private void relativizeAllPaths() {
		if (currentFile == null)
			return;
		Path base = currentFile.getParent();
		for (FormalContextDef fc : model.getFormalContexts()) {
			fc.path = relativize(base, fc.path);
		}
		for (RelationalContextDef rc : model.getRelationalContexts()) {
			rc.path = relativize(base, rc.path);
		}
		// Rafraîchir les tables si visibles
		contextsTable.refresh();
		relationsTable.refresh();
		if (selectedFc != null)
			refreshAttrsTable(selectedFc);
		if (selectedRc != null)
			refreshJoinPane(selectedRc);
	}

	private static String relativize(Path base, String filePath) {
		if (filePath == null || filePath.isBlank())
			return filePath;
		Path p = Path.of(filePath);
		if (!p.isAbsolute())
			return filePath; // déjà relatif
		try {
			return base.relativize(p).toString().replace('\\', '/');
		} catch (Exception e) {
			return filePath;
		} // disques différents
	}

	// ── Rafraîchissement ──────────────────────────────────────────────────────
	private void refreshAll() {
		modelNameLabel.setText((currentFile == null ? I18n.get("model.new.name") : currentFile.getFileName().toString())
				+ (modified ? " *" : ""));
		statsLabel.setText(
				I18n.get("model.stats", model.getFormalContexts().size(), model.getRelationalContexts().size()));
		contextsTable.getItems().setAll(model.getFormalContexts());
		relationsTable.getItems().setAll(model.getRelationalContexts());
		btnAddRelation.setDisable(model.getFormalContexts().isEmpty());
		attrsTable.getItems().clear();
		csvPathLabel.setText("");
		attrPaneTitle.setText(I18n.get("model.attrs.title"));
		btnBrowseCsv.setDisable(true);
		joinPaneTitle.setText(I18n.get("model.join.title"));
		transactionFileField.setText("");
		sourceKeysTable.getItems().clear();
		targetKeysTable.getItems().clear();
		pairsTable.getItems().clear();
		setJoinPaneDisabled(true);
		switchJoinView(true);
		selectedFc = null;
		selectedRc = null;
	}

	private void markModified() {
	    modified = true;
	    Platform.runLater(() -> {
	        modelNameLabel.setText(
	            (currentFile == null ? I18n.get("model.new.name")
	                                 : currentFile.getFileName().toString()) + " *");
	        statsLabel.setText(I18n.get("model.stats",
	            model.getFormalContexts().size(),
	            model.getRelationalContexts().size()));
	    });
	}
	// ── Actions toolbar ───────────────────────────────────────────────────────

	@FXML
	public void onNew() {
		if (!confirmDiscard())
			return;
		loadEmpty();
	}

	@FXML
	public void onOpen() {
		if (!confirmDiscard())
			return;
		FileChooser fc = buildChooser(I18n.get("model.open.title"));
		File f = fc.showOpenDialog(contextsTable.getScene().getWindow());
		if (f != null) {
			loadModel(f.toPath());
			AppPreferences.setLastDirectory(f.getParent());
		}
	}

	@FXML
	public void onSave() {
		if (currentFile == null) {
			onSaveAs();
			return;
		}
		saveToFile(currentFile);
	}

	@FXML
	public void onSaveAs() {
		FileChooser fc = buildChooser(I18n.get("model.saveas.title"));
		File f = fc.showSaveDialog(contextsTable.getScene().getWindow());
		if (f != null) {
			currentFile = f.toPath();
			saveToFile(currentFile);
			AppPreferences.setLastDirectory(f.getParent());
		}
	}

	@FXML
	public void onAddContext() {
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.setTitle(I18n.get("model.dialog.add.context"));
		dialog.setHeaderText(null);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.setPadding(new javafx.geometry.Insets(12));

		TextField nameField = new TextField();
		nameField.setPromptText(I18n.get("model.col.name"));
		nameField.textProperty().addListener((obs, old, val) -> {
			if (val.contains(" "))
				nameField.setText(val.replace(" ", "_"));
		});
		TextField pathField = new TextField();
		pathField.setPromptText(I18n.get("model.col.path"));
		Button browseBtn = new Button(I18n.get("button.browse"));
		browseBtn.setOnAction(ev -> {
			FileChooser fc2 = new FileChooser();
			fc2.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
			fc2.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
			File f = fc2.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
			if (f != null) {
				pathField.setText(relativizeIfPossible(f.toPath()));
				if (nameField.getText().isBlank())
					nameField.setText(f.getName().replaceAll("\\.[^.]+$", ""));
			}
		});

		javafx.scene.Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);
		okBtn.setDisable(true);
		Runnable validate = () -> okBtn.setDisable(nameField.getText().isBlank() || pathField.getText().isBlank());
		nameField.textProperty().addListener((obs, old, val) -> validate.run());
		pathField.textProperty().addListener((obs, old, val) -> validate.run());
		grid.add(new Label(I18n.get("model.col.name")), 0, 0);
		grid.add(nameField, 1, 0);
		grid.add(new Label(I18n.get("model.col.path")), 0, 1);
		grid.add(pathField, 1, 1);
		grid.add(browseBtn, 2, 1);
		dialog.getDialogPane().setContent(grid);
		Platform.runLater(nameField::requestFocus);

		dialog.showAndWait().ifPresent(btn -> {
			if (btn != ButtonType.OK)
				return;
			FormalContextDef fc2 = new FormalContextDef();
			fc2.nom = nameField.getText().trim();
			fc2.path = pathField.getText().trim();
			model.addFormalContext(fc2);
			markModified();
			refreshAll();
			contextsTable.getSelectionModel().select(fc2);
		});
	}

	@FXML
	public void onAddRelation() {
		if (model.getFormalContexts().isEmpty()) {
			showInfo(I18n.get("family.need.one.context"));
			return;
		}
		showRelationalContextDialog(null);
	}

	// ── Suppression ───────────────────────────────────────────────────────────

	private void removeSelectedContext() {
		FormalContextDef sel = contextsTable.getSelectionModel().getSelectedItem();
		if (sel == null)
			return;
		List<RelationalContextDef> deps = model.getRelationsUsing(sel.nom);
		if (!deps.isEmpty()) {
			showError(I18n.get("model.error.context.used"), I18n.get("model.error.context.used.detail",
					deps.stream().map(r -> r.nom).collect(java.util.stream.Collectors.joining(", "))));
			return;
		}
		if (!confirmDelete(I18n.get("family.confirm.delete.context", sel.nom)))
			return;
		model.removeFormalContext(sel);
		markModified();
		refreshAll();
	}

	private void removeSelectedRelation() {
		RelationalContextDef sel = relationsTable.getSelectionModel().getSelectedItem();
		if (sel == null)
			return;
		if (!confirmDelete(I18n.get("family.confirm.delete.relation", sel.nom)))
			return;
		model.removeRelationalContext(sel);
		markModified();
		refreshAll();
	}

	// ── Accesseurs ────────────────────────────────────────────────────────────

	public ImportModel getModel() {
		return model;
	}

	public Path getCurrentFile() {
		return currentFile;
	}

	public void setOnFileOpened(Consumer<Path> callback) {
		this.onFileOpened = callback;
	}

	// ── Utilitaires ───────────────────────────────────────────────────────────

	private Path resolveCsvPath(String csvPath) {
		if (csvPath == null || csvPath.isBlank())
			return null;
		Path p = Path.of(csvPath);
		if (p.isAbsolute())
			return p;
		if (currentFile != null)
			return currentFile.getParent().resolve(p);
		return null;
	}

	private String relativizeIfPossible(Path absolute) {
		if (currentFile != null) {
			try {
				return currentFile.getParent().relativize(absolute).toString().replace('\\', '/');
			} catch (Exception ignored) {
			}
		}
		return absolute.toString();
	}

	private FileChooser buildChooser(String title) {
		FileChooser fc = new FileChooser();
		fc.setTitle(title);
		fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
		fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("JSON", "*.json"),
				new FileChooser.ExtensionFilter(I18n.get("filter.all"), "*.*"));
		return fc;
	}

	public boolean confirmDiscard() {
		if (!modified)
			return true;
		Alert a = new Alert(Alert.AlertType.CONFIRMATION);
		a.setTitle(I18n.get("editor.confirm.discard.title"));
		a.setHeaderText(null);
		String name = currentFile != null ? currentFile.getFileName().toString() : I18n.get("model.new.name");
		a.setContentText(I18n.get("editor.confirm.discard.model", name));
		return a.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
	}

	private boolean confirmDelete(String msg) {
		Alert a = new Alert(Alert.AlertType.CONFIRMATION);
		a.setTitle(I18n.get("editor.confirm.delete.title"));
		a.setHeaderText(null);
		a.setContentText(msg);
		return a.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
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
}
