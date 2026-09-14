package yourscraft.jasdewstarfield.brnquest.client;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.author.*;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.network.AuthoringNetwork;
import java.util.*;

/** Display-only projection of a submitted edit. Pure definition helpers perform no permission, disk or gameplay work. */
public final class ClientEditPreview {
    private ClientEditPreview() {}
    public static QuestBookDefinition mutation(QuestBookDefinition book, AuthoringNetwork.EditorMutationWire w) {
        ResourceLocation target = id(w.targetId()), parent = id(w.parentId()), source = id(w.sourceId());
        Map<String, String> config = w.config() == null ? Map.of() : w.config();
        Map<ResourceLocation, DraftBookEditor.Position> positions = new LinkedHashMap<>();
        if (w.positions() != null) w.positions().forEach(p -> positions.put(ResourceLocation.parse(p.questId()), new DraftBookEditor.Position(p.x(), p.y())));
        return switch (w.action()) {
            case "UPDATE_CANVAS" -> changed(CanvasEdits.replace(book, target, CanvasScene.decode(config.get("scene"))));
            case "MOVE_QUESTS" -> changed(DraftBookEditor.updateQuestPositions(book, positions));
            case "MOVE_CANVAS_SELECTION", "COPY_CANVAS_SELECTION", "DELETE_CANVAS_SELECTION" ->
                    changed(CanvasSelectionEdits.edit(book, target, w.action(), positions, w.x(), w.y()));
            case "COPY_QUESTS" -> changed(QuestSelectionEdits.copy(book, positions.keySet(), w.x(), w.y()));
            case "DELETE_QUESTS" -> changed(DraftBookEditor.removeQuestSelection(book, positions.keySet()));
            case "PASTE_QUESTS" -> changed(QuestClipboardSnapshot.decode(config.get("snapshot")).paste(book, target, w.x(), w.y()));
            case "COPY_CHAPTER" -> changed(ChapterCopyEdits.copy(book, target));
            case "ADD_GROUP" -> changed(DraftBookEditor.addGroup(book, new ChapterGroupDefinition(book.id(), target, w.title(), w.targetIndex())));
            case "UPDATE_GROUP" -> updateGroup(book, target, w.title(), w.targetIndex(), config);
            case "MOVE_GROUP" -> changed(DraftBookEditor.moveGroup(book, target, w.targetIndex()));
            case "DELETE_GROUP" -> changed(DraftBookEditor.removeGroupWithContents(book, target));
            case "ADD_CHAPTER" -> changed(DraftBookEditor.addChapter(book, new ChapterDefinition(book.id(), target, parent, w.title(), "", w.targetIndex(), List.of())));
            case "UPDATE_CHAPTER" -> updateChapter(book, target, parent, w.title(), w.targetIndex(), config);
            case "MOVE_CHAPTER" -> changed(config.containsKey("group")
                    ? DraftBookEditor.moveChapterToGroup(book, target, ResourceLocation.parse(config.get("group")), w.targetIndex())
                    : DraftBookEditor.moveChapterOrder(book, target, w.targetIndex()));
            case "DELETE_CHAPTER" -> changed(DraftBookEditor.removeChapterWithContents(book, target));
            case "UPDATE_BOOK_PROPERTIES" -> updateBook(book, config);
            case "ADD_QUEST" -> changed(DraftBookEditor.createQuest(book, parent, new QuestDefinition(book.id(), target, parent,
                    w.title(), "", "", "", w.x(), w.y(), List.of(), List.of(), List.of(), ""), defaults(config, QuestCreationDefaults.EMPTY)));
            case "COPY_QUEST" -> copyQuest(book, source, target, w.title(), w.x(), w.y());
            case "DELETE_QUEST" -> changed(DraftBookEditor.removeQuestAndReferences(book, target));
            case "ADD_DEPENDENCY" -> changed(DraftBookEditor.addDependency(book, target, source));
            case "REMOVE_DEPENDENCY" -> changed(DraftBookEditor.removeDependency(book, target, source));
            case "UPDATE_QUEST_TRANSLATION" -> config.containsKey(LocalizedSingleLineEdits.FIELD)
                    ? LocalizedSingleLineEdits.apply(book, "quest", target, config.get(LocalizedSingleLineEdits.FIELD), LocalizedSingleLineEdits.values(config))
                    : changed(DraftBookEditor.updateQuestTranslation(book, target, w.title(), config.getOrDefault("title", ""), config.getOrDefault("subtitle", ""), config.getOrDefault("description", "")));
            case "ADD_TASK" -> changed(DraftBookEditor.addTask(book, parent, new TaskDefinition(book.id(), target, source,
                    EntryCreationPolicy.taskConfig(book, parent, source, config), false)));
            case "UPDATE_TASK", "COPY_TASK" -> {
                var original = quest(book, parent).tasks().stream().filter(t -> t.id().equals(source)).findFirst().orElseThrow();
                var value = new TaskDefinition(book.id(), target, original.typeId(), w.action().equals("COPY_TASK") ? original.config() : config,
                        w.action().equals("COPY_TASK") ? original.optional() : w.targetIndex() != 0);
                yield changed(w.action().equals("COPY_TASK") ? DraftBookEditor.copyTask(book, parent, source, value) : DraftBookEditor.updateTask(book, parent, source, value));
            }
            case "PASTE_TASK" -> changed(DraftBookEditor.addTask(book, parent, TypedEntrySnapshot.decode(config.get("snapshot")).task(target)));
            case "MOVE_TASK" -> changed(DraftBookEditor.moveTask(book, parent, target, w.targetIndex()));
            case "DELETE_TASK" -> changed(DraftBookEditor.removeTask(book, parent, target));
            case "ADD_REWARD" -> changed(DraftBookEditor.addReward(book, parent, new RewardDefinition(book.id(), target, source, config,
                    w.title().isBlank() ? EntryCreationPolicy.rewardPolicy(book, source, config) : w.title(),
                    w.title().isBlank() ? book.settings().rewardTeam() : w.targetIndex() != 0)));
            case "UPDATE_REWARD", "COPY_REWARD" -> {
                var original = quest(book, parent).rewards().stream().filter(r -> r.id().equals(source)).findFirst().orElseThrow();
                boolean copy = w.action().equals("COPY_REWARD");
                var value = new RewardDefinition(book.id(), target, original.typeId(), copy ? original.config() : config,
                        copy ? original.claimPolicy() : w.title(), copy ? original.teamReward() : w.targetIndex() != 0);
                yield changed(copy ? DraftBookEditor.copyReward(book, parent, source, value) : DraftBookEditor.updateReward(book, parent, source, value));
            }
            case "PASTE_REWARD" -> changed(DraftBookEditor.addReward(book, parent, TypedEntrySnapshot.decode(config.get("snapshot")).reward(target)));
            case "MOVE_REWARD" -> changed(DraftBookEditor.moveReward(book, parent, target, w.targetIndex()));
            case "DELETE_REWARD" -> changed(DraftBookEditor.removeReward(book, parent, target));
            default -> book; // History uses verified local checkpoints; unknown operations await the server.
        };
    }
    /** Full property forms already hold parsed appearance/behavior; preserve all unrelated definitions. */
    public static QuestBookDefinition questProperties(QuestBookDefinition book, ResourceLocation oldId, QuestDefinition replacement) {
        return changed(oldId.equals(replacement.id()) ? DraftBookEditor.updateQuest(book, oldId, replacement)
                : DraftBookEditor.updateQuestBasics(book, oldId, replacement));
    }
    private static QuestBookDefinition updateBook(QuestBookDefinition book, Map<String, String> c) {
        var updated = changed(DraftBookEditor.updateBookProperties(book, defaults(c, book.questDefaults()),
                c.getOrDefault("fallback_locale", book.localization().fallbackLocale()), LocalizedSingleLineEdits.values(c),
                c.containsKey("book_settings") ? BookSettings.fromJson(JsonParser.parseString(c.get("book_settings"))) : book.settings()));
        return c.containsKey("backgrounds") ? changed(CanvasEdits.replace(updated, book.id(), CanvasScene.decode(c.get("backgrounds")))) : updated;
    }
    private static QuestBookDefinition updateGroup(QuestBookDefinition book, ResourceLocation target, String title, int order, Map<String, String> c) {
        var original = book.chapterGroups().stream().filter(g -> g.id().equals(target)).findFirst().orElseThrow();
        var value = new ChapterGroupDefinition(book.id(), target, c.containsKey(LocalizedSingleLineEdits.FIELD) ? original.title() : title,
                order, c.getOrDefault("icon", original.icon()), c.getOrDefault("description", original.description()), original.extensions());
        return c.containsKey(LocalizedSingleLineEdits.FIELD)
                ? LocalizedSingleLineEdits.apply(changed(DraftBookEditor.updateGroup(book, target, value)), "chapter_group", target, "title", LocalizedSingleLineEdits.values(c))
                : changed(DraftBookEditor.updateGroupProperties(book, target, value, c.getOrDefault("locale", "")));
    }
    private static QuestBookDefinition updateChapter(QuestBookDefinition book, ResourceLocation target, ResourceLocation group, String title, int order, Map<String, String> c) {
        var original = book.chapters().stream().filter(ch -> ch.id().equals(target)).findFirst().orElseThrow();
        var value = new ChapterDefinition(book.id(), target, group, c.containsKey(LocalizedSingleLineEdits.FIELD) ? original.title() : title,
                c.getOrDefault("icon", original.icon()), order, original.quests(), c.containsKey("backgrounds")
                ? original.canvasScene().withBackgrounds(CanvasScene.decode(c.get("backgrounds"))).write(original.extensions()) : original.extensions(),
                defaults(c, original.questDefaults()), c.containsKey("default_consume_items") ? (c.get("default_consume_items").equals("default") ? null : Boolean.valueOf(c.get("default_consume_items"))) : original.consumeItems(),
                c.containsKey("autofocus_id") ? id(c.get("autofocus_id")) : original.autofocusQuestId(),
                c.containsKey("default_hide_dependency_lines") ? Boolean.parseBoolean(c.get("default_hide_dependency_lines")) : original.defaultHideDependencyLines());
        var updated = changed(DraftBookEditor.updateChapter(book, target, value));
        return c.containsKey(LocalizedSingleLineEdits.FIELD)
                ? LocalizedSingleLineEdits.apply(updated, "chapter", target, "title", LocalizedSingleLineEdits.values(c)) : updated;
    }
    private static QuestCreationDefaults defaults(Map<String, String> c, QuestCreationDefaults original) {
        return c.containsKey("quest_defaults") ? QuestCreationDefaults.fromJson(JsonParser.parseString(c.get("quest_defaults"))) : original;
    }
    private static QuestBookDefinition copyQuest(QuestBookDefinition book, ResourceLocation source, ResourceLocation target, String title, double x, double y) {
        var q = quest(book, source);
        // Nested IDs are display placeholders only; server-generated identities replace them on acknowledgement.
        var tasks = q.tasks().stream().map(t -> new TaskDefinition(book.id(), fresh(target), t.typeId(), t.config(), t.optional())).toList();
        var rewards = q.rewards().stream().map(r -> new RewardDefinition(book.id(), fresh(target), r.typeId(), r.config(), r.claimPolicy(), r.teamReward())).toList();
        return changed(DraftBookEditor.copyQuest(book, source, new QuestDefinition(book.id(), target, q.chapterId(), title, q.subtitle(), q.description(),
                q.icon(), x, y, q.dependencies(), tasks, rewards, "", q.appearance(), q.behavior(), q.extensions())));
    }
    private static ResourceLocation fresh(ResourceLocation base) { return ResourceLocation.fromNamespaceAndPath(base.getNamespace(), "preview_"+UUID.randomUUID().toString().replace("-", "")); }
    private static QuestDefinition quest(QuestBookDefinition book, ResourceLocation id) { return book.quests().stream().filter(q -> q.id().equals(id)).findFirst().orElseThrow(); }
    private static ResourceLocation id(String value) { return value == null || value.isBlank() ? null : ResourceLocation.parse(value); }
    private static QuestBookDefinition changed(AuthorOperationResult<DraftChange> result) {
        if (!result.success()) throw new IllegalArgumentException(result.message());
        return result.value().book();
    }
}
