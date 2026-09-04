package yourscraft.jasdewstarfield.brnquest.runtime;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.task.TaskContext;
import yourscraft.jasdewstarfield.brnquest.task.TaskType;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestBookReloadTransactionTest {
    private static final ResourceLocation TYPE_A = id("type_a");
    private static final ResourceLocation TYPE_B = id("type_b");

    @BeforeEach
    void installKnownBaseline() {
        resetScriptTypes();
        assertTrue(QuestBookManager.get().install(book("baseline", TaskTypes.CHECKMARK, false),
                new DiagnosticReport()));
    }

    @AfterEach
    void clearScriptTypes() {
        resetScriptTypes();
    }

    @Test
    void validBookAndSealedTypesBecomeActiveTogether() {
        TaskType<Map<String, String>> type = taskType();
        ScriptExtensionRegistry.beginRegistration();
        ScriptExtensionRegistry.stageTask(TYPE_A, type);
        ScriptExtensionRegistry.sealRegistration();

        assertNull(TaskTypeRegistry.get(TYPE_A));
        assertTrue(QuestBookReloadTransaction.install(book("candidate", TYPE_A, false),
                new DiagnosticReport(), id("candidate_resource")));

        assertSame(type, TaskTypeRegistry.get(TYPE_A));
        assertEquals(id("candidate"), QuestBookManager.get().active().orElseThrow().book().id());
        assertEquals(id("candidate_resource"), QuestBookManager.get().activeResource().orElseThrow());
    }

    @Test
    void invalidBookDiscardsNewTypesAndPreservesThePriorPair() {
        TaskType<Map<String, String>> original = installScriptBook(TYPE_A, "old");

        ScriptExtensionRegistry.beginRegistration();
        ScriptExtensionRegistry.stageTask(TYPE_B, taskType());
        ScriptExtensionRegistry.sealRegistration();
        DiagnosticReport report = new DiagnosticReport();

        assertFalse(QuestBookReloadTransaction.install(book("invalid", TYPE_B, true), report, id("invalid")));
        assertEquals(id("old"), QuestBookManager.get().active().orElseThrow().book().id());
        assertEquals(id("old_resource"), QuestBookManager.get().activeResource().orElseThrow());
        assertSame(original, TaskTypeRegistry.get(TYPE_A));
        assertNull(TaskTypeRegistry.get(TYPE_B));
        assertTrue(report.hasFatal());
    }

    @Test
    void scriptFailureRejectsAnOtherwiseValidBook() {
        TaskType<Map<String, String>> original = installScriptBook(TYPE_A, "old");

        ScriptExtensionRegistry.beginRegistration();
        ScriptExtensionRegistry.stageTask(TYPE_B, taskType());
        ScriptExtensionRegistry.failRegistration("server_scripts:broken.js failed");
        DiagnosticReport report = new DiagnosticReport();

        assertFalse(QuestBookReloadTransaction.install(book("new", TYPE_B, false), report, id("new")));
        assertEquals(id("old"), QuestBookManager.get().active().orElseThrow().book().id());
        assertEquals(id("old_resource"), QuestBookManager.get().activeResource().orElseThrow());
        assertSame(original, TaskTypeRegistry.get(TYPE_A));
        assertNull(TaskTypeRegistry.get(TYPE_B));
        assertTrue(report.diagnostics().stream().anyMatch(value -> value.code().equals("BQV-006")
                && value.message().contains("broken.js")));
    }

    private static TaskType<Map<String, String>> installScriptBook(ResourceLocation typeId, String bookPath) {
        TaskType<Map<String, String>> type = taskType();
        ScriptExtensionRegistry.beginRegistration();
        ScriptExtensionRegistry.stageTask(typeId, type);
        ScriptExtensionRegistry.sealRegistration();
        assertTrue(QuestBookReloadTransaction.install(book(bookPath, typeId, false),
                new DiagnosticReport(), id(bookPath + "_resource")));
        return type;
    }

    private static void resetScriptTypes() {
        ScriptExtensionRegistry.rollbackRegistration();
        ScriptExtensionRegistry.beginRegistration();
        ScriptExtensionRegistry.sealRegistration();
        ScriptExtensionRegistry.commitSealedRegistration();
    }

    private static QuestBookDefinition book(String path, ResourceLocation taskType, boolean selfCycle) {
        ResourceLocation bookId = id(path);
        ResourceLocation groupId = id(path + "_group");
        ResourceLocation chapterId = id(path + "_chapter");
        ResourceLocation questId = id(path + "_quest");
        TaskDefinition task = new TaskDefinition(bookId, id(path + "_task"), taskType, Map.of(), false);
        QuestDefinition quest = new QuestDefinition(bookId, questId, chapterId, "Quest", "", "",
                "minecraft:book", 0, 0, selfCycle ? List.of(questId) : List.of(), List.of(task), List.of(), "");
        return new QuestBookDefinition(bookId, 1, "Book",
                List.of(new ChapterGroupDefinition(bookId, groupId, "Group", 0)),
                List.of(new ChapterDefinition(bookId, chapterId, groupId, "Chapter", "minecraft:book", 0,
                        List.of(quest))), Map.of());
    }

    private static TaskType<Map<String, String>> taskType() {
        return new TaskType<>() {
            public Codec<Map<String, String>> configCodec() {
                return Codec.unboundedMap(Codec.STRING, Codec.STRING);
            }

            public boolean satisfied(TaskContext context, Map<String, String> config) {
                return false;
            }

            public Component describe(yourscraft.jasdewstarfield.brnquest.api.TaskView task,
                                      Map<String, String> config) {
                return Component.literal("script");
            }
        };
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("brnquest_reload_test", path);
    }
}
