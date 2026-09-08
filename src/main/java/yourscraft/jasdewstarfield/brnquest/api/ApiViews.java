package yourscraft.jasdewstarfield.brnquest.api;

import yourscraft.jasdewstarfield.brnquest.data.BookText;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;

/** Keeps public projections in one package-private mapper so definitions never leak by accident. */
@ApiStatus(ApiStability.INTERNAL)
public final class ApiViews {
    private ApiViews() {}

    public static QuestBookView book(QuestBookSnapshot snapshot) {
        var book = snapshot.book();
        return new QuestBookView(book.id(), book.schemaVersion(), book.title(), snapshot.revision(),
                book.chapterGroups().stream().map(ChapterGroupDefinition::id).toList(),
                book.chapters().stream().map(ChapterDefinition::id).toList(),
                book.quests().stream().map(QuestDefinition::id).toList(), book.legacyIds());
    }

    public static ChapterGroupView chapterGroup(QuestBookSnapshot snapshot, ChapterGroupDefinition group) {
        return new ChapterGroupView(group.bookId(), group.id(), group.title(), group.order(),
                snapshot.book().chapters().stream().filter(chapter -> chapter.groupId().equals(group.id()))
                        .map(ChapterDefinition::id).toList());
    }

    public static ChapterView chapter(ChapterDefinition chapter) {
        return new ChapterView(chapter.bookId(), chapter.id(), chapter.groupId(), chapter.title(), chapter.icon(),
                chapter.order(), chapter.quests().stream().map(QuestDefinition::id).toList());
    }

    /** Localized projections keep identifiers and source storage unchanged. */
    public static QuestBookView book(QuestBookSnapshot snapshot, String locale) {
        var source = book(snapshot);
        return new QuestBookView(source.id(), source.schemaVersion(),
                BookText.title(snapshot.book(), locale), source.revision(),
                source.chapterGroupIds(), source.chapterIds(), source.questIds(), source.legacyIds());
    }

    public static ChapterGroupView chapterGroup(QuestBookSnapshot snapshot, ChapterGroupDefinition group, String locale) {
        var source = chapterGroup(snapshot, group);
        return new ChapterGroupView(group.bookId(), group.id(),
                BookText.structureTitle(snapshot.book(), "chapter_group",
                        group.id(), locale, group.title()), group.order(), source.chapterIds());
    }

    public static ChapterView chapter(QuestBookSnapshot snapshot, ChapterDefinition chapter, String locale) {
        return new ChapterView(chapter.bookId(), chapter.id(), chapter.groupId(),
                BookText.structureTitle(snapshot.book(), "chapter",
                        chapter.id(), locale, chapter.title()), chapter.icon(), chapter.order(),
                chapter.quests().stream().map(QuestDefinition::id).toList());
    }

    public static QuestView quest(QuestDefinition quest) {
        return new QuestView(quest.bookId(), quest.id(), quest.chapterId(), quest.title(), quest.subtitle(),
                quest.description(), quest.icon(), quest.x(), quest.y(), quest.dependencies(),
                quest.tasks().stream().map(ApiViews::task).toList(),
                quest.rewards().stream().map(ApiViews::reward).toList(), quest.legacyId(), behavior(quest));
    }

    public static QuestView quest(QuestBookSnapshot snapshot, QuestDefinition quest, String locale) {
        String prefix = BookText.questPrefix(quest);
        var localization = snapshot.book().localization();
        return new QuestView(quest.bookId(), quest.id(), quest.chapterId(),
                localization.resolve(locale, prefix + "title", quest.title()),
                localization.resolve(locale, prefix + "quest_subtitle", quest.subtitle()),
                localization.resolve(locale, prefix + "quest_desc", quest.description()),
                quest.icon(), quest.x(), quest.y(), quest.dependencies(),
                quest.tasks().stream().map(ApiViews::task).toList(),
                quest.rewards().stream().map(ApiViews::reward).toList(), quest.legacyId(), behavior(quest));
    }

    private static QuestBehaviorView behavior(QuestDefinition quest) {
        var value = quest.behavior();
        return new QuestBehaviorView(value.hideUntilDependenciesVisible(), value.hideUntilDependenciesComplete(),
                value.invisibleUntilComplete(), value.visibleAfterTasks(), value.hideDetailsUntilStartable(),
                value.hideTextUntilComplete(), value.hideLockIcon(), value.dependencyRequirement().serializedName(),
                value.minimumRequiredDependencies(), value.sequentialTasks(), value.repeatable(),
                value.repeatCooldownSeconds(), value.ignoreRewardBlocking());
    }

    public static TaskView task(TaskDefinition task) {
        return new TaskView(task.bookId(), task.id(), task.typeId(), task.config(), task.optional());
    }

    public static RewardView reward(RewardDefinition reward) {
        return new RewardView(reward.bookId(), reward.id(), reward.typeId(), reward.config(),
                reward.claimPolicy(), reward.teamReward());
    }
}
