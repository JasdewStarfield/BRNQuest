package yourscraft.jasdewstarfield.brnquest.author;

import yourscraft.jasdewstarfield.brnquest.editor.ConfigNormalizationContext;

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
        if (content.chapters().size() != 1 || (content.quests().isEmpty() && content.chapters().getFirst().canvasScene().decorations().isEmpty())
                || content.quests().size() > yourscraft.jasdewstarfield.brnquest.BrnQuestConstants.MAX_QUESTS
                || content.quests().stream().map(QuestDefinition::id).distinct().count() != content.quests().size()
                || content.quests().stream().anyMatch(q -> !Double.isFinite(q.x()) || !Double.isFinite(q.y())))
            throw new IllegalArgumentException("Invalid quest selection snapshot");
    }
    public static QuestClipboardSnapshot capture(QuestBookDefinition book, Set<ResourceLocation> ids) {
        var selected = book.quests().stream().filter(q -> ids.contains(q.id())).toList();
        if (selected.isEmpty()) throw new IllegalArgumentException("Select tasks from one chapter");
        return capture(book, selected.getFirst().chapterId(), ids, Set.of());
    }
    /** A mixed snapshot stores only selected artwork, never chapter backgrounds or unrelated extensions. */
    public static QuestClipboardSnapshot capture(QuestBookDefinition book, ResourceLocation chapterId,
                                                 Set<ResourceLocation> ids, Set<ResourceLocation> decorations) {
        var chapter = book.chapters().stream().filter(c -> c.id().equals(chapterId)).findFirst().orElseThrow(() -> new IllegalArgumentException("Chapter no longer exists"));
        var selected = chapter.quests().stream().filter(q -> ids.contains(q.id())).toList();
        var artwork = chapter.canvasScene().decorations().stream().filter(d -> decorations.contains(d.id())).toList();
        if (selected.size() != ids.size() || artwork.size() != decorations.size() || ids.isEmpty() && decorations.isEmpty())
            throw new IllegalArgumentException("Select existing objects from one chapter");
        var prefixes = selected.stream().map(BookText::questPrefix).toList();
        var locales = new TreeMap<String, Map<String, String>>();
        book.localization().translations().forEach((locale, values) -> {
            var text = new TreeMap<String, String>();
            values.forEach((key, value) -> { if (prefixes.stream().anyMatch(key::startsWith)) text.put(key, value); });
            locales.put(locale, text);
        });
        // Only the selected definitions and their locale keys travel; unrelated book data and progress do not.
        var seed = new ChapterDefinition(book.id(), chapter.id(), chapter.groupId(), "", "", 0, selected, new CanvasScene(artwork, null, null).write(Map.of()), QuestCreationDefaults.EMPTY, null, null, false);
        return new QuestClipboardSnapshot(new QuestBookDefinition(book.id(), book.schemaVersion(), "", List.of(), List.of(seed),
                Map.of(), new BookLocalization(book.localization().fallbackLocale(), locales), Map.of()));
    }
    /** Property editors can copy their unsaved artwork draft into the same canvas clipboard. */
    public static QuestClipboardSnapshot artwork(ResourceLocation book, CanvasScene.Decoration decoration) {
        var chapter = new ChapterDefinition(book, book, book, "", "", 0, List.of(),
                new CanvasScene(List.of(decoration), null, null).write(Map.of()));
        return new QuestClipboardSnapshot(new QuestBookDefinition(book, 1, "", List.of(), List.of(chapter), Map.of()));
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
        return paste(book, chapterId, x, y, ConfigNormalizationContext.withoutRegistries());
    }

    /** Passes the current registry lookup through the complete atomic copy. */
    public AuthorOperationResult<DraftChange> paste(QuestBookDefinition book, ResourceLocation chapterId, double x, double y, ConfigNormalizationContext context) {
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
        double minX = minX();
        double minY = minY();
        var result = book;
        var affected = new ArrayList<ResourceLocation>();
        for (var source : sources) {
            var id = remap.get(source.id());
            int number = QuestCopyTitles.nextNumber(result, source, content.localization());
            String title = QuestCopyTitles.title(source.title().isBlank() ? source.id().toString() : source.title(), result.localization().fallbackLocale(), number);
            var tasks = source.tasks().stream().map(t -> new TaskDefinition(book.id(), fresh(t.id()), t.typeId(), t.config(), t.optional())).toList();
            var rewards = source.rewards().stream().map(r -> new RewardDefinition(book.id(), fresh(r.id()), r.typeId(), r.config(), r.claimPolicy(), r.teamReward())).toList();
            var copy = new QuestDefinition(book.id(), id, chapterId, title, source.subtitle(), source.description(),
                    source.descriptionFormat(), source.icon(),
                    x + source.x() - minX, y + source.y() - minY,
                    source.dependencies().stream().map(dep -> remap.getOrDefault(dep, dep)).toList(), tasks, rewards, "",
                    source.appearance(), source.behavior(), source.extensions());
            var added = DraftBookEditor.addQuest(result, chapterId, copy, context);
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
        var chapter = result.chapters().stream().filter(c -> c.id().equals(chapterId)).findFirst().orElseThrow(() -> new IllegalArgumentException("Chapter no longer exists"));
        var scene = chapter.canvasScene();
        var artwork = new ArrayList<>(scene.decorations());
        for (var source : content.chapters().getFirst().canvasScene().decorations()) {
            artwork.add(source.placed(CanvasScene.newId(), x + source.x() - minX, y + source.y() - minY, source.width(), source.height()));
        }
        if (artwork.size() > CanvasScene.MAX_DECORATIONS)
            return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST, "DECORATION_LIMIT", "Paste exceeds artwork capacity");
        if (!artwork.equals(scene.decorations())) {
            var changed = CanvasEdits.replace(result, chapterId, new CanvasScene(artwork, scene.canvas(), scene.screen(), scene.screenAbove()));
            if (!changed.success()) return changed;
            result = changed.value().book(); affected.addAll(changed.value().affectedObjects());
        }
        return AuthorOperationResult.success("QUEST_CLIPBOARD_PASTED", "Quest snapshot pasted; external dependencies retained", new DraftChange(result, affected));
    }
    public double minX() {
        return Math.min(content.quests().stream().mapToDouble(QuestDefinition::x).min().orElse(Double.POSITIVE_INFINITY),
                content.chapters().getFirst().canvasScene().decorations().stream().mapToDouble(CanvasScene.Decoration::x).min().orElse(Double.POSITIVE_INFINITY));
    }
    public double minY() {
        return Math.min(content.quests().stream().mapToDouble(QuestDefinition::y).min().orElse(Double.POSITIVE_INFINITY),
                content.chapters().getFirst().canvasScene().decorations().stream().mapToDouble(CanvasScene.Decoration::y).min().orElse(Double.POSITIVE_INFINITY));
    }
    private static ResourceLocation fresh(ResourceLocation source) {
        // Random identities also avoid reusing progress IDs left by previously deleted copies.
        return ResourceLocation.fromNamespaceAndPath(source.getNamespace(), "copy_" + UUID.randomUUID().toString().replace("-", ""));
    }
}
