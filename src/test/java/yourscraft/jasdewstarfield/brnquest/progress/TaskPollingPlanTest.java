package yourscraft.jasdewstarfield.brnquest.progress;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.*;
import static org.junit.jupiter.api.Assertions.*;

class TaskPollingPlanTest {
    @Test void keepsAuthoredOrderAndOriginalModuloCadenceForMixedTypes() {
        var plan = plan("ten", "five", "ten");
        assertTrue(plan.dueQuests(1, id -> id.getPath().equals("ten") ? 10 : 5).isEmpty());
        assertEquals(List.of(plan.quests.get(1)), plan.dueQuests(5, id -> id.getPath().equals("ten") ? 10 : 5));
        assertEquals(plan.quests, plan.dueQuests(10, id -> id.getPath().equals("ten") ? 10 : 5));
    }

    @Test void typeReplacementAndChangedIntervalsAreVisibleWithoutChangingTheBook() {
        var plan = plan("script");
        var intervals = new HashMap<ResourceLocation, Integer>();
        assertTrue(plan.dueQuests(10, id -> intervals.getOrDefault(id, 0)).isEmpty());
        intervals.put(id("script"), 10);
        assertEquals(plan.quests, plan.dueQuests(10, id -> intervals.getOrDefault(id, 0)));
        intervals.put(id("script"), 3);
        assertTrue(plan.dueQuests(10, id -> intervals.getOrDefault(id, 0)).isEmpty());
        assertEquals(plan.quests, plan.dueQuests(12, id -> intervals.getOrDefault(id, 0)));
        intervals.clear();
        assertTrue(plan.dueQuests(12, id -> intervals.getOrDefault(id, 0)).isEmpty());
    }

    @Test void nonSamplingTicksQueryEachTypeOnceRegardlessOfQuestCount() {
        var names = new String[4096];
        java.util.Arrays.fill(names, "ten");
        var plan = plan(names);
        var calls = new AtomicInteger();
        assertTrue(plan.dueQuests(9, id -> { calls.incrementAndGet(); return 10; }).isEmpty());
        assertEquals(1, calls.get());
    }

    @Test void samplingIdentityIsMemoizedByTypeInstanceAndEmptyKeyStillHasDurableIdentity() {
        var plan = plan("stateful");
        var task = plan.quests.getFirst().tasks().getFirst();
        var calls = new AtomicInteger();
        var type = samplingType("", calls);
        var info = plan.samplingInfo(task, type);
        assertSame(info, plan.samplingInfo(task, type));
        assertEquals(1, calls.get());
        assertEquals("test:stateful|owner|", info.ownerIdentity());
        assertEquals("test:stateful|members|", info.memberIdentity());
        var member = java.util.UUID.randomUUID();
        var progress = new PlayerProgress();
        progress.prepareSamplingIdentity("q", info.taskId(), info.ownerIdentity());
        progress.samplePrepared(info.taskId(), member, true);
        assertTrue(PlayerProgress.load(progress.save()).samplePrepared(info.taskId(), member));
        var replacement = plan.samplingInfo(task, samplingType("replacement", calls));
        assertEquals("test:stateful|owner|replacement", replacement.ownerIdentity());
        assertEquals(2, calls.get());
    }

    /** A minimal stateful extension can rely on the default empty semantic suffix without losing initialization. */
    private static yourscraft.jasdewstarfield.brnquest.task.TaskType<Map<String, String>> samplingType(String key, AtomicInteger calls) {
        return new yourscraft.jasdewstarfield.brnquest.task.TaskType<>() {
            public com.mojang.serialization.Codec<Map<String, String>> configCodec() {
                return com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING, com.mojang.serialization.Codec.STRING);
            }
            public boolean usesSamplingState() { return true; }
            public String samplingIdentity(Map<String, String> config) { calls.incrementAndGet(); return key; }
            public boolean satisfied(yourscraft.jasdewstarfield.brnquest.task.TaskContext context, Map<String, String> config) { return false; }
            public net.minecraft.network.chat.Component describe(yourscraft.jasdewstarfield.brnquest.api.TaskView task, Map<String, String> config) {
                return net.minecraft.network.chat.Component.empty();
            }
        };
    }

    private static TaskPollingPlan plan(String... types) {
        var bookId = id("book");
        var chapterId = id("chapter");
        var quests = new java.util.ArrayList<QuestDefinition>();
        for (int i = 0; i < types.length; i++) {
            var task = new TaskDefinition(bookId, id("task" + i), id(types[i]), Map.of(), false);
            quests.add(new QuestDefinition(bookId, id("quest" + i), chapterId, "Quest", "", "", "", 0, 0,
                    List.of(), List.of(task), List.of(), ""));
        }
        return new TaskPollingPlan(QuestBookSnapshot.of(new QuestBookDefinition(bookId, 1, "Book", List.of(),
                List.of(new ChapterDefinition(bookId, chapterId, id("group"), "Chapter", "", 0, quests)), Map.of())));
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("test", path); }
}
