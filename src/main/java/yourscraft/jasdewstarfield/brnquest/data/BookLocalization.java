package yourscraft.jasdewstarfield.brnquest.data;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Immutable task-book translations keyed by locale and stable semantic text key. */
public record BookLocalization(String fallbackLocale, Map<String, Map<String, String>> translations) {
    public static final BookLocalization EMPTY = new BookLocalization("en_us", Map.of());

    public BookLocalization {
        fallbackLocale = normalizeLocale(fallbackLocale);
        Map<String, Map<String, String>> copied = new LinkedHashMap<>();
        // Equivalent locale spellings may contribute different keys, but must never silently lose text.
        translations.forEach((locale, values) -> {
            Map<String, String> merged = copied.computeIfAbsent(normalizeLocale(locale), ignored -> new LinkedHashMap<>());
            values.forEach((key, value) -> {
                String previous = merged.putIfAbsent(key, value);
                if (previous != null && !previous.equals(value)) {
                    throw new IllegalArgumentException("Conflicting translation for " + normalizeLocale(locale) + ": " + key);
                }
            });
        });
        copied.replaceAll((locale, values) -> Map.copyOf(values));
        translations = Map.copyOf(copied);
    }

    /** Resolves the requested locale, then the configured fallback locale, then the native fallback text. */
    public String resolve(String locale, String key, String fallback) {
        String requested = translations.getOrDefault(normalizeLocale(locale), Map.of()).get(key);
        if (requested != null && !requested.isBlank()) return requested;
        String configured = translations.getOrDefault(fallbackLocale, Map.of()).get(key);
        return configured == null || configured.isBlank() ? fallback : configured;
    }

    public boolean missing(String locale, String key) {
        return !translations.getOrDefault(normalizeLocale(locale), Map.of()).containsKey(key);
    }

    public static String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) return "en_us";
        return locale.toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
