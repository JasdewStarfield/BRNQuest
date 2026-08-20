package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestIconValue;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure presentation rules shared by rendering and deterministic UI tests. */
final class QuestPresentation {
    private QuestPresentation() {}

    static List<NavigationEntry> navigation(QuestBookDefinition book) {
        List<ChapterGroupDefinition> groups = book.chapterGroups().stream()
                .sorted(Comparator.comparingInt(ChapterGroupDefinition::order)
                        .thenComparing(group -> group.id().toString()))
                .toList();
        Map<net.minecraft.resources.ResourceLocation, List<ChapterDefinition>> byGroup = new HashMap<>();
        book.chapters().forEach(chapter -> byGroup.computeIfAbsent(chapter.groupId(), ignored -> new ArrayList<>()).add(chapter));
        byGroup.values().forEach(chapters -> chapters.sort(Comparator.comparingInt(ChapterDefinition::order)
                .thenComparing(chapter -> chapter.id().toString())));

        List<NavigationEntry> result = new ArrayList<>();
        for (ChapterGroupDefinition group : groups) {
            result.add(new NavigationEntry(group, null));
            byGroup.getOrDefault(group.id(), List.of()).forEach(chapter -> result.add(new NavigationEntry(null, chapter)));
            byGroup.remove(group.id());
        }
        // Invalid or intentionally ungrouped native chapters remain reachable instead of disappearing.
        byGroup.values().stream().flatMap(List::stream)
                .sorted(Comparator.comparingInt(ChapterDefinition::order).thenComparing(chapter -> chapter.id().toString()))
                .forEach(chapter -> result.add(new NavigationEntry(null, chapter)));
        return List.copyOf(result);
    }

    static List<ChapterDefinition> orderedChapters(QuestBookDefinition book) {
        return navigation(book).stream().map(NavigationEntry::chapter).filter(java.util.Objects::nonNull).toList();
    }

    static QuestVisual visual(QuestDefinition quest) {
        if (QuestIconValue.isTexture(quest.icon())) return new QuestVisual(VisualKind.TEXTURE, quest.icon());
        if (!quest.icon().isBlank()) return new QuestVisual(VisualKind.ITEM, quest.icon());
        for (TaskDefinition task : quest.tasks()) {
            var view = ApiViews.task(task);
            ClientTaskPresentation presentation = ClientTaskPresentationRegistry.get(task.typeId());
            String itemSnbt = presentation.itemSnbt(view);
            VisualKind kind = switch (presentation.nodeStyle(view)) {
                case ITEM -> VisualKind.ITEM;
                case CHECKMARK -> VisualKind.CHECKMARK;
                case CUSTOM -> VisualKind.CUSTOM;
                case PLACEHOLDER -> VisualKind.PLACEHOLDER;
            };
            if (kind != VisualKind.PLACEHOLDER) return new QuestVisual(kind, itemSnbt);
        }
        return new QuestVisual(VisualKind.PLACEHOLDER, "");
    }

    static int requiredCount(TaskDefinition task) {
        return ClientTaskPresentationRegistry.requiredCount(ApiViews.task(task));
    }

    /** A completion badge is meaningful only while at least one real reward remains unclaimed. */
    static boolean hasPendingReward(QuestDefinition quest, QuestStatus status, Set<String> claimedRewardIds) {
        if (status != QuestStatus.COMPLETED) return false;
        return quest.rewards().stream().anyMatch(reward -> !claimedRewardIds.contains(reward.id().toString()));
    }

    /** Separates plain completion from the stronger "reward waiting" message. */
    static String statusTranslationKey(QuestDefinition quest, QuestStatus status, Set<String> claimedRewardIds) {
        if (status == QuestStatus.COMPLETED) {
            return hasPendingReward(quest, status, claimedRewardIds)
                    ? "screen.brnquest.status.rewards_pending"
                    : "screen.brnquest.status.completed";
        }
        return "screen.brnquest.status." + status.name().toLowerCase(java.util.Locale.ROOT);
    }

    record NavigationEntry(ChapterGroupDefinition group, ChapterDefinition chapter) {}
    record QuestVisual(VisualKind kind, String value) {
        String itemSnbt() { return kind == VisualKind.ITEM ? value : ""; }
    }
    enum VisualKind { ITEM, TEXTURE, CHECKMARK, CUSTOM, PLACEHOLDER }
}
