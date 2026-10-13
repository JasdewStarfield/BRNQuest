package yourscraft.jasdewstarfield.brnquest.progress;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;

/** Definition-only index: owner status, dependencies and current objectives are always checked live. */
final class TaskPollingPlan {
    final QuestBookSnapshot snapshot;
    final List<QuestDefinition> quests;
    private final Map<ResourceLocation, BitSet> questsByType = new LinkedHashMap<>();
    private final Map<ResourceLocation, java.util.IdentityHashMap<yourscraft.jasdewstarfield.brnquest.task.TaskType<?>, SamplingInfo>> sampling = new java.util.HashMap<>();
    /** Definition/type-instance memoization cannot retain a player or skip a replaced script implementation. */
    record SamplingInfo(yourscraft.jasdewstarfield.brnquest.api.TaskView view, String taskId, String ownerIdentity, String memberIdentity) {}
    SamplingInfo samplingInfo(yourscraft.jasdewstarfield.brnquest.data.TaskDefinition task, yourscraft.jasdewstarfield.brnquest.task.TaskType<?> type) {
        return sampling.computeIfAbsent(task.id(), ignored -> new java.util.IdentityHashMap<>()).computeIfAbsent(type, current -> {
            var view = yourscraft.jasdewstarfield.brnquest.api.ApiViews.task(task);
            String key = yourscraft.jasdewstarfield.brnquest.task.TaskTypeExecutor.samplingIdentity(current, view);
            // An empty type-owned key still needs a persistent identity for preparation and member state.
            return new SamplingInfo(view, task.id().toString(), task.typeId() + "|owner|" + key,
                    task.typeId() + "|members|" + key);
        });
    }

    TaskPollingPlan(QuestBookSnapshot snapshot) {
        this.snapshot = snapshot;
        this.quests = List.copyOf(snapshot.book().quests());
        for (int index = 0; index < quests.size(); index++) {
            for (var task : quests.get(index).tasks()) {
                questsByType.computeIfAbsent(task.typeId(), ignored -> new BitSet()).set(index);
            }
        }
    }

    /** Re-query intervals, including missing/disabled types, so script replacement needs no stale registry cache. */
    List<QuestDefinition> dueQuests(int tick, ToIntFunction<ResourceLocation> interval) {
        BitSet due = new BitSet();
        questsByType.forEach((type, indices) -> {
            int every = interval.applyAsInt(type);
            if (every > 0 && tick % every == 0) due.or(indices);
        });
        if (due.isEmpty()) return List.of();
        if (due.cardinality() == quests.size()) return quests;
        // Keep authored order: an earlier completion can unlock a later quest during the same tick.
        return due.stream().mapToObj(quests::get).toList();
    }
    /** Query current registrations so replacing a type does not leave a stale preparation index. */
    List<QuestDefinition> statefulQuests(java.util.function.Predicate<ResourceLocation> stateful) {
        BitSet selected = new BitSet();
        questsByType.forEach((type, indices) -> { if (stateful.test(type)) selected.or(indices); });
        return selected.stream().mapToObj(quests::get).toList();
    }
}
