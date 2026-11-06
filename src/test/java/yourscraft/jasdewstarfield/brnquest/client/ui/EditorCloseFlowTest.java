package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EditorCloseFlowTest {
    @Test void confirmationAndCloseAcknowledgementEachConsumeOnlyOnce() {
        var flow = new EditorCloseFlow();
        var plan = new EditorCloseFlow.Plan(null, "", false, EditorCloseFlow.AfterClose.COPY_CURRENT);
        flow.request(plan, true);
        assertEquals(EditorCloseFlow.Step.CONFIRM_DISCARD, flow.step());
        assertTrue(flow.closed().isEmpty(), "a close reply cannot skip the user's pending discard decision");
        assertEquals(plan, flow.confirm().orElseThrow());
        assertTrue(flow.confirm().isEmpty(), "repeated confirm input must not dispatch another close");
        assertEquals(plan, flow.closed().orElseThrow());
        assertTrue(flow.closed().isEmpty(), "the next operation is dispatched only once");
    }

    @Test void cancelOrFailureDiscardsTheQueuedOperationBeforeAnotherRequest() {
        var flow = new EditorCloseFlow();
        flow.request(new EditorCloseFlow.Plan(null, "", false, EditorCloseFlow.AfterClose.SHOW_VERSIONS), true);
        flow.cancel();
        assertTrue(flow.confirm().isEmpty());
        assertTrue(flow.closed().isEmpty());
        var next = new EditorCloseFlow.Plan(ResourceLocation.parse("test:other"), "selected-revision", false,
                EditorCloseFlow.AfterClose.NONE);
        flow.request(next, false);
        assertEquals(next, flow.closed().orElseThrow());
        assertEquals(EditorCloseFlow.Step.IDLE, flow.step());
    }
}
