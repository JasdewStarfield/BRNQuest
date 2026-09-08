package yourscraft.jasdewstarfield.brnquest.data;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;

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

    /** The root title key also reads existing imported translation tables without migration. */
    public static String title(QuestBookDefinition book, String locale) {
        return book.localization().resolve(locale, "title", book.title());
    }

    public static String structureTitle(QuestBookDefinition book, String kind, ResourceLocation id,
                                        String locale, String fallback) {
        // Historical source IDs remain usable; sorting prevents hash-map order from choosing a title.
        String sourceId = book.legacyIds().entrySet().stream()
                .filter(entry -> !entry.getKey().startsWith("@") && entry.getValue().equals(id))
                .map(Map.Entry::getKey).sorted().findFirst().orElse(id.toString());
        return book.localization().resolve(locale, kind + "." + sourceId + ".title", fallback);
    }
}
