package yourscraft.jasdewstarfield.brnquest.author;

import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.Map;
import java.util.TreeMap;

/** Shared copy-title policy for the form preview and server-owned locale copies. */
public final class QuestCopyTitles {
    private QuestCopyTitles() {}
    public static String title(String original, String locale, int number) {
        // Copying a copy advances its suffix instead of accumulating nested copy labels.
        String base = original.replaceFirst("(?: \\(Copy(?: [0-9]+)?\\)|（副本(?: [0-9]+)?）)$", "");
        String counter = number == 1 ? "" : " " + number;
        return base + (locale.startsWith("zh") ? "（副本" + counter + "）" : " (Copy" + counter + ")");
    }
    public static String suggestedTitle(QuestBookDefinition book, QuestDefinition source) {
        String base = source.title().isBlank() ? source.id().toString() : source.title();
        return title(base, book.localization().fallbackLocale(), nextNumber(book, source));
    }
    /** One ordinal covers all existing title locales; missing translations never become explicit values. */
    public static int nextNumber(QuestBookDefinition book, QuestDefinition source) {
        return nextNumber(book, source, book.localization());
    }
    /** Frozen source translations may differ from the current destination book. */
    public static int nextNumber(QuestBookDefinition book, QuestDefinition source, BookLocalization sourceText) {
        Map<String, String> titles = new TreeMap<>();
        String key = BookText.questPrefix(source) + "title";
        sourceText.translations().forEach((locale, values) -> {
            String text = values.get(key);
            if (text != null && !text.isBlank()) titles.put(locale, text);
        });
        String fallback = book.localization().fallbackLocale();
        String nativeBase = source.title().isBlank() ? source.id().toString() : source.title();
        for (int number = 1; ; number++) {
            String nativeTitle = title(nativeBase, fallback, number);
            final int ordinal = number;
            boolean collision = book.quests().stream().anyMatch(q -> q.title().equals(nativeTitle)
                    || titles.entrySet().stream().anyMatch(e -> BookText.quest(book, q, e.getKey(), "title", q.title())
                            .equals(title(e.getValue(), e.getKey(), ordinal))));
            if (!collision) return number;
        }
    }
}
