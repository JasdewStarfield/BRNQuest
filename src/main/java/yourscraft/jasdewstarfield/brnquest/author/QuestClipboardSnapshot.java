package yourscraft.jasdewstarfield.brnquest.author;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/** Immutable definition-only snapshot; source objects need not exist when it is pasted. */
public record QuestClipboardSnapshot(QuestBookDefinition content) {
    public static final int MAX_BYTES = 65536;
    public QuestClipboardSnapshot {
        Objects.requireNonNull(content);
        if (content.chapters().size() != 1 || content.quests().isEmpty()
                || content.quests().size() > yourscraft.jasdewstarfield.brnquest.BrnQuestConstants.MAX_QUESTS
                || content.quests().stream().map(QuestDefinition::id).distinct().count() != content.quests().size()
                || content.quests().stream().anyMatch(q -> !Double.isFinite(q.x()) || !Double.isFinite(q.y())))
            throw new IllegalArgumentException("Invalid quest selection snapshot");
    }
    public static QuestClipboardSnapshot capture(QuestBookDefinition book, Set<ResourceLocation> ids) {
        var selected = book.quests().stream().filter(q -> ids.contains(q.id())).toList();
        if (selected.isEmpty() || selected.size() != ids.size() || selected.stream().map(QuestDefinition::chapterId).distinct().count() != 1)
            throw new IllegalArgumentException("Select tasks from one chapter");
        var prefixes = selected.stream().map(BookText::questPrefix).toList();
        var locales = new TreeMap<String, Map<String, String>>();
        book.localization().translations().forEach((locale, values) -> {
            var text = new TreeMap<String, String>();
            values.forEach((key, value) -> { if (prefixes.stream().anyMatch(key::startsWith)) text.put(key, value); });
            locales.put(locale, text);
        });
        // Only the selected definitions and their locale keys travel; unrelated book data and progress do not.
        var chapter = book.chapters().stream().filter(c -> c.id().equals(selected.getFirst().chapterId())).findFirst().orElseThrow();
        var seed = new ChapterDefinition(book.id(), chapter.id(), chapter.groupId(), "", "", 0, selected);
        return new QuestClipboardSnapshot(new QuestBookDefinition(book.id(), book.schemaVersion(), "", List.of(), List.of(seed),
                Map.of(), new BookLocalization(book.localization().fallbackLocale(), locales), Map.of()));
    }
    public String encode() {
        String encoded = NativeBookJson.encode(content);
        // A config string has its own 65536-character limit; also reserve room for JSON envelope escaping.
        if (encoded.length() > MAX_BYTES || encoded.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES
                || new com.google.gson.Gson().toJson(encoded).getBytes(StandardCharsets.UTF_8).length
                > yourscraft.jasdewstarfield.brnquest.BrnQuestConstants.MAX_EDITOR_METADATA_BYTES - 4096)
            throw new IllegalArgumentException("Quest snapshot is too large");
        return encoded;
    }
    public static QuestClipboardSnapshot decode(String encoded) {
        if (encoded == null || encoded.length() > MAX_BYTES || encoded.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Missing or oversized quest snapshot");
        return new QuestClipboardSnapshot(NativeBookJson.decode(JsonParser.parseString(encoded).getAsJsonObject()));
    }
    public void requireDestination(ResourceLocation bookId) {
        if (!content.id().equals(bookId)) throw new IllegalArgumentException("Quest clipboard belongs to another book");
    }
    /** Builds a whole candidate before the session validates/publishes it as one history step. */
    public AuthorOperationResult<DraftChange> paste(QuestBookDefinition book, ResourceLocation chapterId, double x, double y) {
        requireDestination(book.id());
        if (!Double.isFinite(x) || !Double.isFinite(y) || book.chapters().stream().noneMatch(c -> c.id().equals(chapterId)))
            return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST, "INVALID_PASTE_DESTINATION", "Paste requires an existing chapter and finite coordinates");
        var sources = content.quests();
        if (book.quests().size() + sources.size() > yourscraft.jasdewstarfield.brnquest.BrnQuestConstants.MAX_QUESTS)
            return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST, "QUEST_LIMIT", "Paste exceeds the quest capacity");
        var sourceIds = sources.stream().map(QuestDefinition::id).collect(Collectors.toSet());
        var existing = book.quests().stream().map(QuestDefinition::id).collect(Collectors.toSet());
        var missing = sources.stream().flatMap(q -> q.dependencies().stream()).filter(id -> !sourceIds.contains(id) && !existing.contains(id)).distinct().toList();
        if (!missing.isEmpty()) return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST,
                "CLIPBOARD_DEPENDENCY_MISSING", "Cannot paste: external dependency no longer exists: " + missing.getFirst());
        Map<ResourceLocation, ResourceLocation> remap = new LinkedHashMap<>();
        sources.forEach(q -> remap.put(q.id(), fresh(q.id())));
        double minX = sources.stream().mapToDouble(QuestDefinition::x).min().orElseThrow();
        double minY = sources.stream().mapToDouble(QuestDefinition::y).min().orElseThrow();
        var result = book;
        var affected = new ArrayList<ResourceLocation>();
        for (var source : sources) {
            var id = remap.get(source.id());
            int number = QuestCopyTitles.nextNumber(result, source, content.localization());
            String title = QuestCopyTitles.title(source.title().isBlank() ? source.id().toString() : source.title(), result.localization().fallbackLocale(), number);
            var tasks = source.tasks().stream().map(t -> new TaskDefinition(book.id(), fresh(t.id()), t.typeId(), t.config(), t.optional())).toList();
            var rewards = source.rewards().stream().map(r -> new RewardDefinition(book.id(), fresh(r.id()), r.typeId(), r.config(), r.claimPolicy(), r.teamReward())).toList();
            var copy = new QuestDefinition(book.id(), id, chapterId, title, source.subtitle(), source.description(), source.icon(),
                    x + source.x() - minX, y + source.y() - minY,
                    source.dependencies().stream().map(dep -> remap.getOrDefault(dep, dep)).toList(), tasks, rewards, "",
                    source.appearance(), source.behavior(), source.extensions());
            var added = DraftBookEditor.addQuest(result, chapterId, copy);
            if (!added.success()) return added;
            result = added.value().book(); affected.addAll(added.value().affectedObjects());
            var locales = new TreeMap<String, Map<String, String>>(result.localization().translations());
            String prefix = BookText.questPrefix(source);
            content.localization().translations().forEach((locale, values) -> {
                var text = new TreeMap<>(locales.getOrDefault(locale, Map.of()));
                values.forEach((key, value) -> {
                    if (key.startsWith(prefix)) {
                        String suffix = key.substring(prefix.length());
                        text.put(BookText.questPrefix(copy) + suffix, suffix.equals("title") && !value.isBlank()
                                ? QuestCopyTitles.title(value, locale, number) : value);
                    }
                });
                locales.put(locale, text);
            });
            result = new QuestBookDefinition(result.id(), result.schemaVersion(), result.title(), result.chapterGroups(), result.chapters(),
                    result.legacyIds(), new BookLocalization(result.localization().fallbackLocale(), locales), result.extensions(), result.questDefaults(), result.settings());
        }
        return AuthorOperationResult.success("QUEST_CLIPBOARD_PASTED", "Quest snapshot pasted; external dependencies retained", new DraftChange(result, affected));
    }
    private static ResourceLocation fresh(ResourceLocation source) {
        // Random identities also avoid reusing progress IDs left by previously deleted copies.
        return ResourceLocation.fromNamespaceAndPath(source.getNamespace(), "copy_" + UUID.randomUUID().toString().replace("-", ""));
    }
}
