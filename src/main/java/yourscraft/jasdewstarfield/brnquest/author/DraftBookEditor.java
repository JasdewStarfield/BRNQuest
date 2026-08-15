package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/** Pure immutable CRUD operations; no method can partially change a live draft. */
public final class DraftBookEditor {
    private DraftBookEditor() {}

    public static AuthorOperationResult<DraftChange> setBookTitle(QuestBookDefinition book, String title) {
        if (title == null || title.isBlank()) return invalid("BOOK_TITLE_REQUIRED", "Book title is required");
        return changed(new QuestBookDefinition(book.id(), book.schemaVersion(), title,
                book.chapterGroups(), book.chapters(), book.legacyIds()), book.id());
    }

    public static AuthorOperationResult<DraftChange> addGroup(QuestBookDefinition book, ChapterGroupDefinition group) {
        if (group == null || !group.bookId().equals(book.id())) return invalid("GROUP_BOOK_MISMATCH", "Group belongs to another book");
        if (book.chapterGroups().stream().anyMatch(value -> value.id().equals(group.id()))) return conflict("DUPLICATE_GROUP_ID", group.id());
        List<ChapterGroupDefinition> groups = append(book.chapterGroups(), group);
        return changed(withGroups(book, groups), group.id());
    }

    public static AuthorOperationResult<DraftChange> copyGroup(QuestBookDefinition book, ResourceLocation sourceId,
                                                                ChapterGroupDefinition copy) {
        if (book.chapterGroups().stream().noneMatch(group -> group.id().equals(sourceId))) return notFound("GROUP_NOT_FOUND", sourceId);
        if (copy == null || copy.id().equals(sourceId)) return invalid("COPY_ID_REQUIRED", "Group copy requires a new stable ID");
        return addGroup(book, copy);
    }

    public static AuthorOperationResult<DraftChange> updateGroup(QuestBookDefinition book, ResourceLocation groupId,
                                                                  ChapterGroupDefinition replacement) {
        if (replacement == null || !replacement.id().equals(groupId)) return invalid("STABLE_ID_REQUIRED", "Group ID cannot change in an update");
        return replaceGroup(book, groupId, ignored -> replacement);
    }

    public static AuthorOperationResult<DraftChange> moveGroup(QuestBookDefinition book, ResourceLocation groupId,
                                                                int targetIndex) {
        List<ChapterGroupDefinition> ordered = book.chapterGroups().stream()
                .sorted(java.util.Comparator.comparingInt(ChapterGroupDefinition::order)
                        .thenComparing(group -> group.id().toString())).toList();
        int source = indexOf(ordered, groupId, ChapterGroupDefinition::id);
        if (source < 0) return notFound("GROUP_NOT_FOUND", groupId);
        List<ChapterGroupDefinition> moved = new ArrayList<>(ordered);
        ChapterGroupDefinition value = moved.remove(source);
        moved.add(Math.max(0, Math.min(targetIndex, moved.size())), value);
        List<ChapterGroupDefinition> normalized = new ArrayList<>();
        for (int index = 0; index < moved.size(); index++) {
            ChapterGroupDefinition group = moved.get(index);
            normalized.add(new ChapterGroupDefinition(group.bookId(), group.id(), group.title(), index));
        }
        return changed(withGroups(book, normalized), groupId);
    }

    public static AuthorOperationResult<DraftChange> removeGroup(QuestBookDefinition book, ResourceLocation groupId) {
        List<ResourceLocation> children = book.chapters().stream().filter(c -> c.groupId().equals(groupId)).map(ChapterDefinition::id).toList();
        if (!children.isEmpty()) return conflict("GROUP_NOT_EMPTY", groupId + " contains " + children);
        return remove(book, book.chapterGroups(), groupId, ChapterGroupDefinition::id, DraftBookEditor::withGroups, "GROUP_NOT_FOUND");
    }

