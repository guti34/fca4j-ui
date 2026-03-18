package fr.lirmm.fca4j.ui.util;

import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Classe utilitaire centrale pour l'internationalisation.
 *
 * Usage :
 *   I18n.get("label.input.file")               → "Input file"
 *   I18n.get("status.running", "LATTICE")       → "Running LATTICE…"
 *   I18n.bindingFor("section.input")            → StringBinding (réactif)
 *
 * La langue est persistée dans AppPreferences et peut être changée
 * à chaud — les bindings se mettent à jour automatiquement.
 */
public class I18n {

    private static final String BUNDLE_BASE =
        "fr/lirmm/fca4j/ui/i18n/messages";

    public static final List<Locale> SUPPORTED_LOCALES = List.of(
        Locale.ENGLISH,
        Locale.FRENCH
    );

    /** Locale active — observable pour les bindings. */
    private static final ObjectProperty<Locale> locale =
        new SimpleObjectProperty<>(resolveInitialLocale());

    /** Bundle actif. */
    private static ResourceBundle bundle = loadBundle(locale.get());

    // ── Initialisation ────────────────────────────────────────────────────────

    private static Locale resolveInitialLocale() {
        String saved = AppPreferences.getLanguage();
        if (saved != null && !saved.isBlank()) {
            return Locale.forLanguageTag(saved);
        }
        // Détection automatique : français si disponible, anglais sinon
        Locale sys = Locale.getDefault();
        return sys.getLanguage().equals("fr") ? Locale.FRENCH : Locale.ENGLISH;
    }

    private static ResourceBundle loadBundle(Locale loc) {
        return ResourceBundle.getBundle(BUNDLE_BASE, loc);
    }

    // ── API publique ──────────────────────────────────────────────────────────

    /** Retourne la traduction d'une clé. */
    public static String get(String key) {
        try {
            return bundle.getString(key);
        } catch (Exception e) {
            return "!" + key + "!";
        }
    }

    /** Retourne la traduction avec substitution de paramètres ({0}, {1}…). */
    public static String get(String key, Object... args) {
        return MessageFormat.format(get(key), args);
    }

    /**
     * Retourne un StringBinding réactif.
     * À utiliser dans les contrôleurs pour les labels mis à jour dynamiquement.
     */
    public static StringBinding bindingFor(String key) {
        return new StringBinding() {
            { bind(locale); }
            @Override
            protected String computeValue() { return I18n.get(key); }
        };
    }

    /** Retourne la locale active. */
    public static Locale getLocale() { return locale.get(); }

    /** Retourne le ResourceBundle actif (pour FXMLLoader). */
    public static ResourceBundle getBundle() { return bundle; }

    /**
     * Change la langue à chaud et persiste le choix.
     * Les FXML déjà chargés ne se mettront pas à jour —
     * il faut recharger les vues (voir MainController#reloadUI).
     */
    public static void setLocale(Locale newLocale) {
        AppPreferences.setLanguage(newLocale.toLanguageTag());
        bundle = loadBundle(newLocale);
        locale.set(newLocale);
    }

    /** Nom d'affichage d'une locale pour le ComboBox des préférences. */
    public static String displayName(Locale loc) {
        return loc.getDisplayLanguage(loc).substring(0, 1).toUpperCase()
             + loc.getDisplayLanguage(loc).substring(1);
    }
}
