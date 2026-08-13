package yourscraft.jasdewstarfield.brnquest.api;

import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;

/** Keeps public projections in one package-private mapper so definitions never leak by accident. */
@ApiStatus(ApiStability.INTERNAL)
final class ApiViews {
    private ApiViews() {}

    static QuestBookView book(QuestBookSnapshot snapshot) {
        var book = snapshot.book();
        return new QuestBookView(book.id(), book.schemaVersion(), book.title(), snapshot.revision(),
                book.chapterGroups().stream().map(ChapterGroupDefinition::id).toList(),
                book.chapters().stream().map(ChapterDefinition::id).toList(),
                book.quests().stream().map(QuestDefinition::id).toList(), book.legacyIds());
    }

    static ChapterGroupView chapterGroup(QuestBookSnapshot snapshot, ChapterGroupDefinition group) {
        return new ChapterGroupView(group.bookId(), group.id(), group.title(), group.order(),
                snapshot.book().chapters().stream().filter(chapter -> chapter.groupId().equals(group.id()))
                        .map(ChapterDefinition::id).toList());
    }

    static ChapterView chapter(ChapterDefinition chapter) {
        return new ChapterView(chapter.bookId(), chapter.id(), chapter.groupId(), chapter.title(), chapter.icon(),
                chapter.order(), chapter.quests().stream().map(QuestDefinition::id).toList());
    }

    static QuestView quest(QuestDefinition quest) {
        return new QuestView(quest.bookId(), quest.id(), quest.chapterId(), quest.title(), quest.subtitle(),
                quest.description(), quest.icon(), quest.x(), quest.y(), quest.dependencies(),
                quest.tasks().stream().map(ApiViews::task).toList(),
                quest.rewards().stream().map(ApiViews::reward).toList(), quest.legacyId());
    }

    static TaskView task(TaskDefinition task) {
        return new TaskView(task.bookId(), task.id(), task.typeId(), task.config(), task.optional());
    }

    static RewardView reward(RewardDefinition reward) {
        return new RewardView(reward.bookId(), reward.id(), reward.typeId(), reward.config(),
                reward.claimPolicy(), reward.teamReward());
    }
}
