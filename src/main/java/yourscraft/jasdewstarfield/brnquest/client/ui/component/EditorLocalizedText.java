package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import yourscraft.jasdewstarfield.brnquest.data.BookLocalization;
import java.util.*;

/** Local drafts for one semantic field. Switching languages never writes to the book or game settings. */
public final class EditorLocalizedText {
    private final BookLocalization localization;
    private final String key;
    private final String nativeText;
    private final Map<String, String> drafts = new TreeMap<>();
    private final Set<String> locales = new TreeSet<>();
    private String locale;

    public EditorLocalizedText(BookLocalization localization, String key, String nativeText, String playerLocale) {
        this.localization = localization;
        this.key = key;
        this.nativeText = nativeText;
        locale = BookLocalization.normalizeLocale(playerLocale);
        locales.addAll(localization.translations().keySet());
        locales.add(localization.fallbackLocale());
        locales.add(locale);
    }

    public String locale() { return locale; }
    public List<String> locales() { return List.copyOf(locales); }
    public String value() { return drafts.getOrDefault(locale, originalInput()); }

    /** Only text owned by this locale enters the editable buffer; inherited text is reference only. */
    private String originalInput() {
        if (locale.equals(localization.fallbackLocale())) return localization.resolve(locale, key, nativeText);
        return localization.translations().getOrDefault(locale, Map.of()).getOrDefault(key, "");
    }

    public String placeholder() {
        if (locale.equals(localization.fallbackLocale())) return "";
        String fallback = localization.fallbackLocale();
        return drafts.getOrDefault(fallback, localization.resolve(fallback, key, nativeText));
    }

    public void remember(String value) {
        if (value.equals(originalInput())) drafts.remove(locale);
        else drafts.put(locale, value);
    }
    public void select(String value) {
        if (!validLocale(value)) throw new IllegalArgumentException("Invalid locale");
        locale = BookLocalization.normalizeLocale(value.strip());
        locales.add(locale);
    }
    public static boolean validLocale(String value) {
        return value != null && value.strip().replace('-', '_').toLowerCase(Locale.ROOT).matches("[a-z0-9_]{2,16}");
    }
    public Map<String, String> changes() { return Map.copyOf(drafts); }

    /** Empty means the native default; otherwise return the locale providing the visible text. */
    public String source() {
        if (!value().isBlank()) return locale;
        String fallback = localization.fallbackLocale();
        if (drafts.containsKey(fallback)) return fallback;
        return localization.translations().getOrDefault(fallback, Map.of()).getOrDefault(key, "").isBlank() ? "" : fallback;
    }

    /** Reserve a compact code button while giving long titles the rest of the existing input row. */
    public static UiRect languageBounds(UiRect row) {
        return new UiRect(Math.max(row.left(), row.right() - 64), row.top(), row.right(), row.bottom());
    }
    public static UiRect inputBounds(UiRect row) {
        return new UiRect(row.left(), row.top(), Math.max(row.left(), row.right() - 68), row.bottom());
    }
}
