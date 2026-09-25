package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestIconValue;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypes;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class QuestPresentationTest {
    @Test void authoredQuestTitleIsNotReplacedByItsNamedObjective() {
        assertEquals("brnquest:authored_quest", QuestPresentation.questTitle(
                "brnquest:authored_quest", () -> "Named objective"));
    }

    @Test void blankQuestTitleMayUseItsObjectiveAsAFallback() {
        assertEquals("Named objective", QuestPresentation.questTitle("", () -> "Named objective"));
    }

    @Test void navigationUsesGroupAndChapterOrderInsteadOfIds() {
        ResourceLocation bookId = id("book");
        var laterGroup = new ChapterGroupDefinition(bookId, id("a_group"), "Later", 1);
        var firstGroup = new ChapterGroupDefinition(bookId, id("z_group"), "First", 0);
        var secondChapter = chapter(bookId, id("a_chapter"), firstGroup.id(), 2);
        var firstChapter = chapter(bookId, id("z_chapter"), firstGroup.id(), 1);
        var laterChapter = chapter(bookId, id("later"), laterGroup.id(), 0);
        var book = new QuestBookDefinition(bookId, 1, "Book", List.of(laterGroup, firstGroup),
                List.of(secondChapter, laterChapter, firstChapter), Map.of());

        assertEquals(List.of(firstChapter.id(), secondChapter.id(), laterChapter.id()),
                QuestPresentation.orderedChapters(book).stream().map(ChapterDefinition::id).toList());
    }

    @Test void defaultVisualUsesFirstTaskAndNeverAReward() {
        ResourceLocation bookId = id("book");
        ResourceLocation chapterId = id("chapter");
        var item = new TaskDefinition(bookId, id("task"), TaskTypes.ITEM,
                Map.of("item", "{count:1,id:\"minecraft:rope\"}"), false);
        var quest = new QuestDefinition(bookId, id("quest"), chapterId, "Quest", "", "", "",
                0, 0, List.of(), List.of(item), List.of(), "LEGACY");

        assertEquals(QuestPresentation.VisualKind.ITEM, QuestPresentation.visual(quest).kind());
        assertEquals(item.config().get("item"), QuestPresentation.visual(quest).itemSnbt());
        assertSame(ClientTaskPresentationRegistry.typeIcon(TaskTypes.ITEM),
                QuestPresentation.visual(quest).icon().orElseThrow());
        assertEquals(true, QuestPresentation.visual(quest).defaultTypeIcon());
    }

    @Test void samePathFromForeignNamespaceUsesPlaceholderPresentation() {
        ResourceLocation bookId = id("book");
        ResourceLocation chapterId = id("chapter");
        var foreignItem = new TaskDefinition(bookId, id("task"), id("item"),
                Map.of("item", "{count:1,id:\"minecraft:stone\"}"), false);
        var quest = new QuestDefinition(bookId, id("quest"), chapterId, "Quest", "", "", "",
                0, 0, List.of(), List.of(foreignItem), List.of(), "LEGACY");

        assertEquals(QuestPresentation.VisualKind.PLACEHOLDER, QuestPresentation.visual(quest).kind());
        assertSame(ClientTypeIconFallback.icon(), QuestPresentation.visual(quest).icon().orElseThrow());
        assertEquals(true, QuestPresentation.visual(quest).defaultTypeIcon());
    }

    @Test void explicitTextureIconBypassesItemSnbtPresentation() {
        ResourceLocation texture = ResourceLocation.parse("brnquest_test:textures/gui/custom_icon.png");
        var quest = new QuestDefinition(id("book"), id("quest"), id("chapter"), "Quest", "", "",
                QuestIconValue.texture(texture), 0, 0, List.of(), List.of(), List.of(), "");

        QuestPresentation.QuestVisual visual = QuestPresentation.visual(quest);

        assertEquals(QuestPresentation.VisualKind.TEXTURE, visual.kind());
        assertEquals(texture, QuestIconValue.textureId(visual.value()).orElseThrow());
        assertEquals("", visual.itemSnbt());
        assertEquals(false, visual.defaultTypeIcon());
    }

    @Test void completedQuestWithoutRewardsUsesPlainCompletionAndNoBadge() {
        QuestDefinition quest = questWithRewards(List.of());

        assertEquals(false, QuestPresentation.hasPendingReward(quest, QuestStatus.COMPLETED, Set.of()));
        assertEquals("screen.brnquest.status.completed",
                QuestPresentation.statusTranslationKey(quest, QuestStatus.COMPLETED, Set.of()));
    }

    @Test void completedQuestWithUnclaimedRewardUsesPendingMessageAndBadge() {
        RewardDefinition reward = new RewardDefinition(id("book"), id("reward"), RewardTypes.ITEM,
                Map.of("item", "{count:1,id:\"minecraft:diamond\"}"), "manual", false);
        QuestDefinition quest = questWithRewards(List.of(reward));

        assertEquals(true, QuestPresentation.hasPendingReward(quest, QuestStatus.COMPLETED, Set.of()));
        assertEquals("screen.brnquest.status.rewards_pending",
                QuestPresentation.statusTranslationKey(quest, QuestStatus.COMPLETED, Set.of()));
        assertEquals(false, QuestPresentation.hasPendingReward(quest, QuestStatus.COMPLETED,
                Set.of(reward.id().toString())));
    }

    private static QuestDefinition questWithRewards(List<RewardDefinition> rewards) {
        return new QuestDefinition(id("book"), id("quest"), id("chapter"), "Quest", "", "", "",
                0, 0, List.of(), List.of(), rewards, "LEGACY");
    }

    private ChapterDefinition chapter(ResourceLocation bookId, ResourceLocation id, ResourceLocation group, int order) {
        return new ChapterDefinition(bookId, id, group, id.getPath(), "", order, List.of());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("brnquest_test", path);
    }
}
