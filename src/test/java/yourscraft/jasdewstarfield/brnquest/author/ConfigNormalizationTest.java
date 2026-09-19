package yourscraft.jasdewstarfield.brnquest.author;

import com.mojang.serialization.Codec;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigNormalizationContext;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.task.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the shared write boundary with external types, opaque data and real item registries. */
class ConfigNormalizationTest {
    private static final ResourceLocation TASK = id("probe_task"), REWARD = id("probe_reward");
    private static final ConfigNormalizationContext CONTEXT = ConfigNormalizationContext.withRegistries(RegistryAccess.EMPTY);
    private static int taskCalls, rewardCalls;

    @BeforeAll static void registerExternalTypes() {
        TaskTypeRegistry.register(TASK, new TaskType<Map<String, String>>() {
            public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
            public Map<String, String> normalizeConfig(ConfigNormalizationContext context, Map<String, String> config) {
                taskCalls++;
                assertSame(RegistryAccess.EMPTY, context.registries().orElseThrow());
                if (config.containsKey("reject")) throw new IllegalArgumentException("Rejected task config");
                return Map.of("owned", config.get("owned").trim());
            }
            public boolean satisfied(TaskContext context, Map<String, String> config) { return true; }
            public Component describe(TaskView task, Map<String, String> config) { return Component.empty(); }
        });
        RewardTypeRegistry.register(REWARD, new RewardType<Map<String, String>>() {
            public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
            public Map<String, String> normalizeConfig(ConfigNormalizationContext context, Map<String, String> config) {
                rewardCalls++;
                assertSame(RegistryAccess.EMPTY, context.registries().orElseThrow());
                return Map.of("owned", config.get("owned").trim());
            }
            public RewardResult execute(RewardContext context, Map<String, String> config) { return RewardResult.success("Probe"); }
        });
    }

    @Test void individualAddUpdateAndCopyNormalizeExactlyOnceAndPreserveOpaqueKeys() {
        var book = book(List.of());
        book = value(DraftBookEditor.addQuest(book, id("chapter"), quest("q", List.of(), List.of()), CONTEXT));
        taskCalls = rewardCalls = 0;
        book = value(DraftBookEditor.addTask(book, id("q"), task("t"), CONTEXT));
        book = value(DraftBookEditor.addReward(book, id("q"), reward("r"), CONTEXT));
        book = value(DraftBookEditor.updateTask(book, id("q"), id("t"), task("t"), CONTEXT));
        book = value(DraftBookEditor.updateReward(book, id("q"), id("r"), reward("r"), CONTEXT));
        book = value(DraftBookEditor.copyTask(book, id("q"), id("t"), task("tc"), CONTEXT));
        book = value(DraftBookEditor.copyReward(book, id("q"), id("r"), reward("rc"), CONTEXT));
        assertEquals(3, taskCalls); assertEquals(3, rewardCalls);
        book.quests().getFirst().tasks().forEach(t -> normalized(t.config()));
        book.quests().getFirst().rewards().forEach(r -> normalized(r.config()));
    }