    public static AuthorOperationResult<DraftChange> removeGroupWithContents(QuestBookDefinition book,
                                                                              ResourceLocation groupId) {
        if (book.chapterGroups().stream().noneMatch(group -> group.id().equals(groupId))) return notFound("GROUP_NOT_FOUND", groupId);
        Set<ResourceLocation> chapters = book.chapters().stream().filter(chapter -> chapter.groupId().equals(groupId))
                .map(ChapterDefinition::id).collect(java.util.stream.Collectors.toSet());
        Set<ResourceLocation> quests = book.chapters().stream().filter(chapter -> chapters.contains(chapter.id()))
                .flatMap(chapter -> chapter.quests().stream()).map(QuestDefinition::id)
                .collect(java.util.stream.Collectors.toSet());
        QuestBookDefinition pruned = removeQuestSetAndReferences(book, quests);
        List<ChapterDefinition> remainingChapters = pruned.chapters().stream()
                .filter(chapter -> !chapters.contains(chapter.id())).toList();
        List<ChapterGroupDefinition> remainingGroups = pruned.chapterGroups().stream()
                .filter(group -> !group.id().equals(groupId)).toList();
        List<ResourceLocation> affected = new ArrayList<>();
        affected.add(groupId);
        affected.addAll(chapters);
        affected.addAll(quests);
        affected.addAll(dependencyReferrers(book, quests));
        return changed(new QuestBookDefinition(book.id(), book.schemaVersion(), book.title(), remainingGroups,
                remainingChapters, book.legacyIds()), affected.toArray(ResourceLocation[]::new));
    }

    public static AuthorOperationResult<DraftChange> addChapter(QuestBookDefinition book, ChapterDefinition chapter) {
        if (chapter == null || !chapter.bookId().equals(book.id()) || !chapter.quests().isEmpty()) {
            return invalid("INVALID_NEW_CHAPTER", "New chapter must belong to the book and contain no quests");
        }
        if (book.chapters().stream().anyMatch(value -> value.id().equals(chapter.id()))) return conflict("DUPLICATE_CHAPTER_ID", chapter.id());
        return changed(withChapters(book, append(book.chapters(), chapter)), chapter.id());
    }

    public static AuthorOperationResult<DraftChange> copyChapter(QuestBookDefinition book, ResourceLocation sourceId,
                                                                  ChapterDefinition copy) {
        if (chapter(book, sourceId) == null) return notFound("CHAPTER_NOT_FOUND", sourceId);
        if (copy == null || copy.id().equals(sourceId) || !copy.bookId().equals(book.id())) {
            return invalid("COPY_ID_REQUIRED", "Chapter copy requires a new stable ID in the same book");
        }
        if (book.chapters().stream().anyMatch(chapter -> chapter.id().equals(copy.id()))) return conflict("DUPLICATE_CHAPTER_ID", copy.id());
        if (copy.quests().stream().anyMatch(quest -> !quest.bookId().equals(book.id()) || !quest.chapterId().equals(copy.id()))) {
            return invalid("QUEST_CONTAINER_MISMATCH", "Copied quests must belong to the copied chapter");
        }
        return changed(withChapters(book, append(book.chapters(), copy)), containedIds(copy));
    }

    public static AuthorOperationResult<DraftChange> updateChapter(QuestBookDefinition book, ResourceLocation chapterId,
                                                                    ChapterDefinition replacement) {
        ChapterDefinition old = chapter(book, chapterId);
        if (old == null) return notFound("CHAPTER_NOT_FOUND", chapterId);
        if (replacement == null || !replacement.id().equals(chapterId) || !replacement.bookId().equals(book.id())) {
            return invalid("STABLE_ID_REQUIRED", "Chapter ID and book ownership cannot change in an update");
        }
        // Child quests are owned by their dedicated operations, not an enclosing overwrite.
        ChapterDefinition safe = new ChapterDefinition(book.id(), chapterId, replacement.groupId(), replacement.title(),
                replacement.icon(), replacement.order(), old.quests());
        return replaceChapter(book, chapterId, ignored -> safe, chapterId);
    }

