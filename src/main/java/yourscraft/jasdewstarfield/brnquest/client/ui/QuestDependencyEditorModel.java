package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure dependency-picker model kept independent from rendering for deterministic tests. */
final class QuestDependencyEditorModel {
    record Candidate(ResourceLocation questId, String title, String chapterTitle, boolean createsCycle) {}

    private QuestDependencyEditorModel() {}

    static List<Candidate> candidates(QuestBookDefinition book, ResourceLocation selectedQuestId, String filter) {
        QuestDefinition selected = quest(book, selectedQuestId);
        if (selected == null) return List.of();

        Map<ResourceLocation, String> chapterTitles = new HashMap<>();
        for (ChapterDefinition chapter : book.chapters()) {
            chapterTitles.put(chapter.id(), chapter.title().isBlank() ? chapter.id().toString() : chapter.title());
        }
        String normalizedFilter = filter == null ? "" : filter.strip().toLowerCase(Locale.ROOT);
        Set<ResourceLocation> cycleCandidates = cycleCandidates(book, selectedQuestId);
        List<Candidate> candidates = new ArrayList<>();
        // Preserve chapter and quest list order so the picker matches navigation order.
        for (ChapterDefinition chapter : book.chapters()) {
            for (QuestDefinition candidate : chapter.quests()) {
                if (candidate.id().equals(selectedQuestId) || selected.dependencies().contains(candidate.id())) continue;
                String title = candidate.title().isBlank() ? candidate.id().toString() : candidate.title();
                String chapterTitle = chapterTitles.getOrDefault(candidate.chapterId(), candidate.chapterId().toString());
                if (!matches(normalizedFilter, title, candidate.id().toString(), chapterTitle)) continue;
                candidates.add(new Candidate(candidate.id(), title, chapterTitle,
                        cycleCandidates.contains(candidate.id())));
            }
        }
        return List.copyOf(candidates);
    }

    /** Adding candidate as a prerequisite cycles when it already reaches the selected quest. */
    static boolean wouldCreateCycle(QuestBookDefinition book, ResourceLocation selectedQuestId,
                                    ResourceLocation candidateId) {
        if (selectedQuestId == null || candidateId == null || selectedQuestId.equals(candidateId)) return true;
        ArrayDeque<ResourceLocation> pending = new ArrayDeque<>();
        Set<ResourceLocation> visited = new HashSet<>();
        pending.add(candidateId);
        while (!pending.isEmpty()) {
            ResourceLocation current = pending.removeFirst();
            if (!visited.add(current)) continue;
            if (current.equals(selectedQuestId)) return true;
            QuestDefinition quest = quest(book, current);
            if (quest != null) pending.addAll(quest.dependencies());
        }
        return false;
    }

    /** Computes every cyclic candidate in one reverse-graph walk for smooth picker rendering. */
    private static Set<ResourceLocation> cycleCandidates(QuestBookDefinition book, ResourceLocation selectedQuestId) {
        Map<ResourceLocation, List<ResourceLocation>> dependents = new HashMap<>();
        for (QuestDefinition quest : book.quests()) {
            for (ResourceLocation dependency : quest.dependencies()) {
                dependents.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(quest.id());
            }
        }
        Set<ResourceLocation> result = new HashSet<>();
        ArrayDeque<ResourceLocation> pending = new ArrayDeque<>();
        pending.add(selectedQuestId);
        while (!pending.isEmpty()) {
            ResourceLocation current = pending.removeFirst();
            for (ResourceLocation dependent : dependents.getOrDefault(current, List.of())) {
                if (result.add(dependent)) pending.addLast(dependent);
            }
        }
        return Set.copyOf(result);
    }

    private static QuestDefinition quest(QuestBookDefinition book, ResourceLocation id) {
        if (book == null || id == null) return null;
        return book.quests().stream().filter(quest -> quest.id().equals(id)).findFirst().orElse(null);
    }

    private static boolean matches(String filter, String... values) {
        if (filter.isEmpty()) return true;
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).contains(filter)) return true;
        }
        return false;
    }
}
