package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.*;

/** Applies only explicitly edited single-line translations; the server derives all semantic keys. */
public final class LocalizedSingleLineEdits {
    public static final String FIELD = "text_field";
    public static final String PREFIX = "text_locale.";
    private LocalizedSingleLineEdits() {}

    public static Map<String, String> values(Map<String, String> config) {
        Map<String, String> values = new TreeMap<>();
        config.forEach((key, value) -> {
            if (key.startsWith(PREFIX)) values.put(key.substring(PREFIX.length()), value);
        });
        return Map.copyOf(values);
    }

    /** Validate before applying any candidate changes, including callers outside the packet decoder. */
    public static void validate(String kind, String field, Map<String, String> values) {
        if (!Set.of("book", "chapter_group", "chapter", "quest").contains(kind)
                || !(field.equals("title") || kind.equals("quest") && field.equals("quest_subtitle")))
            throw new IllegalArgumentException("Unsupported localized single-line field");
        if (values.size() > 64) throw new IllegalArgumentException("Too many edited locales");
        values.forEach((locale, value) -> {
            if (!locale.matches("[a-z0-9_]{2,16}") || value == null || value.length() > 256
                    || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
                throw new IllegalArgumentException("Invalid single-line translation");
        });
    }

    public static QuestBookDefinition apply(QuestBookDefinition book, String kind, ResourceLocation id,
                                            String field, Map<String, String> values) {
        validate(kind, field, values);
        QuestDefinition quest = kind.equals("quest") ? book.quests().stream()
                .filter(q -> q.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown quest")) : null;
        if (kind.equals("chapter_group") && book.chapterGroups().stream().noneMatch(g -> g.id().equals(id))
                || kind.equals("chapter") && book.chapters().stream().noneMatch(c -> c.id().equals(id)))
            throw new IllegalArgumentException("Unknown localized text target");
        String key = kind.equals("book") ? "title" : quest == null ? BookText.structureTitleKey(book, kind, id) : BookText.questPrefix(quest) + field;
        String fallback = book.localization().fallbackLocale();
        Map<String, Map<String, String>> translations = new TreeMap<>(book.localization().translations());
        values.forEach((locale, value) -> {
            Map<String, String> table = new TreeMap<>(translations.getOrDefault(locale, Map.of()));
            // Native fields remain canonical for the fallback locale; other fields are never normalized here.
            if (locale.equals(fallback)) table.remove(key);
            else table.put(key, value);
            if (table.isEmpty()) translations.remove(locale); else translations.put(locale, table);
        });
        String nativeValue = values.get(fallback);
        var groups = book.chapterGroups().stream().map(g -> nativeValue != null && kind.equals("chapter_group") && g.id().equals(id)
                ? new ChapterGroupDefinition(g.bookId(), g.id(), nativeValue, g.order(), g.icon(), g.description(), g.extensions()) : g).toList();
        var chapters = book.chapters().stream().map(c -> {
            var quests = c.quests().stream().map(q -> nativeValue != null && kind.equals("quest") && q.id().equals(id)
                    ? new QuestDefinition(q.bookId(), q.id(), q.chapterId(), field.equals("title") ? nativeValue : q.title(),
                    field.equals("quest_subtitle") ? nativeValue : q.subtitle(), q.description(), q.icon(), q.x(), q.y(),
                    q.dependencies(), q.tasks(), q.rewards(), q.legacyId(), q.appearance(), q.behavior(), q.extensions()) : q).toList();
            return new ChapterDefinition(c.bookId(), c.id(), c.groupId(), nativeValue != null && kind.equals("chapter") && c.id().equals(id)
                    ? nativeValue : c.title(), c.icon(), c.order(), quests, c.extensions(), c.questDefaults(), c.consumeItems(), c.autofocusQuestId());
        }).toList();
        return new QuestBookDefinition(book.id(), book.schemaVersion(), kind.equals("book") && nativeValue != null ? nativeValue : book.title(), groups, chapters, book.legacyIds(),
                new BookLocalization(fallback, translations), book.extensions(), book.questDefaults(), book.settings());
    }
}
