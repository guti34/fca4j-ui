/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.ui.controller;

import fr.lirmm.fca4j.ui.util.AppPreferences;
import fr.lirmm.fca4j.ui.util.I18n;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Viewer de règles d'implication (RULEBASIS / DBASIS). Supporte les formats
 * TXT, JSON, XML et Datalog. Affiche les règles avec mise en valeur visuelle et
 * propose filtre, tri par support et copie presse-papier.
 */
public class RulesViewerController implements Initializable {

	// ── FXML ──────────────────────────────────────────────────────────────────
	@FXML
	private Button btnOpen;
	@FXML
	private Button btnCopy;
	@FXML
	private Label fileNameLabel;
	@FXML
	private TextField filterField;
	@FXML
	private ComboBox<String> sortCombo;
	@FXML
	private ListView<Rule> rulesList;
	@FXML
	private Label statusLabel;
	@FXML
	private Button btnFcavizir;
	// ── État ──────────────────────────────────────────────────────────────────
	private final List<Rule> allRules = new ArrayList<>();
	private final List<Rule> filteredRules = new ArrayList<>();
	private Path currentFile;

	private Consumer<Path> openInFcavizir;

	// ── Modèle ────────────────────────────────────────────────────────────────

	private record Rule(int support, List<String> premises, List<String> conclusions) {
		String toTxt() {
			return "<" + support + "> " + String.join(", ", premises) + " => " + String.join(", ", conclusions);
		}
	}

	// ── Couleurs ──────────────────────────────────────────────────────────────

	private static final String COLOR_PREMISE = "#185FA5"; // bleu
	private static final String COLOR_CONCLUSION = "#B45309"; // orange
	private static final String COLOR_ARROW = "#888780"; // gris
	private static final String ROW_ODD = "#FFFFFF";
	private static final String ROW_EVEN = "#F5F4F0";

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		// Toolbar
		setIcon(btnOpen, new FontIcon(Material2AL.FOLDER_OPEN), I18n.get("editor.tooltip.open"));
		setIcon(btnCopy, new FontIcon(Material2AL.CONTENT_COPY), I18n.get("rules.tooltip.copy"));
		FontIcon iconFcavizir = new FontIcon(Material2MZ.OPEN_IN_BROWSER);
		iconFcavizir.setIconSize(16);
		iconFcavizir.setIconColor(Color.WHITE);
		btnFcavizir.setGraphic(iconFcavizir);
		btnFcavizir.setText(I18n.get("rules.btn.fcavizir"));
		btnFcavizir.setStyle("-fx-background-color: #0047B3; -fx-text-fill: white;"
				+ "-fx-font-weight: bold; -fx-cursor: hand;" + "-fx-padding: 6 16; -fx-background-radius: 4;");
		// Tri
		sortCombo.getItems().addAll(I18n.get("rules.sort.original"), I18n.get("rules.sort.support.desc"),
				I18n.get("rules.sort.support.asc"), I18n.get("rules.sort.premise.size"),
				I18n.get("rules.sort.conclusion.size"));
		sortCombo.setValue(I18n.get("rules.sort.original"));
		sortCombo.valueProperty().addListener((obs, old, val) -> applyFilterAndSort());

		// Filtre
		filterField.textProperty().addListener((obs, old, val) -> applyFilterAndSort());

		// ListView virtualisée
		rulesList.setCellFactory(lv -> new RuleCell());
		rulesList.setFixedCellSize(36);
		rulesList.setFocusTraversable(false);

