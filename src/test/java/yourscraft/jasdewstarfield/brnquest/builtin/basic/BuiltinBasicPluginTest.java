package yourscraft.jasdewstarfield.brnquest.builtin.basic;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import yourscraft.jasdewstarfield.brnquest.extension.BrnQuestPlugins;
import yourscraft.jasdewstarfield.brnquest.task.*;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerId;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Legacy maps, completion intent, and persisted composition contracts survive plugin extraction. */
class BuiltinBasicPluginTest {
    @Test void registeredPluginPreservesIdsAndRejectsDuplicateInitialization() {
        assertTrue(BrnQuestPlugins.registeredPluginIds().contains(ResourceLocation.parse("brnquest:builtin_basic")));
        for (var id : java.util.List.of(TaskTypes.CHECKMARK, TaskTypes.CUSTOM, TaskTypes.XP))
            assertNotNull(TaskTypeRegistry.get(id));
        for (var id : java.util.List.of(RewardTypes.CUSTOM, RewardTypes.XP, RewardTypes.XP_LEVELS))
            assertNotNull(RewardTypeRegistry.get(id));
        assertFalse(RewardTypeRegistry.get(RewardTypes.XP).hiddenFromCreation());
        assertTrue(RewardTypeRegistry.get(RewardTypes.XP_LEVELS).hiddenFromCreation());
        assertThrows(IllegalArgumentException.class, () -> BrnQuestPlugins.register(new BuiltinBasicPlugin()));
    }
    @Test void oldMapsRetainUnknownKeysAndCustomStillRequiresExternalProgress() {
        var json = JsonParser.parseString("{\"title\":\"Old book\",\"addon.private\":\"opaque\"}");
        for (var id : java.util.List.of(TaskTypes.CHECKMARK, TaskTypes.CUSTOM, TaskTypes.XP)) {
            var type = TaskTypeRegistry.get(id);
            assertEquals(Map.of("title", "Old book", "addon.private", "opaque"),
                    type.configCodec().parse(JsonOps.INSTANCE, json).getOrThrow());
            var task = new TaskView(id, id, id, Map.of(), false);
            assertEquals(id.equals(TaskTypes.CHECKMARK), TaskTypeExecutor.acceptsQuestCompletionIntent(type, task));
            assertEquals(!id.equals(TaskTypes.CUSTOM), TaskTypeExecutor.allowsManualSubmission(type, task));
            assertFalse(TaskTypeExecutor.satisfied(type, new TaskContext(null, id, id, task, 0)));
            assertTrue(TaskTypeExecutor.satisfied(type, new TaskContext(null, id, id, task, 1)));
        }
    }
    @Test void compositionKeepsPreparedDataVersionAndRefusesUncertainReplay() throws Exception {
        for (var id : java.util.List.of(RewardTypes.CUSTOM, RewardTypes.XP, RewardTypes.XP_LEVELS)) {
            var adapter = RewardTypeRegistry.get(id).composition().orElseThrow();
            var config = Map.of(id.getPath(), "7", "addon.private", "opaque");
            var view = new RewardView(id, id, id, config, "manual", false);
            var root = new RewardClaimContext(new RewardContext(null, id, id, view),
                    new ProgressOwnerId(ResourceLocation.parse("brnquest:player"), new java.util.UUID(0, 1)), 1);
            var leaf = new RewardLeafContext(root, "entry", "existing-occurrence", id, config);
            assertEquals("1", adapter.version());
            assertEquals(config, adapter.prepare(leaf));
            assertEquals(RewardClaimResult.State.FAILURE, adapter.recover(leaf, config).state());
            if (!id.equals(RewardTypes.CUSTOM)) {
                assertThrows(IllegalArgumentException.class, () -> adapter.validateConfig(Map.of(id.getPath(), "0")));
                assertThrows(IllegalArgumentException.class, () -> adapter.validateConfig(Map.of(id.getPath(), "bad")));
            }
        }
        // The unified XP receipt carries its selected unit; old point receipts may omit it.
        var experience = RewardTypeRegistry.get(RewardTypes.XP).composition().orElseThrow();
        assertDoesNotThrow(() -> experience.validateConfig(Map.of("xp", "2", "points", "false")));
        assertDoesNotThrow(() -> experience.validateConfig(Map.of("xp", "2")));
        assertThrows(IllegalArgumentException.class,
                () -> experience.validateConfig(Map.of("xp", "2", "points", "levels")));
    }
}
