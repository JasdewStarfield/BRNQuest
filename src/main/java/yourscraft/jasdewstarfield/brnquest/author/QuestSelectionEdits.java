package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.*;

/** Builds one candidate for a whole selection; only the enclosing session transaction publishes it. */
public final class QuestSelectionEdits {
    private QuestSelectionEdits() {}
    public static AuthorOperationResult<DraftChange> copy(QuestBookDefinition book, Set<ResourceLocation> selected, double dx, double dy) {
        return copyInto(book, selected, dx, dy, null);
    }
    /** A chapter copy seeds its empty destination first, then reuses the same graph/locale copy rules. */
    static AuthorOperationResult<DraftChange> copyInto(QuestBookDefinition book, Set<ResourceLocation> selected,
            double dx, double dy, ResourceLocation destinationChapter) {
        var sources = book.quests().stream().filter(q -> selected.contains(q.id())).toList();
        if (selected.isEmpty() || sources.size() != selected.size() || !Double.isFinite(dx) || !Double.isFinite(dy)
                || sources.stream().map(QuestDefinition::chapterId).distinct().count() != 1)
            return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST, "INVALID_SELECTION", "Select existing quests from one chapter");
        Set<ResourceLocation> used = new HashSet<>();
        book.chapterGroups().forEach(g -> used.add(g.id())); book.chapters().forEach(c -> used.add(c.id()));
        book.quests().forEach(q -> { used.add(q.id()); q.tasks().forEach(t -> used.add(t.id())); q.rewards().forEach(r -> used.add(r.id())); });
        used.addAll(book.legacyIds().values());
        book.legacyIds().keySet().forEach(key -> {
            // Retired IDs must not be reused, including task/reward alias keys.
            String raw = key.startsWith("@task:") ? key.substring(6) : key.startsWith("@reward:") ? key.substring(8) : key;
            var id = ResourceLocation.tryParse(raw); if (id != null) used.add(id);
        });
        Map<ResourceLocation, ResourceLocation> remap = new LinkedHashMap<>();
        sources.forEach(q -> remap.put(q.id(), allocate(used, q.id(), "_copy")));
        QuestBookDefinition result = book;
        var affected = new ArrayList<ResourceLocation>();
        for (QuestDefinition source : sources) {
            ResourceLocation id = remap.get(source.id());
            var tasks = new ArrayList<TaskDefinition>();
            for (int i = 0; i < source.tasks().size(); i++) {
                var t = source.tasks().get(i);
                tasks.add(new TaskDefinition(book.id(), allocate(used, id, "/task_" + i), t.typeId(), t.config(), t.optional()));
            }
            var rewards = new ArrayList<RewardDefinition>();
            for (int i = 0; i < source.rewards().size(); i++) {
                var r = source.rewards().get(i);
                rewards.add(new RewardDefinition(book.id(), allocate(used, id, "/reward_" + i), r.typeId(), r.config(), r.claimPolicy(), r.teamReward()));
            }
            // Internal edges follow the copied nodes; same-book external edges and opaque configs stay unchanged.
            var copy = new QuestDefinition(book.id(), id, destinationChapter == null ? source.chapterId() : destinationChapter, QuestCopyTitles.suggestedTitle(result, source),
                    source.subtitle(), source.description(), source.descriptionFormat(), source.icon(), source.x() + dx, source.y() + dy,
                    source.dependencies().stream().map(dep -> remap.getOrDefault(dep, dep)).toList(), tasks, rewards, "",
                    source.appearance(), source.behavior(), source.extensions());
            var change = DraftBookEditor.copyQuest(result, source.id(), copy);
            if (!change.success()) return change;
            result = change.value().book(); affected.addAll(change.value().affectedObjects());
        }
        return AuthorOperationResult.success("QUEST_SELECTION_COPIED", "Selection copied; external dependencies and private references retained", new DraftChange(result, affected));
    }
    private static ResourceLocation allocate(Set<ResourceLocation> used, ResourceLocation base, String suffix) {
        // Fresh IDs also avoid accidentally reviving historical progress after an older copy was deleted.
        while (true) {
            var id = ResourceLocation.fromNamespaceAndPath(base.getNamespace(), base.getPath() + suffix + "_" + UUID.randomUUID().toString().replace("-", ""));
            if (used.add(id)) return id;
        }
    }
}