		updateStatus();
	}

	private void setIcon(Button btn, FontIcon icon, String tooltip) {
		icon.setIconSize(20);
		icon.setIconColor(Color.valueOf("#333333"));
		btn.setGraphic(icon);
		btn.setText("");
		btn.setTooltip(new Tooltip(tooltip));
	}

	// ── Chargement ────────────────────────────────────────────────────────────

	@FXML
	private void onOpen() {
		FileChooser fc = new FileChooser();
		fc.setTitle(I18n.get("rules.open.title"));
		fc.setInitialDirectory(new File(AppPreferences.getLastDirectory()));
		fc.getExtensionFilters().addAll(
				new FileChooser.ExtensionFilter("Rules files", "*.txt", "*.json", "*.xml", "*.dlgp"),
				new FileChooser.ExtensionFilter(I18n.get("filter.all"), "*.*"));
		File f = fc.showOpenDialog(btnOpen.getScene().getWindow());
		if (f != null) {
			AppPreferences.setLastDirectory(f.getParent());
			loadFile(f.toPath());
		}
	}

	@FXML
	private void onOpenInFcavizir() {
		if (allRules.isEmpty())
			return;
		try {
			// Écrire le fichier converti dans un temp
			String content = convertToFcavizirFormat();
			Path tmp = Files.createTempFile("fcavizir-", ".txt");
			tmp.toFile().deleteOnExit();
			byte[] bytes = convertToFcavizirFormat().getBytes(StandardCharsets.UTF_8);
			Files.write(tmp, bytes); // Ouvrir via le même mécanisme que RCAViz

			if (openInFcavizir != null)
				openInFcavizir.accept(tmp);
		} catch (Exception e) {
			statusLabel.setText("Erreur export FCAvizIR : " + e.getMessage());
		}
	}

	public void setOpenInFcavizir(Consumer<Path> callback) {
		this.openInFcavizir = callback;
	}

	public void loadFile(Path path) {
		currentFile = path;
		fileNameLabel.setText(path.getFileName().toString());
		CompletableFuture.supplyAsync(() -> {
			try {
				String content = Files.readString(path, java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n")
						.replace("\r", "\n");
				String name = path.getFileName().toString().toLowerCase();
				if (name.endsWith(".json"))
					return parseJson(content);
				if (name.endsWith(".xml"))
					return parseXml(content);
				if (name.endsWith(".dlgp"))
					return parseDlgp(content);
				return parseTxt(content);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}).thenAccept(rules -> Platform.runLater(() -> {
			allRules.clear();
			allRules.addAll(rules);
			applyFilterAndSort();
		})).exceptionally(ex -> {
			Platform.runLater(() -> statusLabel.setText("Erreur : " + ex.getCause().getMessage()));
			return null;
		});
	}

	// ── Parseurs ──────────────────────────────────────────────────────────────

	/** Format TXT : <support> premise1, premise2 => conclusion1, conclusion2 */
	private List<Rule> parseTxt(String content) {
		List<Rule> rules = new ArrayList<>();
		for (String line : content.split("\n")) {
			line = line.trim();
			if (line.isEmpty())
				continue;
			// <support> ...
			int end = line.indexOf('>');
			if (end < 0)
				continue;
			int support = Integer.parseInt(line.substring(1, end).trim());
			String rest = line.substring(end + 1).trim();
			String[] parts = rest.split("=>");
			if (parts.length != 2)
				continue;
			List<String> premises = splitTerms(parts[0]);
			List<String> conclusions = splitTerms(parts[1]);
			rules.add(new Rule(support, premises, conclusions));
		}
		return rules;
	}

	/** Format JSON : [{"support":N,"premise":[...],"conclusion":[...]},...] */
	private List<Rule> parseJson(String content) {
		List<Rule> rules = new ArrayList<>();
		// Parser JSON minimal sans dépendance externe
		content = content.trim();
		if (content.startsWith("["))
			content = content.substring(1);
		if (content.endsWith("]"))
			content = content.substring(0, content.length() - 1);
		// Découper par objet
		int depth = 0;
		int start = 0;
		for (int i = 0; i < content.length(); i++) {
			char c = content.charAt(i);
			if (c == '{') {
				if (depth++ == 0)
					start = i;
			} else if (c == '}') {
				if (--depth == 0) {
					String obj = content.substring(start, i + 1);
					Rule r = parseJsonObject(obj);
					if (r != null)
						rules.add(r);
				}
			}
		}
		return rules;
	}

	private Rule parseJsonObject(String obj) {
		try {
			int support = Integer.parseInt(extractJsonValue(obj, "support"));
			List<String> premises = extractJsonArray(obj, "premise");
			List<String> conclusions = extractJsonArray(obj, "conclusion");
			return new Rule(support, premises, conclusions);
		} catch (Exception e) {
			return null;
		}
	}

	private String extractJsonValue(String obj, String key) {
		String search = "\"" + key + "\"";
		int idx = obj.indexOf(search);
		if (idx < 0)
			return "0";
		idx = obj.indexOf(':', idx) + 1;
		int end = obj.indexOf(',', idx);
		if (end < 0)
			end = obj.indexOf('}', idx);
		return obj.substring(idx, end).trim().replace("\"", "");
	}

	private List<String> extractJsonArray(String obj, String key) {
		String search = "\"" + key + "\"";
		int idx = obj.indexOf(search);
		if (idx < 0)
			return List.of();
		int start = obj.indexOf('[', idx);
		int end = obj.indexOf(']', start);
		if (start < 0 || end < 0)
			return List.of();
		String arr = obj.substring(start + 1, end);
		return Arrays.stream(arr.split(",")).map(s -> s.trim().replace("\"", "")).filter(s -> !s.isEmpty())
				.collect(Collectors.toList());
	}

	/**
	 * Format XML :
	 * <rule support="N"><premise>...</premise><conclusion>...</conclusion></rule>
	 */
	private List<Rule> parseXml(String content) {
		List<Rule> rules = new ArrayList<>();
		String[] ruleBlocks = content.split("<rule ");
		for (int i = 1; i < ruleBlocks.length; i++) {
			String block = ruleBlocks[i];
			try {
				int support = Integer.parseInt(block.substring(block.indexOf("support=\"") + 9,
						block.indexOf("\"", block.indexOf("support=\"") + 9)));
				String prem = extractXmlTag(block, "premise");
				String conc = extractXmlTag(block, "conclusion");
				rules.add(new Rule(support, splitTerms(prem), splitTerms(conc)));
			} catch (Exception ignored) {
			}
		}
		return rules;
	}

	private String extractXmlTag(String block, String tag) {
		int start = block.indexOf("<" + tag + ">") + tag.length() + 2;
		int end = block.indexOf("</" + tag + ">");
		if (start < 0 || end < 0 || start > end)
			return "";
		return block.substring(start, end).trim();
	}

	/**
	 * Format Datalog : "% support=N\n conclusions(X) :- premises(X)." Nettoie les
	 * URIs <http://...> et la variable (X).
	 */
	private List<Rule> parseDlgp(String content) {
		List<Rule> rules = new ArrayList<>();
		String[] lines = content.split("\n");
		for (int i = 0; i < lines.length - 1; i++) {
			String line = lines[i].trim();
			if (!line.startsWith("% support="))
				continue;
			int support = Integer.parseInt(line.substring(10).trim());
			String rule = lines[i + 1].trim();
			// Enlever le point final
			if (rule.endsWith("."))
				rule = rule.substring(0, rule.length() - 1);
			String[] parts = rule.split(":-");
			if (parts.length != 2)
				continue;
			List<String> conclusions = parseDlgpAtoms(parts[0]);
			List<String> premises = parseDlgpAtoms(parts[1]);
			rules.add(new Rule(support, premises, conclusions));
			i++; // sauter la ligne de règle
		}
		return rules;
	}

	private List<String> parseDlgpAtoms(String part) {
		// Nettoyer les URIs <http://...> → garder seulement le nom local
		part = part.replaceAll("<[^>]*/([^/>]+)>", "$1");
		// Supprimer (X) et les espaces
		part = part.replaceAll("\\(X\\)", "").trim();
		return Arrays.stream(part.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
	}

	private List<String> splitTerms(String s) {
		return Arrays.stream(s.split(",")).map(String::trim).filter(t -> !t.isEmpty()).collect(Collectors.toList());
	}

	// ── Filtre et tri ─────────────────────────────────────────────────────────

	private void applyFilterAndSort() {
		String filter = filterField.getText().trim().toLowerCase();
		filteredRules.clear();
		filteredRules.addAll(allRules.stream()
				.filter(r -> filter.isEmpty() || r.premises().stream().anyMatch(p -> p.toLowerCase().contains(filter))
						|| r.conclusions().stream().anyMatch(c -> c.toLowerCase().contains(filter)))
				.collect(Collectors.toList()));

		String sort = sortCombo.getValue();
		if (sort == null)
			sort = "";
		if (sort.equals(I18n.get("rules.sort.support.desc")))
			filteredRules.sort((a, b) -> Integer.compare(b.support(), a.support()));
		else if (sort.equals(I18n.get("rules.sort.support.asc")))
			filteredRules.sort(Comparator.comparingInt(Rule::support));
		else if (sort.equals(I18n.get("rules.sort.premise.size")))
			filteredRules.sort(Comparator.comparingInt(r -> r.premises().size()));
		else if (sort.equals(I18n.get("rules.sort.conclusion.size")))
			filteredRules.sort(Comparator.comparingInt(r -> r.conclusions().size()));

		renderRules();
		updateStatus();
	}

	// ── Rendu visuel ──────────────────────────────────────────────────────────

	private void renderRules() {
		// ListView virtualisée : O(1) quel que soit le nombre de règles
		rulesList.setItems(javafx.collections.FXCollections.observableList(filteredRules));
		btnFcavizir.setDisable(false);
	}

	public void clearRules() {
		allRules.clear();
		filteredRules.clear();
		rulesList.setItems(javafx.collections.FXCollections.emptyObservableList());
		fileNameLabel.setText("");
		btnFcavizir.setDisable(true);
	}

	private HBox buildRuleRow(Rule rule, int index) {
		HBox row = new HBox(10);
		row.setAlignment(Pos.CENTER_LEFT);
		row.setPadding(new Insets(6, 12, 6, 12));
		row.setStyle("-fx-background-color: " + (index % 2 == 0 ? ROW_ODD : ROW_EVEN) + ";");
		row.setMinHeight(36);

		// Badge support
		Label badge = new Label(String.valueOf(rule.support()));
		badge.setFont(Font.font(11));
		badge.setPadding(new Insets(2, 7, 2, 7));
		badge.setStyle("-fx-background-radius: 999; -fx-font-weight: bold;" + " -fx-background-color: "
				+ supportColor(rule.support()) + ";" + " -fx-text-fill: " + supportTextColor(rule.support()) + ";");
		badge.setMinWidth(34);
		badge.setAlignment(Pos.CENTER);

		// Prémisses
		Label premises = new Label(String.join(", ", rule.premises()));
		premises.setStyle("-fx-text-fill: " + COLOR_PREMISE + "; -fx-font-size: 12px;");
		premises.setWrapText(true);

		// Flèche
		Label arrow = new Label("  ⟹  ");
		arrow.setStyle("-fx-text-fill: " + COLOR_ARROW + "; -fx-font-size: 14px;");

		// Conclusions
		Label conclusions = new Label(String.join(", ", rule.conclusions()));
		conclusions.setStyle("-fx-text-fill: " + COLOR_CONCLUSION + "; -fx-font-size: 12px;");
		conclusions.setWrapText(true);
		HBox.setHgrow(conclusions, Priority.ALWAYS);

		row.getChildren().addAll(badge, premises, arrow, conclusions);
		return row;
	}

	/** Couleur de fond du badge selon le support (vert→orange→rouge). */
	private String supportColor(int support) {
		if (support == 0)
			return "#FECACA"; // rouge pâle
		if (support <= 2)
			return "#FED7AA"; // orange pâle
		if (support <= 4)
			return "#FEF08A"; // jaune pâle
		return "#BBF7D0"; // vert pâle
	}

	private String supportTextColor(int support) {
		if (support == 0)
			return "#991B1B";
		if (support <= 2)
			return "#9A3412";
		if (support <= 4)
			return "#854D0E";
		return "#166534";
	}

	// ── Copie presse-papier ───────────────────────────────────────────────────

	@FXML
	private void onCopy() {
		if (filteredRules.isEmpty())
			return;
		String text = filteredRules.stream().map(Rule::toTxt).collect(Collectors.joining("\n"));
		ClipboardContent content = new ClipboardContent();
		content.putString(text);
		Clipboard.getSystemClipboard().setContent(content);
		statusLabel.setText(I18n.get("rules.status.copied", filteredRules.size()));
	}

	// ── Statut ────────────────────────────────────────────────────────────────

	private void updateStatus() {
		if (allRules.isEmpty()) {
			statusLabel.setText(I18n.get("rules.status.empty"));
		} else if (filteredRules.size() == allRules.size()) {
			statusLabel.setText(I18n.get("rules.status.total", allRules.size()));
		} else {
			statusLabel.setText(I18n.get("rules.status.filtered", filteredRules.size(), allRules.size()));
		}
	}

	public String convertToFcavizirFormat() {
		if (allRules.isEmpty())
			return "";

		// Extraire les noms de relations uniques (partie avant '(')
		Set<String> relations = new LinkedHashSet<>();
		for (Rule rule : allRules) {
			for (String attr : rule.premises())
				if (attr.contains("("))
					relations.add(attr.substring(0, attr.indexOf('(')));
			for (String attr : rule.conclusions())
				if (attr.contains("("))
					relations.add(attr.substring(0, attr.indexOf('(')));
		}

		StringBuilder sb = new StringBuilder();

		// Ligne source
		if (currentFile != null)
			sb.append("Rules calculated from file: ").append(currentFile.toAbsolutePath()).append("\n");
		sb.append("\n");

		// Section relation_description
		sb.append("#relation_description#\n");
		for (String rel : relations)
			sb.append("  ").append(rel).append(":\n");
		sb.append("\n");

		// Section métriques
		sb.append("#metrics_ordered#\n");
		sb.append("  support\n");
		// sb.append(" confidence\n"); // pour le futur
		sb.append("\n");

		sb.append("#rules#\n");

		for (Rule rule : allRules) {
			String support = String.format("%03d", rule.support());
			sb.append("<").append(support).append("> ").append(String.join(",", rule.premises())).append(" => ")
					.append(String.join(",", rule.conclusions())).append("\n");
		}
		// Normaliser les fins de ligne en LF (Unix) — requis par FCAvizIR
		return sb.toString().replace("\r\n", "\n").replace("\r", "\n");
	}
	// ── Cellule virtuelle ListView ────────────────────────────────────────────
	// Reproduit buildRuleRow() mais recyclée : ~25 instances pour N règles.

	private class RuleCell extends javafx.scene.control.ListCell<Rule> {

		private final HBox row;
		private final Label badge;
		private final Label premises;
		private final Label arrow;
		private final Label conclusions;

		RuleCell() {
			row = new HBox(10);
			row.setAlignment(Pos.CENTER_LEFT);
			row.setPadding(new Insets(6, 12, 6, 12));
			row.setMinHeight(36);

			badge = new Label();
			badge.setFont(Font.font(11));
			badge.setPadding(new Insets(2, 7, 2, 7));
			badge.setMinWidth(34);
			badge.setAlignment(Pos.CENTER);

			premises = new Label();
			premises.setStyle("-fx-text-fill: " + COLOR_PREMISE + "; -fx-font-size: 12px;");

			arrow = new Label("  \u27F9  ");
			arrow.setStyle("-fx-text-fill: " + COLOR_ARROW + "; -fx-font-size: 14px;");

			conclusions = new Label();
			conclusions.setStyle("-fx-text-fill: " + COLOR_CONCLUSION + "; -fx-font-size: 12px;");
			HBox.setHgrow(conclusions, Priority.ALWAYS);

			row.getChildren().addAll(badge, premises, arrow, conclusions);
			setGraphic(row);
			setText(null);
			setPadding(Insets.EMPTY);
		}

		@Override
		protected void updateItem(Rule rule, boolean empty) {
			super.updateItem(rule, empty);
			if (empty || rule == null) {
				setGraphic(null);
				return;
			}
			badge.setText(String.valueOf(rule.support()));
			badge.setStyle("-fx-background-radius: 999; -fx-font-weight: bold;" + " -fx-background-color: "
					+ supportColor(rule.support()) + ";" + " -fx-text-fill: " + supportTextColor(rule.support()) + ";");

			premises.setText(String.join(", ", rule.premises()));
			conclusions.setText(String.join(", ", rule.conclusions()));

			int idx = getIndex();
			row.setStyle("-fx-background-color: " + (idx % 2 == 0 ? ROW_ODD : ROW_EVEN) + ";");

			setGraphic(row);
		}
	}

}
