package yourscraft.jasdewstarfield.brnquest.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientQuestStateTest {
    @Test void retainsTaskProgressFromServerSnapshot() {
        ClientQuestState state = ClientQuestState.get();
        state.progress("{\"quests\":{},\"tasks\":{\"test:task\":3},\"claimed\":[],\"revision\":\"r1\"}");

        assertEquals(3L, state.taskProgress().get("test:task"));
    }
}
