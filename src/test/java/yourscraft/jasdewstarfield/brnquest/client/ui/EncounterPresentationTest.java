package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EncounterPresentationTest {
    @Test void partialKillUsesUnmetRowUntilConfiguredThreshold() {
        var task = task("kill_entity", Map.of("count", "2"));
        var presentation = ClientTaskPresentationRegistry.get(task.typeId());
        // Exercise the same registry dispatch used by the detail row, not just the concrete type.
        assertFalse(ClientTaskPresentationRegistry.confirmed(task, 1));
        assertEquals(TaskDisplayState.UNMET, TaskDisplayState.resolve(QuestStatus.ACTIVE,
                ClientTaskPresentationRegistry.confirmed(task, 1), false, false, false, false));
        assertTrue(presentation.confirmed(task, 2));
    }

    @Test void observationCompletionUsesLatchRatherThanDuration() {
        var task = task("observe", Map.of("duration", "40"));
        var presentation = ClientTaskPresentationRegistry.get(task.typeId());
        assertFalse(presentation.confirmed(task, 0));
        assertTrue(presentation.confirmed(task, 1));
    }

    private static TaskView task(String type, Map<String, String> config) {
        return new TaskView(ResourceLocation.parse("test:book"), ResourceLocation.parse("test:task"),
                ResourceLocation.parse("brnquest:" + type), config, false);
    }
}