    @Test void allWholeQuestCopyEntrypointsUseTheSameLookupOncePerNewChild() {
        var source = book(List.of(quest("q", List.of(task("t")), List.of(reward("r")))));
        var clipboard = QuestClipboardSnapshot.decode(QuestClipboardSnapshot.capture(source, Set.of(id("q"))).encode());
        var operations = List.<java.util.function.Supplier<AuthorOperationResult<DraftChange>>>of(
                () -> DraftBookEditor.createQuest(source, id("chapter"), quest("new", List.of(task("tn")), List.of(reward("rn"))), QuestCreationDefaults.EMPTY, CONTEXT),
                () -> DraftBookEditor.copyQuest(source, id("q"), quest("copy", List.of(task("tc")), List.of(reward("rc"))), CONTEXT),
                () -> QuestSelectionEdits.copy(source, Set.of(id("q")), 1, 2, CONTEXT),
                () -> clipboard.paste(source, id("chapter"), 3, 4, CONTEXT),
                () -> CanvasSelectionEdits.edit(source, id("chapter"), "COPY_CANVAS_SELECTION",
                        Map.of(CanvasSelectionKey.quest(id("q")), new DraftBookEditor.Position(0, 0)), 1, 2, CONTEXT),
                () -> ChapterCopyEdits.copy(source, id("chapter"), CONTEXT),
                () -> DraftBookEditor.copyChapter(source, id("chapter"), new ChapterDefinition(id("book"), id("copied_chapter"), id("group"),
                        "Copy", "", 1, List.of(new QuestDefinition(id("book"), id("qc"), id("copied_chapter"), "Copy", "", "", "", 0, 0,
                        List.of(), List.of(task("tc")), List.of(reward("rc")), ""))), CONTEXT));
        for (var operation : operations) {
            taskCalls = rewardCalls = 0;
            var copied = value(operation.get());
            assertEquals(1, taskCalls); assertEquals(1, rewardCalls);
            var added = copied.quests().stream().filter(q -> !q.id().equals(id("q"))).findFirst().orElseThrow();
            normalized(added.tasks().getFirst().config()); normalized(added.rewards().getFirst().config());
            assertTrue(added.tasks().getFirst().optional()); assertTrue(added.rewards().getFirst().teamReward());
            assertEquals("auto_hidden", added.rewards().getFirst().claimPolicy());
        }
        assertEquals(" value ", source.quests().getFirst().tasks().getFirst().config().get("owned"));
    }

    @Test void normalizationIsInsideRevisionAndUndoBoundaryAndSurvivesNativeRoundTrip() {
        var source = book(List.of(quest("q", List.of(task("t")), List.of(reward("r")))));
        var sessions = new EditSessionService(); var server = new Object(); var editor = UUID.randomUUID();
        var initial = DraftSnapshot.of(source, "");
        var handle = sessions.openAuthorized(server, editor, "Editor", initial, 0, 100).value();
        java.util.function.Function<DraftSnapshot, AuthorOperationResult<DraftEditResult>> copy = current -> {
            var change = ChapterCopyEdits.copy(current.book(), id("chapter"), CONTEXT).value();
            return AuthorOperationResult.success("COPIED", "Copied", new DraftEditResult(
                    DraftSnapshot.of(change.book(), ""), change.affectedObjects(), List.of()));
        };
        var changed = sessions.mutateAuthorized(server, editor, handle.sessionId(), source.id(), initial.draftRevision(), 1, 100, copy);
        assertTrue(changed.success()); var revision = changed.value().snapshot().draftRevision();
        int calls = taskCalls;
        var undone = sessions.historyAuthorized(server, editor, handle.sessionId(), source.id(), revision, 3, 100, false);
        assertEquals("DRAFT_UNDONE", undone.code(), undone.message());
        assertEquals(initial.draftRevision(), undone.value().snapshot().draftRevision());
        var redone = sessions.historyAuthorized(server, editor, handle.sessionId(), source.id(), initial.draftRevision(), 4, 100, true);
        assertEquals(revision, redone.value().snapshot().draftRevision());
        assertEquals(calls, taskCalls, "history restores snapshots without rerunning extension callbacks");
        var reloaded = NativeBookJson.decode(com.google.gson.JsonParser.parseString(NativeBookJson.encode(redone.value().snapshot().book())).getAsJsonObject());
        assertEquals(revision, DraftSnapshot.of(reloaded, "").draftRevision());
        normalized(reloaded.chapters().get(1).quests().getFirst().tasks().getFirst().config());
        // Existing conflict recovery deliberately clears history; test it after the successful undo/redo cycle.
        assertFalse(sessions.mutateAuthorized(server, editor, handle.sessionId(), source.id(), initial.draftRevision(), 5, 100, copy).success());
        assertEquals(calls, taskCalls, "stale requests must not even invoke extension normalization");
        assertEquals(revision, sessions.inspectAuthorized(server, source.id(), 6).value().draftRevision());
        assertEquals("UNDO_EMPTY", sessions.historyAuthorized(server, editor, handle.sessionId(), source.id(), revision, 7, 100, false).code());
    }