    public static AuthorOperationResult<DraftChange> moveChapterOrder(QuestBookDefinition book,
                                                                       ResourceLocation chapterId,
                                                                       int targetIndex) {
        ChapterDefinition source = chapter(book, chapterId);
        if (source == null) return notFound("CHAPTER_NOT_FOUND", chapterId);
        List<ChapterDefinition> siblings = book.chapters().stream()
                .filter(chapter -> chapter.groupId().equals(source.groupId()))
                .sorted(java.util.Comparator.comparingInt(ChapterDefinition::order)
                        .thenComparing(chapter -> chapter.id().toString())).toList();
        int sourceIndex = indexOf(siblings, chapterId, ChapterDefinition::id);
        List<ChapterDefinition> moved = new ArrayList<>(siblings);
        ChapterDefinition value = moved.remove(sourceIndex);
        moved.add(Math.max(0, Math.min(targetIndex, moved.size())), value);
        Map<ResourceLocation, Integer> orders = new java.util.HashMap<>();
        for (int index = 0; index < moved.size(); index++) orders.put(moved.get(index).id(), index);
        List<ChapterDefinition> normalized = book.chapters().stream().map(chapter -> {
            Integer order = orders.get(chapter.id());
            return order == null ? chapter : new ChapterDefinition(chapter.bookId(), chapter.id(), chapter.groupId(),
                    chapter.title(), chapter.icon(), order, chapter.quests());
        }).toList();
        return changed(withChapters(book, normalized), chapterId);
    }

    public static AuthorOperationResult<DraftChange> removeChapter(QuestBookDefinition book, ResourceLocation chapterId) {
        ChapterDefinition old = chapter(book, chapterId);
        if (old == null) return notFound("CHAPTER_NOT_FOUND", chapterId);
        if (!old.quests().isEmpty()) return conflict("CHAPTER_NOT_EMPTY", chapterId + " contains quests");
        return remove(book, book.chapters(), chapterId, ChapterDefinition::id, DraftBookEditor::withChapters, "CHAPTER_NOT_FOUND");
    }

    public static AuthorOperationResult<DraftChange> removeChapterWithContents(QuestBookDefinition book,
                                                                                ResourceLocation chapterId) {
        ChapterDefinition chapter = chapter(book, chapterId);
        if (chapter == null) return notFound("CHAPTER_NOT_FOUND", chapterId);
        Set<ResourceLocation> quests = chapter.quests().stream().map(QuestDefinition::id)
                .collect(java.util.stream.Collectors.toSet());
        QuestBookDefinition pruned = removeQuestSetAndReferences(book, quests);
        List<ChapterDefinition> chapters = pruned.chapters().stream().filter(value -> !value.id().equals(chapterId)).toList();
        List<ResourceLocation> affected = new ArrayList<>();
        affected.add(chapterId);
        affected.addAll(quests);
        affected.addAll(dependencyReferrers(book, quests));
        return changed(withChapters(pruned, chapters), affected.toArray(ResourceLocation[]::new));
    }

    public static AuthorOperationResult<DraftChange> addQuest(QuestBookDefinition book, ResourceLocation chapterId,
                                                               QuestDefinition quest) {
        if (quest == null || !quest.bookId().equals(book.id()) || !quest.chapterId().equals(chapterId)) {
            return invalid("QUEST_CONTAINER_MISMATCH", "Quest ownership does not match its chapter");
        }
        if (quest(book, quest.id()) != null) return conflict("DUPLICATE_QUEST_ID", quest.id());
        return replaceChapter(book, chapterId, chapter -> new ChapterDefinition(chapter.bookId(), chapter.id(),
                chapter.groupId(), chapter.title(), chapter.icon(), chapter.order(), append(chapter.quests(), quest)),
                questObjectIds(quest));
    }

    public static AuthorOperationResult<DraftChange> copyQuest(QuestBookDefinition book, ResourceLocation sourceId,
                                                                QuestDefinition copy) {
        if (quest(book, sourceId) == null) return notFound("QUEST_NOT_FOUND", sourceId);
        if (copy == null || copy.id().equals(sourceId)) return invalid("COPY_ID_REQUIRED", "Quest copy requires a new stable ID");
        return addQuest(book, copy.chapterId(), copy);
    }

    public static AuthorOperationResult<DraftChange> updateQuest(QuestBookDefinition book, ResourceLocation questId,
                                                                  QuestDefinition replacement) {
        QuestLocation location = questLocation(book, questId);
        if (location == null) return notFound("QUEST_NOT_FOUND", questId);
        if (replacement == null || !replacement.id().equals(questId)
                || !replacement.bookId().equals(book.id()) || !replacement.chapterId().equals(location.chapter.id())) {
            return invalid("STABLE_ID_REQUIRED", "Quest ID and container cannot change in an update");
        }
        // Basic properties may change here; dependency/task/reward collections have
        // their own stable-ID operations and cannot be overwritten accidentally.
        QuestDefinition safe = new QuestDefinition(book.id(), questId, location.chapter.id(), replacement.title(),
                replacement.subtitle(), replacement.description(), replacement.icon(), replacement.x(), replacement.y(),
                location.chapter.quests().get(location.index).dependencies(),
                location.chapter.quests().get(location.index).tasks(),
                location.chapter.quests().get(location.index).rewards(), replacement.legacyId());
        return replaceQuest(book, location, ignored -> safe, questId);
    }

