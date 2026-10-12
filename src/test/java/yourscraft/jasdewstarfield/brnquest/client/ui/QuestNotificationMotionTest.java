package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import java.util.List;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import static org.junit.jupiter.api.Assertions.*;

/** Sample intermediate poses to catch animation restarts that final-position checks would miss. */
class QuestNotificationMotionTest {
    private static final long SECOND = 1_000_000_000L;
    private static QuestNotificationMotion.Target target(String id, UiRect bounds) {
        return new QuestNotificationMotion.Target(id, bounds);
    }
    @Test void aNewCardEntersIndependentlyWhileThePreviousCardShrinksInPlace() {
        var motion = new QuestNotificationMotion();
        var original = QuestNotificationLayout.front(400, 280, 60, 1);
        var single = List.of(target("old", original));
        assertTrue(motion.frame(single, 0, false, 0).getFirst().top() < 0);
        assertEquals(QuestNotificationMotion.Pose.of(original), motion.frame(single, SECOND, false, 0).getFirst());
        var front = QuestNotificationLayout.front(400, 280, 74, 2);
        var targets = List.of(target("new", front), target("old", QuestNotificationLayout.folded(front, 1)));
        var start = motion.frame(targets, 2 * SECOND, false, 0);
        assertEquals(original.top(), start.get(1).top(), .0001);
        assertEquals(original.width(), start.get(1).width(), .0001);
        assertTrue(start.getFirst().top() < 0);
        var folded = motion.frame(targets, 2 * SECOND + 50_000_000, false, 0);
        assertTrue(folded.get(1).width() < original.width());
        assertTrue(folded.getFirst().top() > start.getFirst().top(), "New card must start entering without a folding delay");
        var arriving = motion.frame(targets, 2 * SECOND + 150_000_000, false, 0);
        assertTrue(arriving.getFirst().top() > start.getFirst().top());
        assertTrue(arriving.get(1).top() >= 0);
    }
    @Test void aFoldedCardReturnsByExpandingAndRapidNewArrivalsKeepTheCurrentPose() {
        var motion = new QuestNotificationMotion();
        var front = QuestNotificationLayout.front(400, 280, 74, 2);
        var rear = QuestNotificationLayout.folded(front, 1);
        var pair = List.of(target("new", front), target("old", rear));
        motion.frame(pair, 0, false, 0);
        motion.frame(pair, SECOND, false, 0);
        var original = QuestNotificationLayout.front(400, 280, 60, 1);
        var restored = motion.frame(List.of(target("old", original)), 2 * SECOND, false, 0).getFirst();
        assertEquals(QuestNotificationMotion.Pose.of(rear), restored);
        var halfway = motion.frame(List.of(target("old", original)), 2 * SECOND + 100_000_000, false, 0).getFirst();
        assertTrue(halfway.width() > rear.width());
        var interrupted = motion.frame(pair, 2 * SECOND + 100_000_000, false, 0).get(1);
        assertEquals(halfway, interrupted);
    }
    @Test void reducedMotionSnapsAndCanceledClosureDoesNotJumpBack() {
        var motion = new QuestNotificationMotion();
        var bounds = QuestNotificationLayout.front(400, 280, 60, 1);
        var targets = List.of(target("one", bounds));
        assertEquals(QuestNotificationMotion.Pose.of(bounds), motion.frame(targets, 0, true, -30).getFirst());
        var closing = motion.frame(targets, SECOND, false, -30).getFirst();
        var resumed = motion.frame(targets, SECOND, false, 0).getFirst();
        assertEquals(closing, resumed);
        assertEquals(QuestNotificationMotion.Pose.of(bounds), motion.frame(targets, 2 * SECOND, false, 0).getFirst());
        motion.clear();
        assertTrue(motion.frame(targets, 3 * SECOND, false, 0).getFirst().top() < 0);
    }
    @Test void threeCardsWithdrawTogetherAndNoOlderCardGetsAnotherExitCycle() {
        var queue = new QuestNotificationQueue();
        var motion = new QuestNotificationMotion();
        for (int i = 0; i < 5; i++) queue.add(new QuestNotificationQueue.Notice("n" + i, "q" + i, "", QuestNotificationQueue.Kind.QUEST), 0);
        var front = QuestNotificationLayout.front(400, 280, 74, 3);
        var stack = queue.stack(0, 4 * SECOND);
        var targets = java.util.stream.IntStream.range(0, 3).mapToObj(i -> target(stack.visible().get(i).id(),
                i == 0 ? front : QuestNotificationLayout.folded(front, i))).toList();
        motion.frame(targets, 0, false, 0);
        var resting = motion.frame(targets, SECOND, false, 0);
        var closing = queue.stack(3_900_000_000L, 4 * SECOND);
        assertTrue(closing.exitAllowed());
        assertEquals(stack.visible().stream().map(QuestNotificationQueue.Active::id).toList(),
                closing.visible().stream().map(QuestNotificationQueue.Active::id).toList());
        double offset = -QuestNotificationMotion.ease((closing.visible().getFirst().elapsedNanos() - 4 * SECOND + 200_000_000L)
                / 200_000_000.0) * (front.bottom() + 8);
        var moving = motion.frame(targets, 3_900_000_000L, false, offset);
        assertTrue(offset < 0);
        for (int i = 0; i < 3; i++) assertEquals(offset, moving.get(i).top() - resting.get(i).top(), .0001);
        assertTrue(queue.stack(4 * SECOND, 4 * SECOND).isEmpty());
        assertTrue(motion.frame(List.of(), 4 * SECOND, false, 0).isEmpty());
        assertTrue(queue.stack(5 * SECOND, 4 * SECOND).isEmpty());
    }
}
