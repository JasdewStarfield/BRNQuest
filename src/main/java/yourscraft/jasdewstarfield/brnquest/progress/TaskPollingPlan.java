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
}