    /** Commits a multi-selection drag as one revision instead of one stale-prone request per node. */
    public static AuthorOperationResult<DraftChange> updateQuestPositions(QuestBookDefinition book,
                                                                           Map<ResourceLocation, Position> positions) {
        if (positions == null || positions.isEmpty()) return invalid("POSITIONS_REQUIRED", "At least one position is required");
        if (positions.values().stream().anyMatch(position -> position == null
                || !Double.isFinite(position.x()) || !Double.isFinite(position.y()))) {
            return invalid("INVALID_QUEST_POSITION", "Quest coordinates must be finite");
        }
        if (positions.keySet().stream().anyMatch(id -> quest(book, id) == null)) {
            return invalid("QUEST_NOT_FOUND", "Every moved quest must exist in the current draft");
        }
        List<ChapterDefinition> chapters = book.chapters().stream().map(chapter -> new ChapterDefinition(
                chapter.bookId(), chapter.id(), chapter.groupId(), chapter.title(), chapter.icon(), chapter.order(),
                chapter.quests().stream().map(quest -> {
                    Position position = positions.get(quest.id());
                    return position == null ? quest : new QuestDefinition(quest.bookId(), quest.id(), quest.chapterId(),
                            quest.title(), quest.subtitle(), quest.description(), quest.icon(), position.x(), position.y(),
                            quest.dependencies(), quest.tasks(), quest.rewards(), quest.legacyId());
                }).toList())).toList();
        return changed(withChapters(book, chapters), positions.keySet().toArray(ResourceLocation[]::new));
    }

    public record Position(double x, double y) {}

    public static AuthorOperationResult<DraftChange> moveQuest(QuestBookDefinition book, ResourceLocation questId,
                                                                ResourceLocation targetChapterId, int targetIndex) {
        QuestLocation source = questLocation(book, questId);
        ChapterDefinition target = chapter(book, targetChapterId);
        if (source == null) return notFound("QUEST_NOT_FOUND", questId);
        if (target == null) return notFound("CHAPTER_NOT_FOUND", targetChapterId);
        List<ChapterDefinition> chapters = new ArrayList<>(book.chapters());
        List<QuestDefinition> sourceQuests = new ArrayList<>(source.chapter.quests());
        QuestDefinition moved = sourceQuests.remove(source.index);
        chapters.set(source.chapterIndex, withQuests(source.chapter, sourceQuests));
        int targetChapterIndex = indexOf(chapters, targetChapterId, ChapterDefinition::id);
        List<QuestDefinition> targetQuests = source.chapter.id().equals(targetChapterId)
                ? new ArrayList<>(sourceQuests) : new ArrayList<>(chapters.get(targetChapterIndex).quests());
        int insertion = Math.max(0, Math.min(targetIndex, targetQuests.size()));
        QuestDefinition relocated = new QuestDefinition(book.id(), moved.id(), targetChapterId, moved.title(), moved.subtitle(),
                moved.description(), moved.icon(), moved.x(), moved.y(), moved.dependencies(), moved.tasks(), moved.rewards(), moved.legacyId());
        targetQuests.add(insertion, relocated);
        chapters.set(targetChapterIndex, withQuests(chapters.get(targetChapterIndex), targetQuests));
        return changed(withChapters(book, chapters), questId, source.chapter.id(), targetChapterId);
    }

    public static AuthorOperationResult<DraftChange> removeQuest(QuestBookDefinition book, ResourceLocation questId) {
        QuestLocation location = questLocation(book, questId);
        if (location == null) return notFound("QUEST_NOT_FOUND", questId);
        List<ResourceLocation> referrers = book.quests().stream().filter(q -> q.dependencies().contains(questId)).map(QuestDefinition::id).toList();
        if (!referrers.isEmpty()) return conflict("QUEST_IS_DEPENDENCY", questId + " is referenced by " + referrers);
        List<QuestDefinition> quests = new ArrayList<>(location.chapter.quests());
        quests.remove(location.index);
        return replaceChapter(book, location.chapter.id(), chapter -> withQuests(chapter, quests), questId);
    }

