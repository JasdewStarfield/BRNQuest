package yourscraft.jasdewstarfield.brnquest.progress;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.*;
import static org.junit.jupiter.api.Assertions.*;

class QuestVisibilityPlanTest {
    @Test void ordinaryBookReusesOnlyImmutableDefinitionIds() {
        var plan = plan(quest("plain", QuestBehavior.DEFAULT));
        var first = plan.visible(new PlayerProgress(), q -> { throw new AssertionError("Unused dependency check"); });
        assertEquals(Set.of("test:plain"), first);
        assertSame(first, plan.visible(new PlayerProgress(), q -> false));
        assertThrows(UnsupportedOperationException.class, () -> first.clear());
    }

    @Test void taskThresholdResetAndRepeatHistoryAreReadEveryTime() {
        var quest = quest("hidden", rules(false, false, true, 2));
        var plan = plan(quest);
        var progress = new PlayerProgress();
        assertTrue(plan.visible(progress, q -> true).isEmpty());
        progress.addTaskProgress("test:hidden_a", 99);
        assertTrue(plan.visible(progress, q -> true).isEmpty(), "Counts objectives, not total progress");
        progress.addTaskProgress("test:hidden_b", 1);
        assertEquals(Set.of("test:hidden"), plan.visible(progress, q -> true));
        progress = new PlayerProgress();
        assertTrue(plan.visible(progress, q -> true).isEmpty(), "Another owner/reset must not inherit visibility");
        progress.completedCycle("test:hidden", 1, 2);
        assertEquals(Set.of("test:hidden"), plan.visible(progress, q -> true));
    }

    @Test void dependencyVisibilityAndCompletionGatesStayIndependentAndLive() {
        var parent = quest("parent", rules(false, false, true, 0));
        var child = quest("child", rules(true, true, false, 0), id("parent"));
        var plan = plan(child, parent);
        var progress = new PlayerProgress();
        assertTrue(plan.visible(progress, q -> true).isEmpty());
        progress.status("test:parent", QuestStatus.COMPLETED);
        assertEquals(Set.of("test:parent"), plan.visible(progress, q -> false));
        assertEquals(Set.of("test:parent", "test:child"), plan.visible(progress, q -> true));
        // A replacement definition with the same IDs may remove the parent's hidden rule.
        assertEquals(Set.of("test:parent", "test:child"),
                plan(quest("parent", QuestBehavior.DEFAULT), child).visible(new PlayerProgress(), q -> true));
    }

    @Test void indexedEvaluatorMatchesPreviousRulesAcrossMixedDependencyDags() {
        var random = new Random(20260921);
        for (int run = 0; run < 100; run++) {
            List<QuestDefinition> quests = new ArrayList<>();
            var progress = new PlayerProgress();
            Set<ResourceLocation> startable = new HashSet<>();
            for (int i = 0; i < 40; i++) {
                var q = quest("q" + i, rules(random.nextBoolean(), random.nextBoolean(),
                        random.nextBoolean(), random.nextInt(4)),
                        i == 0 ? new ResourceLocation[0] : new ResourceLocation[]{id("q" + random.nextInt(i))});
                quests.add(q);
                progress.status(q.id().toString(), QuestStatus.values()[random.nextInt(QuestStatus.values().length)]);
                if (random.nextBoolean()) progress.completedCycle(q.id().toString(), 1, 2);
                for (var t : q.tasks()) progress.addTaskProgress(t.id().toString(), random.nextInt(3));
                if (random.nextBoolean()) startable.add(q.id());
            }
            var plan = plan(quests.toArray(QuestDefinition[]::new));
            Predicate<QuestDefinition> gate = q -> startable.contains(q.id());
            Set<String> expected = new HashSet<>();
            for (var q : quests) if (previous(q, quests, progress, gate)) expected.add(q.id().toString());
            assertEquals(expected, plan.visible(progress, gate), "DAG " + run);
        }
    }

    // Reference uses the pre-optimization full scan and full objective count, independently of the index.
    private static boolean previous(QuestDefinition q, List<QuestDefinition> all, PlayerProgress p,
                                    Predicate<QuestDefinition> gate) {
        var b = q.behavior();
        var status = p.status(q.id().toString());
        boolean complete = status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED
                || p.completionCycles(q.id().toString()) > 0;
        long hits = q.tasks().stream().filter(t -> p.taskProgress(t.id().toString()) >= 1).count();
        if (b.invisibleUntilComplete() && !complete && (b.visibleAfterTasks() <= 0 || hits < b.visibleAfterTasks())) return false;
        if (b.hideUntilDependenciesComplete() && !gate.test(q)) return false;
        if (b.hideUntilDependenciesVisible()) for (var dep : q.dependencies()) {
            var parent = all.stream().filter(x -> x.id().equals(dep)).findFirst().orElse(null);
            if (parent != null && !previous(parent, all, p, gate)) return false;
        }
        return true;
    }
    private static QuestBehavior rules(boolean visible, boolean complete, boolean hidden, int threshold) {
        return new QuestBehavior(visible, complete, hidden, threshold, false, false, false,
                DependencyRequirement.ALL_COMPLETED, 0, false, false, 0, false);
    }
    private static QuestDefinition quest(String name, QuestBehavior behavior, ResourceLocation... deps) {
        return new QuestDefinition(id("book"), id(name), id("chapter"), name, "", "", "", 0, 0,
                List.of(deps), List.of(new TaskDefinition(id("book"), id(name + "_a"), id("type"), Map.of(), false),
                new TaskDefinition(id("book"), id(name + "_b"), id("type"), Map.of(), false)),
                List.of(), "", QuestAppearance.DEFAULT, behavior, Map.of());
    }
    private static QuestVisibilityPlan plan(QuestDefinition... quests) {
        return new QuestVisibilityPlan(QuestBookSnapshot.of(new QuestBookDefinition(id("book"), 1, "Book", List.of(),
                List.of(new ChapterDefinition(id("book"), id("chapter"), id("group"), "Chapter", "", 0, List.of(quests))), Map.of())));
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("test", path); }
}
