package yourscraft.jasdewstarfield.brnquest.extension;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.reward.RewardContext;
import yourscraft.jasdewstarfield.brnquest.reward.RewardResult;
import yourscraft.jasdewstarfield.brnquest.reward.RewardType;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.task.TaskContext;
import yourscraft.jasdewstarfield.brnquest.task.TaskType;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeRegistry;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrnQuestPluginContractTest {
    private static final ResourceLocation PLUGIN = id("atomic_plugin");
    private static final ResourceLocation TASK = id("atomic_task");

    @Test void failedPluginCallbackDoesNotPartiallyCommitDeclarations() {
        assertThrows(IllegalStateException.class, () -> BrnQuestPlugins.register(new BrnQuestPlugin() {
            public ResourceLocation id() { return PLUGIN; }
            public void register(BrnQuestExtensionRegistrar registrar) {
                registrar.task(TASK, new PassiveTask());
                throw new IllegalStateException("synthetic plugin failure");
            }
        }));

        assertFalse(BrnQuestPlugins.registeredPluginIds().contains(PLUGIN));
        assertTrue(TaskTypeRegistry.get(TASK) == null, "staged task must not survive a failed plugin callback");
    }

    @Test void pluginCannotClaimAnotherModsNamespace() {
        ResourceLocation plugin = id("namespace_plugin");
        assertThrows(IllegalArgumentException.class, () -> BrnQuestPlugins.register(new BrnQuestPlugin() {
            public ResourceLocation id() { return plugin; }
            public void register(BrnQuestExtensionRegistrar registrar) {
                registrar.task(ResourceLocation.fromNamespaceAndPath("foreign", "task"), new PassiveTask());
            }
        }));

        assertFalse(BrnQuestPlugins.registeredPluginIds().contains(plugin));
    }

    @Test void crossRegistryConflictIsRejectedBeforeAnyDeclarationCommits() {
        ResourceLocation occupiedReward = id("occupied_reward");
        ResourceLocation stagedTask = id("conflict_staged_task");
        ResourceLocation plugin = id("conflict_plugin");
        RewardTypeRegistry.register(occupiedReward, new PassiveReward());

        assertThrows(IllegalArgumentException.class, () -> BrnQuestPlugins.register(new BrnQuestPlugin() {
            public ResourceLocation id() { return plugin; }
            public void register(BrnQuestExtensionRegistrar registrar) {
                registrar.task(stagedTask, new PassiveTask());
                registrar.reward(occupiedReward, new PassiveReward());
            }
        }));

        assertTrue(TaskTypeRegistry.get(stagedTask) == null,
                "a reward conflict must reject the plugin before its staged task commits");
        assertFalse(BrnQuestPlugins.registeredPluginIds().contains(plugin));
    }

    @Test void fieldSourceConflictDoesNotLeakAnOtherwiseValidTask() {
        var source = id("occupied_field_source");
        var task = id("source_conflict_task");
        yourscraft.jasdewstarfield.brnquest.editor.ServerFieldSources.register(source, (p,f,v) -> null);
        assertThrows(IllegalStateException.class, () -> BrnQuestPlugins.register(new BrnQuestPlugin() {
            public ResourceLocation id() { return BrnQuestPluginContractTest.id("source_conflict_plugin"); }
            public void register(BrnQuestExtensionRegistrar registrar) {
                registrar.task(task, new PassiveTask()).fieldSource(source, (p,f,v) -> null);
            }
        }));
        assertTrue(TaskTypeRegistry.get(task) == null, "source conflict must abort the whole batch");
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("brnquest_plugin_test", path);
    }

    private static final class PassiveTask implements TaskType<Map<String, String>> {
        public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
        public boolean satisfied(TaskContext context, Map<String, String> config) { return false; }
        public Component describe(TaskView task, Map<String, String> config) { return Component.literal("test"); }
    }

    private static final class PassiveReward implements RewardType<Map<String, String>> {
        public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
        public RewardResult execute(RewardContext context, Map<String, String> config) {
            return RewardResult.success("test");
        }
    }
}