    public static AuthorOperationResult<DraftChange> removeQuestAndReferences(QuestBookDefinition book,
                                                                               ResourceLocation questId) {
        if (quest(book, questId) == null) return notFound("QUEST_NOT_FOUND", questId);
        List<ResourceLocation> referrers = book.quests().stream().filter(q -> q.dependencies().contains(questId))
                .map(QuestDefinition::id).toList();
        QuestBookDefinition changed = removeQuestSetAndReferences(book, Set.of(questId));
        List<ResourceLocation> affected = new ArrayList<>();
        affected.add(questId);
        affected.addAll(referrers);
        return changed(changed, affected.toArray(ResourceLocation[]::new));
    }

    public static AuthorOperationResult<DraftChange> addDependency(QuestBookDefinition book, ResourceLocation questId,
                                                                    ResourceLocation dependencyId) {
        if (quest(book, dependencyId) == null) return notFound("DEPENDENCY_NOT_FOUND", dependencyId);
        return editQuest(book, questId, quest -> {
            if (quest.dependencies().contains(dependencyId)) return quest;
            return copyQuest(quest, append(quest.dependencies(), dependencyId), quest.tasks(), quest.rewards());
        }, dependencyId);
    }

    public static AuthorOperationResult<DraftChange> removeDependency(QuestBookDefinition book, ResourceLocation questId,
                                                                       ResourceLocation dependencyId) {
        return editQuest(book, questId, quest -> copyQuest(quest,
                quest.dependencies().stream().filter(id -> !id.equals(dependencyId)).toList(), quest.tasks(), quest.rewards()), dependencyId);
    }

    public static AuthorOperationResult<DraftChange> addTask(QuestBookDefinition book, ResourceLocation questId, TaskDefinition task) {
        if (task == null || !task.bookId().equals(book.id())) return invalid("TASK_BOOK_MISMATCH", "Task belongs to another book");
        if (typedIdExists(book, task.id())) return conflict("DUPLICATE_TYPED_ID", task.id());
        return editQuest(book, questId, quest -> copyQuest(quest, quest.dependencies(), append(quest.tasks(), task), quest.rewards()), task.id());
    }

    public static AuthorOperationResult<DraftChange> copyTask(QuestBookDefinition book, ResourceLocation questId,
                                                               ResourceLocation sourceId, TaskDefinition copy) {
        QuestDefinition quest = quest(book, questId);
        if (quest == null) return notFound("QUEST_NOT_FOUND", questId);
        if (quest.tasks().stream().noneMatch(task -> task.id().equals(sourceId))) return notFound("TASK_NOT_FOUND", sourceId);
        if (copy == null || copy.id().equals(sourceId)) return invalid("COPY_ID_REQUIRED", "Task copy requires a new stable ID");
        return addTask(book, questId, copy);
    }

    public static AuthorOperationResult<DraftChange> updateTask(QuestBookDefinition book, ResourceLocation questId,
                                                                 ResourceLocation taskId, TaskDefinition replacement) {
        if (replacement == null || !replacement.id().equals(taskId) || !replacement.bookId().equals(book.id())) return invalid("STABLE_ID_REQUIRED", "Task ID cannot change");
        return editTyped(book, questId, taskId, replacement, true);
    }

    public static AuthorOperationResult<DraftChange> removeTask(QuestBookDefinition book, ResourceLocation questId, ResourceLocation taskId) {
        return editTyped(book, questId, taskId, null, true);
    }

    public static AuthorOperationResult<DraftChange> moveTask(QuestBookDefinition book, ResourceLocation questId,
                                                               ResourceLocation taskId, int targetIndex) {
        return moveTyped(book, questId, taskId, targetIndex, true);
    }

    public static AuthorOperationResult<DraftChange> addReward(QuestBookDefinition book, ResourceLocation questId, RewardDefinition reward) {
        if (reward == null || !reward.bookId().equals(book.id())) return invalid("REWARD_BOOK_MISMATCH", "Reward belongs to another book");
        if (typedIdExists(book, reward.id())) return conflict("DUPLICATE_TYPED_ID", reward.id());
        return editQuest(book, questId, quest -> copyQuest(quest, quest.dependencies(), quest.tasks(), append(quest.rewards(), reward)), reward.id());
    }

