package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static yourscraft.jasdewstarfield.brnquest.client.ui.QuestNotificationQueue.Kind.*;

/** Exercise immediate display, bounded folding and shared closure without wall-clock sleeps. */
class QuestNotificationQueueTest {
    private static final long SECOND = 1_000_000_000L;
    private static QuestNotificationQueue.Notice notice(String event, String quest, QuestNotificationQueue.Kind kind) {
        return new QuestNotificationQueue.Notice(event, quest, "target", kind);
    }
    private static QuestNotificationQueue.Stack stack(QuestNotificationQueue queue, double seconds) {
        return queue.stack((long) (seconds * SECOND), 4 * SECOND);
    }
    @Test void repeatedDeliveryIsIgnoredAndNearbyCompletionsMerge() {
        var queue = new QuestNotificationQueue();
        queue.add(notice("a", "one", QUEST), 0);
        assertEquals("a", stack(queue, 0).visible().getFirst().id(), "First frame must not wait for the merge window");
        queue.add(notice("a", "one", QUEST), 100_000_000);
        queue.add(notice("b", "one", QUEST), 200_000_000);
        assertEquals(2, stack(queue, .2).visible().getFirst().notices().size());
        assertEquals(2, stack(queue, .6).visible().getFirst().notices().size());
        queue.add(notice("a", "one", QUEST), 4 * SECOND);
        assertTrue(stack(queue, 4.2).isEmpty(), "Duplicate deliveries must not extend the timer");
    }
    @Test void simultaneousCompletionsOfDifferentQuestsFormSeparateCardsWithTheLatestAtTheFront() {
        var queue = new QuestNotificationQueue();
        queue.add(notice("a", "one", QUEST), 0);
        queue.add(notice("b", "two", QUEST), 100_000_000);
        queue.add(notice("c", "three", QUEST), 200_000_000);
        var result = stack(queue, .8);
        assertEquals(java.util.List.of("c", "b", "a"), result.visible().stream().map(QuestNotificationQueue.Active::id).toList());
        assertEquals(2, result.foldedCount());
    }
    @Test void wholeQuestSupersedesOnlyItsOwnSimultaneousObjective() {
        var queue = new QuestNotificationQueue();
        queue.add(notice("a", "one", TASK), 0);
        var original = stack(queue, 0).visible().getFirst();
        queue.add(notice("b", "two", TASK), 100_000_000);
        queue.add(notice("c", "one", QUEST), 200_000_000);
        var first = stack(queue, .8);
        assertEquals(QUEST, first.visible().getFirst().notices().getFirst().kind());
        assertEquals(original.id(), first.visible().getFirst().id(), "In-place replacement must not restart the entry animation");
        assertEquals("c", first.visible().getFirst().notices().getFirst().eventId());
        assertEquals(TASK, original.notices().getFirst().kind(), "Previously returned views remain immutable");
        assertEquals("two", first.visible().get(1).notices().getFirst().questId());
        assertEquals(1, first.foldedCount());
    }
    @Test void newArrivalsRestartOneClockAndTheWholeStackExpiresTogether() {
        var queue = new QuestNotificationQueue();
        queue.add(notice("a", "one", QUEST), 0);
        stack(queue, .6);
        queue.add(notice("b", "two", REWARD), SECOND);
        var covered = stack(queue, 1.6);
        assertEquals("b", covered.visible().getFirst().id());
        assertEquals(600_000_000L, covered.visible().getFirst().elapsedNanos());
        assertEquals(covered.visible().getFirst().elapsedNanos(), covered.visible().get(1).elapsedNanos());
        assertEquals(java.util.List.of("b", "a"), stack(queue, 4.9).visible().stream().map(QuestNotificationQueue.Active::id).toList());
        assertTrue(stack(queue, 5).isEmpty());
        assertTrue(stack(queue, 8.6).isEmpty(), "Older cards must not replay after stack closure");
    }
    @Test void atMostThreeCardsAreVisibleAndTheCountIncludesTheHiddenFoldedGroups() {
        var queue = new QuestNotificationQueue();
        for (int i = 0; i < 5; i++) {
            queue.add(notice("event" + i, "quest" + i, QUEST), i * SECOND);
            stack(queue, i + .6);
        }
        var result = stack(queue, 4.7);
        assertEquals(java.util.List.of("event4", "event3", "event2"), result.visible().stream().map(QuestNotificationQueue.Active::id).toList());
        assertEquals(4, result.foldedCount());
    }
    @Test void changingDurationAppliesToTheEntireStack() {
        var queue = new QuestNotificationQueue();
        queue.add(notice("a", "one", TASK), 0);
        queue.add(notice("b", "two", QUEST), 100_000_000);
        assertEquals("b", stack(queue, .8).visible().getFirst().id());
        assertEquals("b", queue.stack(3 * SECOND, 10 * SECOND).visible().getFirst().id());
        var shorter = queue.stack(3 * SECOND, 2 * SECOND);
        assertTrue(shorter.isEmpty());
        assertTrue(queue.stack(5 * SECOND, 2 * SECOND).isEmpty());
    }
    @Test void anArrivalDuringSharedClosureKeepsTheStackAndRestartsItsDeadline() {
        var queue = new QuestNotificationQueue();
        queue.add(notice("a", "one", QUEST), 0);
        queue.add(notice("b", "two", QUEST), 100_000_000);
        var closing = stack(queue, 4);
        assertEquals(2, closing.visible().size());
        assertTrue(closing.exitAllowed());
        queue.add(notice("c", "three", QUEST), 4 * SECOND);
        var restarted = stack(queue, 4);
        assertEquals(3, restarted.visible().size());
        assertTrue(restarted.visible().stream().allMatch(card -> card.elapsedNanos() == 0));
        assertEquals(3, stack(queue, 7.9).visible().size());
        assertTrue(stack(queue, 8).isEmpty());
    }
    @Test void capacityAndBatchSizesAreBoundedAndClearResetsDeduplication() {
        var queue = new QuestNotificationQueue();
        queue.add(notice("first", "one", QUEST), 0);
        stack(queue, .6);
        for (int i = 0; i < 100; i++)
            queue.add(notice("event" + i, "quest" + i, i % 2 == 0 ? TASK : REWARD), 700_000_000 + i);
        var pending = stack(queue, 1);
        assertEquals("event99", pending.visible().getFirst().id());
        var admitted = stack(queue, 1.3);
        assertEquals("event99", admitted.visible().getFirst().id());
        assertEquals(100, admitted.foldedCount());
        queue.clear();
        assertTrue(stack(queue, 2).isEmpty());
        for (int i = 0; i < 100; i++) queue.add(notice("event" + i, "one", QUEST), 2 * SECOND + i);
        var batches = stack(queue, 2.6);
        assertEquals(3, batches.visible().size());
        assertEquals(3, batches.foldedCount());
        assertEquals(4, batches.visible().getFirst().notices().size());
        assertEquals(32, batches.visible().get(1).notices().size());
        queue.clear();
        queue.add(notice("first", "one", QUEST), 3 * SECOND);
        assertFalse(stack(queue, 3.6).isEmpty());
    }
    @Test void anArrivalAfterTheDeadlineDoesNotResurrectAnUnrenderedOldStack() {
        var queue = new QuestNotificationQueue();
        queue.add(notice("a", "one", QUEST), 0);
        queue.add(notice("b", "two", QUEST), 100_000_000);
        stack(queue, .2);
        queue.add(notice("c", "three", QUEST), 6 * SECOND);
        var fresh = stack(queue, 6);
        assertEquals(java.util.List.of("c"), fresh.visible().stream().map(QuestNotificationQueue.Active::id).toList());
        assertEquals(0, fresh.foldedCount());
    }
    @Test void discardedContentCannotReturnOrTakePartInAnInPlaceReplacement() {
        var queue = new QuestNotificationQueue();
        queue.add(notice("a", "one", TASK), 0);
        for (int i = 2; i <= 4; i++) queue.add(notice("e" + i, "q" + i, QUEST), i * 1_000_000L);
        assertEquals(3, stack(queue, .1).foldedCount());
        queue.add(notice("complete", "one", QUEST), 200_000_000);
        var result = stack(queue, .2);
        assertEquals(java.util.List.of("complete", "e4", "e3"), result.visible().stream().map(QuestNotificationQueue.Active::id).toList());
        assertEquals(4, result.foldedCount());
        assertTrue(stack(queue, 4.2).isEmpty());
        assertEquals(0, stack(queue, 4.2).foldedCount());
    }
    @Test void aLateObjectiveCannotCoverItsWholeQuestNoticeOrRestartTheClock() {
        var queue = new QuestNotificationQueue();
        queue.add(notice("whole", "one", QUEST), 0);
        queue.add(notice("objective", "one", TASK), 100_000_000);
        assertEquals(java.util.List.of("whole"), stack(queue, .1).visible().stream().map(QuestNotificationQueue.Active::id).toList());
        assertTrue(stack(queue, 4).isEmpty());
    }
}
