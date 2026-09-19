package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.task.TaskSubmissionSelection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Interaction factories cannot bypass the core's stale-page and single-request guard. */
class TaskSubmissionInteractionTest {
    @Test void oldPresentationKeepsDirectSubmissionAndNoCandidatePage() {
        var presentation = new ClientTaskPresentation() {};
        assertTrue(presentation.submissionInteraction(null).isEmpty());
        assertTrue(presentation.resolvedOptions(null).isEmpty());
    }
    @Test void duplicateCallbacksSendTheExactOrderedSelectionOnlyOnce() {
        var calls = new AtomicInteger();
        var selection = new TaskSubmissionSelection(List.of(8, 2));
        var dispatch = new TaskSubmissionDispatch(() -> true, value -> { assertEquals(selection, value); calls.incrementAndGet(); });
        dispatch.accept(selection); dispatch.accept(TaskSubmissionSelection.AUTOMATIC);
        assertEquals(1, calls.get());
    }
    @Test void cancelledOrStalePageCannotSendAfterItsGuardBecomesFalse() {
        var active = new AtomicBoolean(true); var calls = new AtomicInteger();
        var dispatch = new TaskSubmissionDispatch(active::get, value -> calls.incrementAndGet());
        active.set(false);
        dispatch.accept(new TaskSubmissionSelection(List.of(0)));
        assertEquals(0, calls.get());
    }
}