    public static AuthorOperationResult<DraftChange> copyReward(QuestBookDefinition book, ResourceLocation questId,
                                                                 ResourceLocation sourceId, RewardDefinition copy) {
        QuestDefinition quest = quest(book, questId);
        if (quest == null) return notFound("QUEST_NOT_FOUND", questId);
        if (quest.rewards().stream().noneMatch(reward -> reward.id().equals(sourceId))) return notFound("REWARD_NOT_FOUND", sourceId);
        if (copy == null || copy.id().equals(sourceId)) return invalid("COPY_ID_REQUIRED", "Reward copy requires a new stable ID");
        return addReward(book, questId, copy);
    }

    public static AuthorOperationResult<DraftChange> updateReward(QuestBookDefinition book, ResourceLocation questId,
                                                                   ResourceLocation rewardId, RewardDefinition replacement) {
        if (replacement == null || !replacement.id().equals(rewardId) || !replacement.bookId().equals(book.id())) return invalid("STABLE_ID_REQUIRED", "Reward ID cannot change");
        return editTyped(book, questId, rewardId, replacement, false);
    }

    public static AuthorOperationResult<DraftChange> removeReward(QuestBookDefinition book, ResourceLocation questId, ResourceLocation rewardId) {
        return editTyped(book, questId, rewardId, null, false);
    }

    public static AuthorOperationResult<DraftChange> moveReward(QuestBookDefinition book, ResourceLocation questId,
                                                                 ResourceLocation rewardId, int targetIndex) {
        return moveTyped(book, questId, rewardId, targetIndex, false);
    }

    private static AuthorOperationResult<DraftChange> editTyped(QuestBookDefinition book, ResourceLocation questId,
                                                                 ResourceLocation typedId, Object replacement, boolean task) {
        QuestDefinition quest = quest(book, questId);
        if (quest == null) return notFound("QUEST_NOT_FOUND", questId);
        if (task) {
            int index = indexOf(quest.tasks(), typedId, TaskDefinition::id);
            if (index < 0) return notFound("TASK_NOT_FOUND", typedId);
            List<TaskDefinition> values = new ArrayList<>(quest.tasks());
            if (replacement == null) values.remove(index); else values.set(index, (TaskDefinition) replacement);
            return editQuest(book, questId, value -> copyQuest(value, value.dependencies(), values, value.rewards()), typedId);
        }
        int index = indexOf(quest.rewards(), typedId, RewardDefinition::id);
        if (index < 0) return notFound("REWARD_NOT_FOUND", typedId);
        List<RewardDefinition> values = new ArrayList<>(quest.rewards());
        if (replacement == null) values.remove(index); else values.set(index, (RewardDefinition) replacement);
        return editQuest(book, questId, value -> copyQuest(value, value.dependencies(), value.tasks(), values), typedId);
    }

    private static AuthorOperationResult<DraftChange> moveTyped(QuestBookDefinition book, ResourceLocation questId,
                                                                 ResourceLocation typedId, int targetIndex, boolean task) {
        QuestDefinition quest = quest(book, questId);
        if (quest == null) return notFound("QUEST_NOT_FOUND", questId);
        if (task) {
            List<TaskDefinition> values = move(quest.tasks(), typedId, targetIndex, TaskDefinition::id);
            if (values == null) return notFound("TASK_NOT_FOUND", typedId);
            return editQuest(book, questId, value -> copyQuest(value, value.dependencies(), values, value.rewards()), typedId);
        }
        List<RewardDefinition> values = move(quest.rewards(), typedId, targetIndex, RewardDefinition::id);
        if (values == null) return notFound("REWARD_NOT_FOUND", typedId);
        return editQuest(book, questId, value -> copyQuest(value, value.dependencies(), value.tasks(), values), typedId);
    }

    private static AuthorOperationResult<DraftChange> editQuest(QuestBookDefinition book, ResourceLocation questId,
                                                                 UnaryOperator<QuestDefinition> operation,
                                                                 ResourceLocation... affected) {
        QuestLocation location = questLocation(book, questId);
        if (location == null) return notFound("QUEST_NOT_FOUND", questId);
        return replaceQuest(book, location, operation, concat(questId, affected));
    }