    @Test void failingChildDoesNotPublishPartialChapterOrConsumeHistory() {
        var bad = new TaskDefinition(id("book"), id("bad"), TASK, Map.of("owned", " x ", "reject", "true"), false);
        var source = book(List.of(quest("q", List.of(task("t"), bad), List.of(reward("r")))));
        var sessions = new EditSessionService(); var server = new Object(); var editor = UUID.randomUUID();
        var initial = DraftSnapshot.of(source, "");
        var handle = sessions.openAuthorized(server, editor, "Editor", initial, 0, 100).value();
        assertThrows(IllegalArgumentException.class, () -> sessions.mutateAuthorized(server, editor, handle.sessionId(), source.id(),
                initial.draftRevision(), 1, 100, current -> { ChapterCopyEdits.copy(current.book(), id("chapter"), CONTEXT); return null; }));
        assertEquals(initial.draftRevision(), sessions.inspectAuthorized(server, source.id(), 2).value().draftRevision());
        assertEquals("UNDO_EMPTY", sessions.historyAuthorized(server, editor, handle.sessionId(), source.id(), initial.draftRevision(), 3, 100, false).code());
    }

    @Test void bothItemIdsCanonicalizeLegacyComponentsAndRetainOpaqueData() {
        var context = ConfigNormalizationContext.withRegistries(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var legacy = Map.of("item", "{id:\"minecraft:stone\",count:7,components:{\"minecraft:custom_name\":'\"Keepsake\"'}}",
                "count", "4", "consume", "true", "private_ref", "normalization:q");
        for (var id : List.of(TaskTypes.ITEM, TaskTypes.ITEM_CHOICE)) {
            var type = TaskTypeRegistry.get(id);
            assertEquals(legacy, type.normalizeConfig(ConfigNormalizationContext.withoutRegistries(), legacy));
            var normalized = type.normalizeConfig(context, legacy);
            var spec = ItemChoiceMatcher.parseConfig(normalized).getOrThrow();
            assertEquals(4, spec.entries().getFirst().requiredCount());
            assertTrue(spec.entries().getFirst().value().contains("Keepsake"));
            assertTrue(spec.entries().getFirst().value().contains("count:1"));
            assertEquals("true", normalized.get("consume_items"));
            assertEquals("normalization:q", normalized.get("private_ref"));
            assertEquals(normalized, type.normalizeConfig(context, normalized));
            assertThrows(IllegalArgumentException.class, () -> type.normalizeConfig(context, Map.of("item", "missing:unknown")));
        }
    }

    private static void normalized(Map<String, String> config) {
        assertEquals("value", config.get("owned"));
        assertEquals("normalization:q", config.get("private_ref"), "opaque ID-looking strings are never rewritten");
        assertEquals("{\"future\":true}", config.get("opaque"));
    }
    private static Map<String, String> config() { return Map.of("owned", " value ", "private_ref", "normalization:q", "opaque", "{\"future\":true}"); }
    private static TaskDefinition task(String name) { return new TaskDefinition(id("book"), id(name), TASK, config(), true); }
    private static RewardDefinition reward(String name) { return new RewardDefinition(id("book"), id(name), REWARD, config(), "auto_hidden", true); }
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("normalization", name); }
    private static QuestDefinition quest(String name, List<TaskDefinition> tasks, List<RewardDefinition> rewards) {
        return new QuestDefinition(id("book"), id(name), id("chapter"), name, "", "", "", 0, 0, List.of(), tasks, rewards, "");
    }
    private static QuestBookDefinition book(List<QuestDefinition> quests) {
        return new QuestBookDefinition(id("book"), 1, "Normalization", List.of(new ChapterGroupDefinition(id("book"), id("group"), "Group", 0)),
                List.of(new ChapterDefinition(id("book"), id("chapter"), id("group"), "Chapter", "", 0, quests)), Map.of());
    }
    private static QuestBookDefinition value(AuthorOperationResult<DraftChange> result) {
        assertTrue(result.success(), result.message()); return result.value().book();
    }
}
