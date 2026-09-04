package yourscraft.jasdewstarfield.brnquest.runtime;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.reward.RewardContext;
import yourscraft.jasdewstarfield.brnquest.reward.RewardResult;
import yourscraft.jasdewstarfield.brnquest.reward.RewardType;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypes;
import yourscraft.jasdewstarfield.brnquest.task.TaskContext;
import yourscraft.jasdewstarfield.brnquest.task.TaskType;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptExtensionRegistryTest {
    private static final ResourceLocation TASK_ID = id("script_task");
    private static final ResourceLocation REWARD_ID = id("script_reward");

    @AfterEach
    void clearScriptTypes() {
        ScriptExtensionRegistry.rollbackRegistration();
        ScriptExtensionRegistry.beginRegistration();
        ScriptExtensionRegistry.sealRegistration();
        ScriptExtensionRegistry.commitSealedRegistration();
    }

    @Test
    void candidateBatchBecomesVisibleOnlyAfterAtomicCommit() {
        TaskType<Map<String, String>> task = task();
        RewardType<Map<String, String>> reward = reward();

        ScriptExtensionRegistry.beginRegistration();
        ScriptExtensionRegistry.stageTask(TASK_ID, task);
        ScriptExtensionRegistry.stageReward(REWARD_ID, reward);

        assertNull(TaskTypeRegistry.get(TASK_ID));
        assertNull(RewardTypeRegistry.get(REWARD_ID));
        assertTrue(ScriptExtensionRegistry.snapshot().registrationOpen());

        ScriptExtensionRegistry.sealRegistration();

        assertNull(TaskTypeRegistry.get(TASK_ID));
        assertSame(task, ScriptExtensionRegistry.withCandidateLookup(() -> TaskTypeRegistry.get(TASK_ID)));
        assertFalse(ScriptExtensionRegistry.snapshot().registrationOpen());

        ScriptExtensionRegistry.commitSealedRegistration();

        assertSame(task, TaskTypeRegistry.get(TASK_ID));
        assertSame(reward, RewardTypeRegistry.get(REWARD_ID));
        assertEquals(java.util.List.of(TASK_ID.toString()), ScriptExtensionRegistry.snapshot().taskTypeIds());
        assertFalse(ScriptExtensionRegistry.snapshot().registrationOpen());
    }

    @Test
    void rollbackPreservesLastKnownGoodSnapshot() {
        TaskType<Map<String, String>> original = task();
        ScriptExtensionRegistry.beginRegistration();
        ScriptExtensionRegistry.stageTask(TASK_ID, original);
        ScriptExtensionRegistry.sealRegistration();
        ScriptExtensionRegistry.commitSealedRegistration();

        ScriptExtensionRegistry.beginRegistration();
        ScriptExtensionRegistry.stageTask(id("replacement"), task());
        ScriptExtensionRegistry.rollbackRegistration();

        assertSame(original, TaskTypeRegistry.get(TASK_ID));
        assertNull(TaskTypeRegistry.get(id("replacement")));
    }

    @Test
    void cleanCommitReplacesRatherThanAccumulatesThePreviousBatch() {
        ScriptExtensionRegistry.beginRegistration();
        ScriptExtensionRegistry.stageTask(TASK_ID, task());
        ScriptExtensionRegistry.sealRegistration();
        ScriptExtensionRegistry.commitSealedRegistration();

        ResourceLocation replacementId = id("replacement");
        ScriptExtensionRegistry.beginRegistration();
        ScriptExtensionRegistry.stageTask(replacementId, task());
        ScriptExtensionRegistry.sealRegistration();
        ScriptExtensionRegistry.commitSealedRegistration();

        assertNull(TaskTypeRegistry.get(TASK_ID));
        assertTrue(TaskTypeRegistry.get(replacementId) != null);
    }

    @Test
    void aNewReloadReplacesAnUnfinishedSealedCandidate() {
        ScriptExtensionRegistry.beginRegistration();
        ScriptExtensionRegistry.stageTask(TASK_ID, task());
        ScriptExtensionRegistry.sealRegistration();

        ResourceLocation replacementId = id("replacement");
        ScriptExtensionRegistry.beginRegistration();
        ScriptExtensionRegistry.stageTask(replacementId, task());
        ScriptExtensionRegistry.sealRegistration();
        ScriptExtensionRegistry.commitSealedRegistration();

        assertNull(TaskTypeRegistry.get(TASK_ID));
        assertTrue(TaskTypeRegistry.get(replacementId) != null);
    }

    @Test
    void scriptsCannotShadowJavaTypesOrUseReservedNamespaces() {
        ScriptExtensionRegistry.beginRegistration();

        assertThrows(IllegalArgumentException.class,
                () -> ScriptExtensionRegistry.stageTask(TaskTypes.CHECKMARK, task()));
        assertThrows(IllegalArgumentException.class,
                () -> ScriptExtensionRegistry.stageReward(RewardTypes.ITEM, reward()));
        assertThrows(IllegalArgumentException.class,
                () -> ScriptExtensionRegistry.stageTask(ResourceLocation.fromNamespaceAndPath("minecraft", "custom"), task()));
        assertThrows(IllegalArgumentException.class,
                () -> ScriptExtensionRegistry.stageReward(ResourceLocation.fromNamespaceAndPath("brnquest", "custom_script"), reward()));
    }

    @Test
    void duplicateDeclarationRejectsWholeCandidateWithoutChangingActiveTypes() {
        ScriptExtensionRegistry.beginRegistration();
        ScriptExtensionRegistry.stageTask(TASK_ID, task());
        assertThrows(IllegalArgumentException.class,
                () -> ScriptExtensionRegistry.stageTask(TASK_ID, task()));
        ScriptExtensionRegistry.rollbackRegistration();

        assertNull(TaskTypeRegistry.get(TASK_ID));
    }

    private static TaskType<Map<String, String>> task() {
        return new TaskType<>() {
            public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
            public boolean satisfied(TaskContext context, Map<String, String> config) { return false; }
            public Component describe(yourscraft.jasdewstarfield.brnquest.api.TaskView value,
                                      Map<String, String> config) { return Component.literal("script"); }
        };
    }

    private static RewardType<Map<String, String>> reward() {
        return new RewardType<>() {
            public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
            public RewardResult execute(RewardContext context, Map<String, String> config) {
                return RewardResult.success("script");
            }
        };
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("brnquest_script_test", path);
    }
}