    private static AuthorOperationResult<DraftChange> replaceQuest(QuestBookDefinition book, QuestLocation location,
                                                                    UnaryOperator<QuestDefinition> operation,
                                                                    ResourceLocation... affected) {
        List<QuestDefinition> quests = new ArrayList<>(location.chapter.quests());
        quests.set(location.index, operation.apply(quests.get(location.index)));
        return replaceChapter(book, location.chapter.id(), chapter -> withQuests(chapter, quests), affected);
    }

    private static AuthorOperationResult<DraftChange> replaceGroup(QuestBookDefinition book, ResourceLocation id,
                                                                    UnaryOperator<ChapterGroupDefinition> operation) {
        int index = indexOf(book.chapterGroups(), id, ChapterGroupDefinition::id);
        if (index < 0) return notFound("GROUP_NOT_FOUND", id);
        List<ChapterGroupDefinition> values = new ArrayList<>(book.chapterGroups());
        values.set(index, operation.apply(values.get(index)));
        return changed(withGroups(book, values), id);
    }

    private static AuthorOperationResult<DraftChange> replaceChapter(QuestBookDefinition book, ResourceLocation id,
                                                                      UnaryOperator<ChapterDefinition> operation,
                                                                      ResourceLocation... affected) {
        int index = indexOf(book.chapters(), id, ChapterDefinition::id);
        if (index < 0) return notFound("CHAPTER_NOT_FOUND", id);
        List<ChapterDefinition> values = new ArrayList<>(book.chapters());
        values.set(index, operation.apply(values.get(index)));
        return changed(withChapters(book, values), concat(id, affected));
    }

    private static <T> AuthorOperationResult<DraftChange> remove(QuestBookDefinition book, List<T> source,
                                                                  ResourceLocation id,
                                                                  java.util.function.Function<T, ResourceLocation> idGetter,
                                                                  java.util.function.BiFunction<QuestBookDefinition, List<T>, QuestBookDefinition> replacer,
                                                                  String notFoundCode) {
        int index = indexOf(source, id, idGetter);
        if (index < 0) return notFound(notFoundCode, id);
        List<T> values = new ArrayList<>(source);
        values.remove(index);
        return changed(replacer.apply(book, values), id);
    }

    private static QuestBookDefinition withGroups(QuestBookDefinition book, List<ChapterGroupDefinition> groups) {
        return new QuestBookDefinition(book.id(), book.schemaVersion(), book.title(), groups, book.chapters(), book.legacyIds());
    }

    private static QuestBookDefinition withChapters(QuestBookDefinition book, List<ChapterDefinition> chapters) {
        return new QuestBookDefinition(book.id(), book.schemaVersion(), book.title(), book.chapterGroups(), chapters, book.legacyIds());
    }

    private static ChapterDefinition withQuests(ChapterDefinition chapter, List<QuestDefinition> quests) {
        return new ChapterDefinition(chapter.bookId(), chapter.id(), chapter.groupId(), chapter.title(), chapter.icon(), chapter.order(), quests);
    }

    private static QuestDefinition copyQuest(QuestDefinition quest, List<ResourceLocation> dependencies,
                                             List<TaskDefinition> tasks, List<RewardDefinition> rewards) {
        return new QuestDefinition(quest.bookId(), quest.id(), quest.chapterId(), quest.title(), quest.subtitle(),
                quest.description(), quest.icon(), quest.x(), quest.y(), dependencies, tasks, rewards, quest.legacyId());
    }

    private static QuestBookDefinition removeQuestSetAndReferences(QuestBookDefinition book,
                                                                    Set<ResourceLocation> removedQuestIds) {
        List<ChapterDefinition> chapters = book.chapters().stream().map(chapter -> {
            List<QuestDefinition> quests = chapter.quests().stream()
                    .filter(quest -> !removedQuestIds.contains(quest.id()))
                    .map(quest -> copyQuest(quest, quest.dependencies().stream()
                            .filter(id -> !removedQuestIds.contains(id)).toList(), quest.tasks(), quest.rewards()))
                    .toList();
            return withQuests(chapter, quests);
        }).toList();
        Map<String, ResourceLocation> aliases = new java.util.TreeMap<>(book.legacyIds());
        aliases.entrySet().removeIf(entry -> removedQuestIds.contains(entry.getValue()));
        return new QuestBookDefinition(book.id(), book.schemaVersion(), book.title(), book.chapterGroups(), chapters, aliases);
    }

