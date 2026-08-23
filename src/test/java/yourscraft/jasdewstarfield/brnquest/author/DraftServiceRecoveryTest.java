package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DraftServiceRecoveryTest {
    @Test void recoveryCopyChangesOnlyBookOwnershipAndKeepsStableContentIds() {
        ResourceLocation sourceId = ResourceLocation.parse("test:source");
        ResourceLocation targetId = ResourceLocation.parse("test:recovered");
        ResourceLocation groupId = ResourceLocation.parse("test:group");
        ResourceLocation chapterId = ResourceLocation.parse("test:chapter");
        ResourceLocation questId = ResourceLocation.parse("test:quest");
        ResourceLocation taskId = ResourceLocation.parse("test:task");
        ResourceLocation rewardId = ResourceLocation.parse("test:reward");
        QuestDefinition quest = new QuestDefinition(sourceId, questId, chapterId, "Quest", "", "", "",
                1, 2, List.of(), List.of(new TaskDefinition(sourceId, taskId,
                ResourceLocation.parse("brnquest:checkmark"), Map.of(), false)),
                List.of(new RewardDefinition(sourceId, rewardId, ResourceLocation.parse("brnquest:item"),
                        Map.of(), "manual", false)), "");
        QuestBookDefinition source = new QuestBookDefinition(sourceId, 1, "Source",
                List.of(new ChapterGroupDefinition(sourceId, groupId, "Group", 0)),
                List.of(new ChapterDefinition(sourceId, chapterId, groupId, "Chapter", "", 0, List.of(quest))),
                Map.of());

        QuestBookDefinition copy = DraftService.recoveryCopy(source, targetId);

        assertEquals(targetId, copy.id());
        assertEquals(targetId, copy.chapterGroups().getFirst().bookId());
        assertEquals(targetId, copy.chapters().getFirst().bookId());
        assertEquals(targetId, copy.quests().getFirst().bookId());
        assertEquals(targetId, copy.quests().getFirst().tasks().getFirst().bookId());
        assertEquals(targetId, copy.quests().getFirst().rewards().getFirst().bookId());
        assertEquals(questId, copy.quests().getFirst().id());
        assertEquals(taskId, copy.quests().getFirst().tasks().getFirst().id());
        assertEquals(rewardId, copy.quests().getFirst().rewards().getFirst().id());
    }
}
