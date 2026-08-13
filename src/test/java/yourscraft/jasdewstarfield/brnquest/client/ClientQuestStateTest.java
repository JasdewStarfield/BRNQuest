package yourscraft.jasdewstarfield.brnquest.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientQuestStateTest {
    @Test void retainsTaskProgressFromServerSnapshot() {
        ClientQuestState state = ClientQuestState.get();
        state.progress("{\"quests\":{},\"tasks\":{\"test:task\":3},\"claimed\":[],\"revision\":\"r1\"}");

        assertEquals(3L, state.taskProgress().get("test:task"));
    }

    @Test void progressResponseAcknowledgesPendingTaskSubmission() {
        ClientQuestState state = ClientQuestState.get();
        assertTrue(state.beginTaskSubmission("test:pending"));
        assertFalse(state.beginTaskSubmission("test:pending"));

        state.progress("{\"quests\":{},\"tasks\":{},\"claimed\":[],\"revision\":\"r1\"}");

        assertFalse(state.isTaskSubmissionPending("test:pending"));
        assertTrue(state.beginTaskSubmission("test:pending"));
    }
}