    private static List<ResourceLocation> dependencyReferrers(QuestBookDefinition book, Set<ResourceLocation> removedIds) {
        return book.quests().stream().filter(quest -> !removedIds.contains(quest.id())
                && quest.dependencies().stream().anyMatch(removedIds::contains)).map(QuestDefinition::id).toList();
    }

    private static ResourceLocation[] containedIds(ChapterDefinition chapter) {
        List<ResourceLocation> ids = new ArrayList<>();
        ids.add(chapter.id());
        chapter.quests().forEach(quest -> ids.addAll(List.of(questObjectIds(quest))));
        return ids.toArray(ResourceLocation[]::new);
    }

    private static ResourceLocation[] questObjectIds(QuestDefinition quest) {
        List<ResourceLocation> ids = new ArrayList<>();
        ids.add(quest.id());
        quest.tasks().forEach(task -> ids.add(task.id()));
        quest.rewards().forEach(reward -> ids.add(reward.id()));
        return ids.toArray(ResourceLocation[]::new);
    }

    private static ChapterDefinition chapter(QuestBookDefinition book, ResourceLocation id) {
        return book.chapters().stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
    }

    private static QuestDefinition quest(QuestBookDefinition book, ResourceLocation id) {
        return book.quests().stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
    }

    private static QuestLocation questLocation(QuestBookDefinition book, ResourceLocation id) {
        for (int chapterIndex = 0; chapterIndex < book.chapters().size(); chapterIndex++) {
            ChapterDefinition chapter = book.chapters().get(chapterIndex);
            for (int questIndex = 0; questIndex < chapter.quests().size(); questIndex++) {
                if (chapter.quests().get(questIndex).id().equals(id)) return new QuestLocation(chapterIndex, chapter, questIndex);
            }
        }
        return null;
    }

    private static boolean typedIdExists(QuestBookDefinition book, ResourceLocation id) {
        return book.quests().stream().anyMatch(quest -> quest.tasks().stream().anyMatch(task -> task.id().equals(id))
                || quest.rewards().stream().anyMatch(reward -> reward.id().equals(id)));
    }

    private static <T> List<T> append(List<T> source, T value) {
        List<T> result = new ArrayList<>(source);
        result.add(value);
        return result;
    }

    private static <T> List<T> move(List<T> source, ResourceLocation id, int targetIndex,
                                    java.util.function.Function<T, ResourceLocation> idGetter) {
        int index = indexOf(source, id, idGetter);
        if (index < 0) return null;
        List<T> result = new ArrayList<>(source);
        T value = result.remove(index);
        result.add(Math.max(0, Math.min(targetIndex, result.size())), value);
        return result;
    }

    private static <T> int indexOf(List<T> source, ResourceLocation id,
                                   java.util.function.Function<T, ResourceLocation> idGetter) {
        for (int i = 0; i < source.size(); i++) if (idGetter.apply(source.get(i)).equals(id)) return i;
        return -1;
    }

    private static ResourceLocation[] concat(ResourceLocation first, ResourceLocation... rest) {
        ResourceLocation[] result = new ResourceLocation[rest.length + 1];
        result[0] = first;
        System.arraycopy(rest, 0, result, 1, rest.length);
        return result;
    }

    private static AuthorOperationResult<DraftChange> changed(QuestBookDefinition book, ResourceLocation... affected) {
        return AuthorOperationResult.success("DRAFT_CHANGED", "Draft candidate changed", new DraftChange(book, List.of(affected)));
    }

    private static AuthorOperationResult<DraftChange> invalid(String code, String message) {
        return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST, code, message);
    }

    private static AuthorOperationResult<DraftChange> conflict(String code, Object detail) {
        return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, code, detail.toString());
    }

    private static AuthorOperationResult<DraftChange> notFound(String code, ResourceLocation id) {
        return AuthorOperationResult.failure(AuthorOperationResult.Status.NOT_FOUND, code, id.toString());
    }

    private record QuestLocation(int chapterIndex, ChapterDefinition chapter, int index) {}
}
