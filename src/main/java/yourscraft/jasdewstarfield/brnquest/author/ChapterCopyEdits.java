package yourscraft.jasdewstarfield.brnquest.author;

import yourscraft.jasdewstarfield.brnquest.editor.ConfigNormalizationContext;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.*;
import java.util.stream.Collectors;

/** Complete same-book chapter cloning, assembled before the enclosing session creates one undo step. */
public final class ChapterCopyEdits {
    private ChapterCopyEdits() {}
    public static AuthorOperationResult<DraftChange> copy(QuestBookDefinition book, ResourceLocation sourceId) {
        return copy(book, sourceId, ConfigNormalizationContext.withoutRegistries());
    }

    /** Passes the current registry lookup through the complete atomic copy. */
    public static AuthorOperationResult<DraftChange> copy(QuestBookDefinition book, ResourceLocation sourceId, ConfigNormalizationContext context) {
        ChapterDefinition source = book.chapters().stream().filter(c -> c.id().equals(sourceId)).findFirst().orElse(null);
        if (source == null) return AuthorOperationResult.failure(AuthorOperationResult.Status.NOT_FOUND, "CHAPTER_NOT_FOUND", "Chapter no longer exists");
        if (book.quests().size() + source.quests().size() > yourscraft.jasdewstarfield.brnquest.BrnQuestConstants.MAX_QUESTS)
            return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST, "QUEST_LIMIT", "Chapter copy exceeds the quest capacity");
        ResourceLocation targetId = ResourceLocation.fromNamespaceAndPath(sourceId.getNamespace(),
                sourceId.getPath() + "_copy_" + UUID.randomUUID().toString().replace("-", ""));
        int number = nextNumber(book, source);
        var seed = new ChapterDefinition(book.id(), targetId, source.groupId(),
                QuestCopyTitles.title(source.title(), book.localization().fallbackLocale(), number), source.icon(),
                source.order(), List.of(), source.canvasScene().copied().write(source.extensions()), source.questDefaults(), source.consumeItems(), null, source.defaultHideDependencyLines());
        var added = DraftBookEditor.addChapter(book, seed);
        if (!added.success()) return added;
        QuestBookDefinition result = added.value().book();
        if (!source.quests().isEmpty()) {
            var copied = QuestSelectionEdits.copyInto(result, source.quests().stream().map(QuestDefinition::id).collect(Collectors.toSet()), 0, 0, targetId, context);
            if (!copied.success()) return copied;
            result = copied.value().book();
        }
        var target = result.chapters().stream().filter(c -> c.id().equals(targetId)).findFirst().orElseThrow();
        ResourceLocation focus = null;
        // Graph copying retains source order, so the autofocus target follows the corresponding copied identity.
        for (int i = 0; i < source.quests().size(); i++) {
            if (source.quests().get(i).id().equals(source.autofocusQuestId())) focus = target.quests().get(i).id();
        }
        var focused = new ChapterDefinition(book.id(), targetId, target.groupId(), target.title(), target.icon(), target.order(),
                target.quests(), target.extensions(), target.questDefaults(), target.consumeItems(), focus, target.defaultHideDependencyLines());
        var updated = DraftBookEditor.updateChapter(result, targetId, focused);
        if (!updated.success()) return updated;
        result = updated.value().book();
        String sourceKey = BookText.structureTitleKey(book, "chapter", sourceId);
        String prefix = sourceKey.substring(0, sourceKey.length() - "title".length());
        String targetPrefix = "chapter." + targetId + ".";
        var locales = new TreeMap<String, Map<String, String>>();
        result.localization().translations().forEach((locale, values) -> {
            var copied = new TreeMap<>(values);
            values.forEach((key, text) -> {
                if (key.startsWith(prefix)) {
                    String suffix = key.substring(prefix.length());
                    copied.put(targetPrefix + suffix, suffix.equals("title") && !text.isBlank()
                            ? QuestCopyTitles.title(text, locale, number) : text);
                }
            });
            locales.put(locale, copied);
        });
        result = new QuestBookDefinition(result.id(), result.schemaVersion(), result.title(), result.chapterGroups(), result.chapters(),
                result.legacyIds(), new BookLocalization(result.localization().fallbackLocale(), locales), result.extensions(), result.questDefaults(), result.settings());
        var siblings = book.chapters().stream().filter(c -> c.groupId().equals(source.groupId()))
                .sorted(Comparator.comparingInt(ChapterDefinition::order).thenComparing(c -> c.id().toString())).toList();
        var ordered = DraftBookEditor.moveChapterOrder(result, targetId, siblings.indexOf(source) + 1);
        if (!ordered.success()) return ordered;
        result = ordered.value().book();
        var affected = new ArrayList<ResourceLocation>();
        result.chapters().forEach(c -> affected.add(c.id()));
        target.quests().forEach(q -> { affected.add(q.id()); q.tasks().forEach(t -> affected.add(t.id())); q.rewards().forEach(r -> affected.add(r.id())); });
        return AuthorOperationResult.success("CHAPTER_COPIED", "Chapter copied; external dependencies and private references retained", new DraftChange(result, affected));
    }
    /** Check every explicitly translated title, without materializing missing locales. */
    private static int nextNumber(QuestBookDefinition book, ChapterDefinition source) {
        String key = BookText.structureTitleKey(book, "chapter", source.id());
        var titles = new TreeMap<String, String>();
        book.localization().translations().forEach((locale, values) -> {
            String text = values.get(key); if (text != null && !text.isBlank()) titles.put(locale, text);
        });
        for (int n = 1; ; n++) {
            final int number = n;
            String nativeTitle = QuestCopyTitles.title(source.title(), book.localization().fallbackLocale(), n);
            boolean exists = book.chapters().stream().anyMatch(c -> c.title().equals(nativeTitle)
                    || titles.entrySet().stream().anyMatch(e -> BookText.structureTitle(book, "chapter", c.id(), e.getKey(), c.title())
                            .equals(QuestCopyTitles.title(e.getValue(), e.getKey(), number))));
            if (!exists) return n;
        }
    }
}
