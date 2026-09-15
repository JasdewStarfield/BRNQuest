package yourscraft.jasdewstarfield.brnquest.data;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import yourscraft.jasdewstarfield.brnquest.data.text.DocumentFormat;
import yourscraft.jasdewstarfield.brnquest.data.text.ResolvedDocument;

/** Shared semantic keys for author text; these are book-local keys, never Minecraft language keys. */
public final class BookText {
    private BookText() {}

    public static String questPrefix(QuestDefinition quest) {
        return "quest." + (quest.legacyId().isBlank() ? quest.id() : quest.legacyId()) + ".";
    }

    public static String quest(QuestBookDefinition book, QuestDefinition quest, String locale,
                               String field, String fallback) {
        return book.localization().resolve(locale, questPrefix(quest) + field, fallback);
    }

    /** Resolves description text and its adjacent format from exactly the same locale source. */
    public static ResolvedDocument resolveQuestDescription(QuestBookDefinition book, QuestDefinition quest, String locale) {
        String requestedLocale = BookLocalization.normalizeLocale(locale);
        String fallbackLocale = book.localization().fallbackLocale();
        String prefix = questPrefix(quest);
        ResolvedDocument requested = explicitDescription(book.localization(), prefix, requestedLocale);
        if (requested != null) return requested;
        if (!fallbackLocale.equals(requestedLocale)) {
            ResolvedDocument fallback = explicitDescription(book.localization(), prefix, fallbackLocale);
            if (fallback != null) return fallback;
        }
        return new ResolvedDocument(quest.description(), quest.descriptionFormat(), fallbackLocale);
    }

    /** Editing an absent non-fallback locale starts literal and empty instead of inheriting fallback Markdown. */
    public static ResolvedDocument questDescriptionForEditing(QuestBookDefinition book, QuestDefinition quest, String locale) {
        String normalized = BookLocalization.normalizeLocale(locale);
        if (normalized.equals(book.localization().fallbackLocale()))
            return new ResolvedDocument(quest.description(), quest.descriptionFormat(), normalized);
        ResolvedDocument explicit = explicitDescription(book.localization(), questPrefix(quest), normalized);
        return explicit == null ? new ResolvedDocument("", DocumentFormat.PLAIN, normalized) : explicit;
    }

    private static ResolvedDocument explicitDescription(BookLocalization localization, String prefix, String locale) {
        Map<String, String> values = localization.translations().getOrDefault(locale, Map.of());
        String text = values.get(prefix + "quest_desc");
        if (text == null || text.isBlank()) return null;
        return new ResolvedDocument(text, DocumentFormat.parse(values.get(prefix + "quest_desc_format")), locale);
    }

    /** The root title key also reads existing imported translation tables without migration. */
    public static String title(QuestBookDefinition book, String locale) {
        return book.localization().resolve(locale, "title", book.title());
    }

    public static String structureTitle(QuestBookDefinition book, String kind, ResourceLocation id,
                                        String locale, String fallback) {
        return book.localization().resolve(locale, structureTitleKey(book, kind, id), fallback);
    }

    /** Shares the exact legacy/native key between structure display and localized property updates. */
    public static String structureTitleKey(QuestBookDefinition book, String kind, ResourceLocation id) {
        // Historical source IDs remain usable; sorting prevents hash-map order from choosing a title.
        String sourceId = book.legacyIds().entrySet().stream()
                .filter(entry -> !entry.getKey().startsWith("@") && entry.getValue().equals(id))
                .map(Map.Entry::getKey).sorted().findFirst().orElse(id.toString());
        return kind + "." + sourceId + ".title";
    }
}
