package fr.lirmm.fca4j.ui.control;

import fr.lirmm.fca4j.ui.util.I18n;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2MZ;

/**
 * Champ de durée type chronomètre (hh:mm:ss), saisie contrôlée, converti en
 * secondes pour l'option -timeout. Même API publique que la version valeur+unité.
 */
public class DurationField extends HBox {

    private static final int MAX_SECONDS = 99 * 3600 + 59 * 60 + 59; // 99:59:59

    private final TextField field = new TextField();
    private final Button resetButton = new Button();
    private final Label preview = new Label();

    private final IntegerProperty seconds = new SimpleIntegerProperty(this, "seconds", 0);
    private boolean updating = false;

    public DurationField() {
        super(6);
        setAlignment(Pos.CENTER_LEFT);

        // Saisie contrôlée : chiffres et ':' uniquement, max 8 caractères
        field.setTextFormatter(new TextFormatter<>(c ->
                c.getControlNewText().matches("[0-9:]{0,8}") ? c : null));
        field.setPrefWidth(90);
        field.setStyle("-fx-font-family: monospace;");
        field.setPromptText("hh:mm:ss");

        // Normalisation à la validation
        field.focusedProperty().addListener((o, was, is) -> { if (!is) commit(); });
        field.setOnAction(e -> commit());

        // Flèches : ±1 s ; Page : ±1 min
        field.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            int step = switch (e.getCode()) {
                case UP -> 1; case DOWN -> -1;
                case PAGE_UP -> 60; case PAGE_DOWN -> -60;
                default -> 0;
            };
            if (step != 0) { setSeconds(Math.max(0, parse(field.getText()) + step)); e.consume(); }
        });

        FontIcon offIcon = new FontIcon(Material2MZ.TIMER_OFF); // cf. note icône
        offIcon.setIconSize(16);
        resetButton.setGraphic(offIcon);
        resetButton.setTooltip(new Tooltip(I18n.get("duration.reset.tooltip")));
        resetButton.setOnAction(e -> setSeconds(0));

        preview.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");

        getChildren().addAll(field, resetButton, preview);
        setSeconds(0);
    }

    private void commit() { setSeconds(parse(field.getText())); }

    /** Secondes à passer à -timeout (0 = désactivé). Toujours relu depuis le champ. */
    public int getSeconds() { return parse(field.getText()); }

    /** Fixe la durée et normalise l'affichage en hh:mm:ss. */
    public void setSeconds(int total) {
        if (total < 0) total = 0;
        if (total > MAX_SECONDS) total = MAX_SECONDS;
        updating = true;
        field.setText(format(total));
        updating = false;
        seconds.set(total);
        preview.setText(total <= 0 ? I18n.get("duration.nolimit") : "\u2192 " + total + " s");
    }

    public ReadOnlyIntegerProperty secondsProperty() { return seconds; }

    // ── Analyse / formatage ──────────────────────────────────────────────────

    /** Lecture positionnelle à droite : ss | mm:ss | hh:mm:ss ; déborde proprement. */
    private static int parse(String text) {
        if (text == null || text.isBlank()) return 0;
        String[] p = text.trim().split(":", -1);
        int n = p.length;
        int s = safe(p[n - 1]);
        int m = n >= 2 ? safe(p[n - 2]) : 0;
        int h = n >= 3 ? safe(p[n - 3]) : 0;
        long total = (long) h * 3600 + (long) m * 60 + s;
        return (int) Math.max(0, Math.min(MAX_SECONDS, total));
    }

    private static int safe(String s) {
        s = s.trim();
        if (s.isEmpty()) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private static String format(int total) {
        return String.format("%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60);
    }
}