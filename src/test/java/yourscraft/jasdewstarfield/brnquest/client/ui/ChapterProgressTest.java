package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Covers privacy and lifecycle changes rather than rendering a fixed progress string. */
class ChapterProgressTest {
    private static ResourceLocation id(String path) { return ResourceLocation.parse("test:" + path); }
    private static ChapterDefinition chapter(List<QuestStatus> states) {
        var quests = java.util.stream.IntStream.range(0, states.size()).mapToObj(index ->
                new QuestDefinition(id("book"), id("q" + index), id("chapter"), "Quest", "", "", "",
                        0, 0, List.of(), List.of(), List.of(), "")).toList();
        return new ChapterDefinition(id("book"), id("chapter"), id("group"), "Chapter", "", 0, quests, Map.of());
    }

    @Test void hiddenCompletedQuestCannotAffectEitherCountOrTriggerAStatusRead() {
        var chapter = chapter(List.of(QuestStatus.COMPLETED, QuestStatus.LOCKED, QuestStatus.REWARD_CLAIMED));
        var result = ChapterProgress.count(chapter, quest -> !quest.equals(id("q2")), quest -> {
            assertNotEquals(id("q2"), quest);
            return quest.equals(id("q0")) ? QuestStatus.COMPLETED : QuestStatus.LOCKED;
        });
        assertEquals(new ChapterProgress(1, 2), result);
        assertEquals(50, result.percent());
    }

    @Test void completionAndClaimCountButActiveLockedAndUnavailableDoNot() {
        var states = List.of(QuestStatus.values());
        var result = ChapterProgress.count(chapter(states), quest -> true,
                quest -> states.get(Integer.parseInt(quest.getPath().substring(1))));
        assertEquals(new ChapterProgress(2, 6), result);
        assertEquals(33, result.percent());
    }

    @Test void resetOrNewCycleRecomputesCompletionWithoutHistoricalCredit() {
        var chapter = chapter(List.of(QuestStatus.COMPLETED));
        assertEquals(100, ChapterProgress.count(chapter, quest -> true, quest -> QuestStatus.COMPLETED).percent());
        assertEquals(0, ChapterProgress.count(chapter, quest -> true, quest -> QuestStatus.AVAILABLE).percent());
        assertEquals(0, ChapterProgress.count(chapter, quest -> true, quest -> null).percent());
    }

    @Test void emptyAndFullyHiddenChaptersHaveZeroProgress() {
        assertEquals(new ChapterProgress(0, 0), ChapterProgress.count(chapter(List.of()), quest -> true, quest -> null));
        assertEquals(0, ChapterProgress.count(chapter(List.of(QuestStatus.COMPLETED)), quest -> false,
                quest -> { fail("Hidden state must not be read"); return null; }).percent());
    }
}
