package yourscraft.jasdewstarfield.brnquest.builtin.basic.client;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** XP readiness stays with its presentation while preserving the historical points flag. */
class BuiltinBasicClientTest {
    @Test void experienceReadinessMatchesTheConfiguredBalanceKind() {
        TaskView points = taskOfType("brnquest:xp", Map.of("value", "5", "points", "true"));
        assertFalse(BuiltinBasicClient.experienceAvailable(points, 4, 20));
        assertTrue(BuiltinBasicClient.experienceAvailable(points, 5, 0));

        TaskView levels = taskOfType("brnquest:xp", Map.of("value", "2", "points", "false"));
        assertFalse(BuiltinBasicClient.experienceAvailable(levels, 100, 1));
        assertTrue(BuiltinBasicClient.experienceAvailable(levels, 0, 2));
    }

    private static TaskView taskOfType(String typeId, Map<String, String> config) {
        return new TaskView(ResourceLocation.parse("test:book"), ResourceLocation.parse("test:task"),
                ResourceLocation.parse(typeId), config, false);
    }
}
