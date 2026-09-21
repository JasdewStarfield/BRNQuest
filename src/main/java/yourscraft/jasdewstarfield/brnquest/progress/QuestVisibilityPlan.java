package yourscraft.jasdewstarfield.brnquest.progress;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;

/** Snapshot-only visibility index. No player state or computed dynamic visibility survives a call. */
final class QuestVisibilityPlan {
    final QuestBookSnapshot snapshot;
    private final Set<String> unconditional;
    private final List<QuestDefinition> conditional;

    QuestVisibilityPlan(QuestBookSnapshot snapshot) {
        this.snapshot = snapshot;
        Set<String> always = new HashSet<>();
        List<QuestDefinition> dynamic = new ArrayList<>();
        for (var quest : snapshot.quests().values()) {
            var rules = quest.behavior();
            if (!rules.invisibleUntilComplete() && !rules.hideUntilDependenciesComplete()
                    && !rules.hideUntilDependenciesVisible()) always.add(quest.id().toString());
            else dynamic.add(quest);
        }
        unconditional = Set.copyOf(always);
        conditional = List.copyOf(dynamic);
    }

    Set<String> visible(PlayerProgress progress, Predicate<QuestDefinition> dependenciesComplete) {
        // Default books need no progress reads, recursive memo or new ID set for each recipient.
        if (conditional.isEmpty()) return unconditional;
        Set<String> result = new HashSet<>(unconditional);
        Map<ResourceLocation, Boolean> memo = new HashMap<>();
        for (var quest : conditional) {
            if (visible(quest, progress, dependenciesComplete, memo)) result.add(quest.id().toString());
        }
        return Set.copyOf(result);
    }

    private boolean visible(QuestDefinition quest, PlayerProgress progress,
                            Predicate<QuestDefinition> dependenciesComplete, Map<ResourceLocation, Boolean> memo) {
        if (unconditional.contains(quest.id().toString())) return true;
        Boolean cached = memo.get(quest.id());
        if (cached != null) return cached;
        memo.put(quest.id(), false); // Retain the defensive recursion guard for the validated dependency DAG.
        var rules = quest.behavior();
        if (rules.invisibleUntilComplete()) {
            QuestStatus status = progress.status(quest.id().toString());
            boolean complete = status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED
                    || progress.completionCycles(quest.id().toString()) > 0;
            if (!complete) {
                int remaining = rules.visibleAfterTasks();
                if (remaining <= 0) return false;
                // Only this rule needs task progress; stop as soon as its threshold is met.
                for (var task : quest.tasks()) {
                    if (progress.taskProgress(task.id().toString()) >= 1 && --remaining == 0) break;
                }
                if (remaining > 0) return false;
            }
        }
        if (rules.hideUntilDependenciesComplete() && !dependenciesComplete.test(quest)) return false;
        if (rules.hideUntilDependenciesVisible()) for (var id : quest.dependencies()) {
            var parent = snapshot.quests().get(id);
            if (parent != null && !visible(parent, progress, dependenciesComplete, memo)) return false;
        }
        memo.put(quest.id(), true);
        return true;
    }
}
