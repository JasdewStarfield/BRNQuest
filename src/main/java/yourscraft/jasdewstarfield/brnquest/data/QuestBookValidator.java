package yourscraft.jasdewstarfield.brnquest.data;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeExecutor;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeExecutor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Central validation shared by native reloads and imported definitions. */
public final class QuestBookValidator {
    private QuestBookValidator() {}

    public static void validate(QuestBookDefinition book, DiagnosticReport report) {
        if (book.quests().size() > BrnQuestConstants.MAX_QUESTS) {
            add(report, Diagnostic.Severity.FATAL, "BQV-124", book.id(),
                    "Quest count " + book.quests().size() + " exceeds runtime limit "
                            + BrnQuestConstants.MAX_QUESTS);
        }
        Set<ResourceLocation> groups = new HashSet<>();
        book.chapterGroups().forEach(group -> {
            if (!group.bookId().equals(book.id())) add(report, Diagnostic.Severity.FATAL, "BQV-110", group.id(), "Chapter group has a different bookId");
            if (QuestIconValue.isTexture(group.icon()) && QuestIconValue.textureId(group.icon()).isEmpty())
                add(report, Diagnostic.Severity.ERROR, "BQV-123", group.id(), "Texture icon must contain a valid ResourceLocation");
            if (!groups.add(group.id())) add(report, Diagnostic.Severity.FATAL, "BQV-111", group.id(), "Duplicate chapter group ID");
        });

        Set<ResourceLocation> chapters = new HashSet<>();
        Map<ResourceLocation, QuestDefinition> quests = new HashMap<>();
        Set<ResourceLocation> typedIds = new HashSet<>();
        book.chapters().forEach(chapter -> {
            if (!chapter.bookId().equals(book.id())) add(report, Diagnostic.Severity.FATAL, "BQV-112", chapter.id(), "Chapter has a different bookId");
            if (!chapters.add(chapter.id())) add(report, Diagnostic.Severity.FATAL, "BQV-113", chapter.id(), "Duplicate chapter ID");
            if (!groups.contains(chapter.groupId())) add(report, Diagnostic.Severity.ERROR, "BQV-114", chapter.id(), "Missing chapter group " + chapter.groupId());
            chapter.quests().forEach(quest -> {
                if (!quest.bookId().equals(book.id()) || !quest.chapterId().equals(chapter.id())) add(report, Diagnostic.Severity.FATAL, "BQV-115", quest.id(), "Quest ownership does not match its container");
                if (quests.putIfAbsent(quest.id(), quest) != null) add(report, Diagnostic.Severity.FATAL, "BQV-101", quest.id(), "Duplicate quest ID");
                if (QuestIconValue.isTexture(quest.icon()) && QuestIconValue.textureId(quest.icon()).isEmpty()) {
                    add(report, Diagnostic.Severity.ERROR, "BQV-123", quest.id(),
                            "Texture icon must contain a valid ResourceLocation");
                }
                quest.tasks().forEach(task -> {
                    if (!typedIds.add(task.id())) add(report, Diagnostic.Severity.FATAL, "BQV-116", task.id(), "Duplicate task/reward ID");
                    var type = TaskTypeRegistry.get(task.typeId());
                    if (type == null) {
                        // Missing extension types remain round-trippable placeholders so a removed mod can return later.
                        add(report, Diagnostic.Severity.WARN, "BQV-117", task.id(), "Unknown task type " + task.typeId());
                    } else {
                        TaskTypeExecutor.configError(type, ApiViews.task(task)).ifPresent(message -> add(report, Diagnostic.Severity.ERROR,
                                "BQV-119", task.id(), "Invalid task config: " + message));
                    }
                });
                quest.rewards().forEach(reward -> {
                    if (!typedIds.add(reward.id())) add(report, Diagnostic.Severity.FATAL, "BQV-116", reward.id(), "Duplicate task/reward ID");
                    var type = RewardTypeRegistry.get(reward.typeId());
                    if (type == null) {
                        // Publishing must not destroy or strand opaque reward data owned by a temporarily absent mod.
                        add(report, Diagnostic.Severity.WARN, "BQV-118", reward.id(), "Unknown reward type " + reward.typeId());
                    } else {
                        RewardTypeExecutor.configError(type, ApiViews.reward(reward)).ifPresent(message -> add(report, Diagnostic.Severity.ERROR,
                                "BQV-120", reward.id(), "Invalid reward config: " + message));
                    }
                });
            });
        });

        quests.values().forEach(quest -> quest.dependencies().forEach(dependency -> {
            if (!quests.containsKey(dependency)) add(report, Diagnostic.Severity.ERROR, "BQV-102", quest.id(), "Missing dependency " + dependency);
        }));
        Set<ResourceLocation> visiting = new HashSet<>();
        Set<ResourceLocation> visited = new HashSet<>();
        quests.keySet().forEach(id -> detectCycle(id, quests, visiting, visited, report));
        Set<ResourceLocation> reachable = new HashSet<>();
        boolean changed;
        do {
            changed = false;
            for (QuestDefinition quest : quests.values()) {
                if (!reachable.contains(quest.id()) && reachable.containsAll(quest.dependencies())) {
                    reachable.add(quest.id());
                    changed = true;
                }
            }
        } while (changed);
        quests.keySet().stream().filter(id -> !reachable.contains(id)).forEach(id ->
                add(report, Diagnostic.Severity.ERROR, "BQV-122", id,
                        "Quest is unreachable from a dependency root"));
        Set<ResourceLocation> taskIds = book.quests().stream().flatMap(quest -> quest.tasks().stream())
                .map(TaskDefinition::id).collect(java.util.stream.Collectors.toSet());
        Set<ResourceLocation> rewardIds = book.quests().stream().flatMap(quest -> quest.rewards().stream())
                .map(RewardDefinition::id).collect(java.util.stream.Collectors.toSet());
        book.legacyIds().forEach((legacyId, targetId) -> {
            boolean validTarget = legacyId.startsWith("@task:") ? taskIds.contains(targetId)
                    : legacyId.startsWith("@reward:") ? rewardIds.contains(targetId) : quests.containsKey(targetId);
            if (!validTarget) add(report, Diagnostic.Severity.ERROR, "BQV-121", targetId,
                    "Legacy alias " + legacyId + " targets a missing object");
        });
    }

    private static void detectCycle(ResourceLocation id, Map<ResourceLocation, QuestDefinition> quests,
                                    Set<ResourceLocation> visiting, Set<ResourceLocation> visited, DiagnosticReport report) {
        if (visited.contains(id) || !quests.containsKey(id)) return;
        if (!visiting.add(id)) {
            add(report, Diagnostic.Severity.FATAL, "BQV-103", id, "Dependency cycle");
            return;
        }
        quests.get(id).dependencies().forEach(dependency -> detectCycle(dependency, quests, visiting, visited, report));
        visiting.remove(id);
        visited.add(id);
    }

    private static void add(DiagnosticReport report, Diagnostic.Severity severity, String code, ResourceLocation id, String message) {
        report.add(new Diagnostic(severity, code, "", "", id.toString(), message));
    }
}
