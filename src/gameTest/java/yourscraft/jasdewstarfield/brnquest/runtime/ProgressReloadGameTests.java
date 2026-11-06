package yourscraft.jasdewstarfield.brnquest.runtime;

import com.mojang.serialization.Codec;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.task.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Same-content reloads replace script implementations and invalidate definition indexes on the real commit path. */
@GameTestHolder("brnquest")
@PrefixGameTestTemplate(false)
@SuppressWarnings("removal")
public final class ProgressReloadGameTests {
    @GameTest(template = "empty", batch = "progressScriptReplacement")
    public static void sameBookRevisionUsesCurrentInventoryPollingAndRewardTypes(GameTestHelper helper) {
        var previous = QuestBookManager.get().active().orElseThrow().book();
        var oldTasks = ScriptExtensionRegistry.snapshot().taskTypeIds().stream().map(ResourceLocation::parse)
                .collect(Collectors.toMap(id -> id, TaskTypeRegistry::get));
        var oldRewards = ScriptExtensionRegistry.snapshot().rewardTypeIds().stream().map(ResourceLocation::parse)
                .collect(Collectors.toMap(id -> id, RewardTypeRegistry::get));
        var player = helper.makeMockServerPlayerInLevel();
        var inventory = new TaskDefinition(id("book"), id("inventory"), id("task_type"), Map.of("mode", "inventory"), false);
        var polling = new TaskDefinition(id("book"), id("polling"), id("task_type"), Map.of("mode", "poll"), false);
        var reward = new RewardDefinition(id("book"), id("reward"), id("reward_type"), Map.of(), "manual", false);
        var inventoryQuest = quest("inventory_quest", inventory, List.of(reward));
        var pollingQuest = quest("poll_quest", polling, List.of());
        var book = new QuestBookDefinition(id("book"), 1, "Script replacement",
                List.of(new ChapterGroupDefinition(id("book"), id("group"), "Group", 0)),
                List.of(new ChapterDefinition(id("book"), id("chapter"), id("group"), "Chapter", "", 0,
                        List.of(inventoryQuest, pollingQuest))), Map.of());
        int[] executions = {0, 0};
        try {
            install(book, taskType(false), rewardType(executions, 0));
            var engine = ProgressEngine.get(); engine.reconcile(player);
            engine.submitInventoryTasks(player); engine.pollTasks(player);
            helper.assertTrue(engine.taskProgress(player, inventoryQuest, inventory) == 0
                    && engine.taskProgress(player, pollingQuest, polling) == 0, "Disabled initial type warms both indexes without progress");
            var initial = QuestBookManager.get().active().orElseThrow();
            install(book, taskType(true), rewardType(executions, 1));
            var replacement = QuestBookManager.get().active().orElseThrow();
            helper.assertTrue(initial.revision().equals(replacement.revision()) && initial != replacement,
                    "An identical book still installs a new snapshot with its replacement script batch");
            engine.reconcile(player); engine.submitInventoryTasks(player); engine.pollTasks(player);
            helper.assertTrue(engine.taskProgress(player, inventoryQuest, inventory) == 1, "Inventory index sees the enabled replacement");
            helper.assertTrue(engine.taskProgress(player, pollingQuest, polling) == 2, "Polling interval and sample use the enabled replacement immediately");
            helper.assertTrue(engine.claim(player, reward.id()).changed(), "Current reward implementation executes");
            helper.assertTrue(executions[0] == 0 && executions[1] == 1, "Old reward implementation is never executed");
            engine.reset(player, inventoryQuest.id()); engine.reset(player, pollingQuest.id());
            install(book, taskType(false), rewardType(executions, 0));
            engine.reconcile(player); engine.submitInventoryTasks(player); engine.pollTasks(player);
            helper.assertTrue(engine.taskProgress(player, inventoryQuest, inventory) == 0
                    && engine.taskProgress(player, pollingQuest, polling) == 0, "Replacing back to a disabled type leaves no cached sampler active");
        } finally {
            // Restore the complete prior batch, so unrelated fixture scripts cannot leak into later tests.
            ScriptExtensionRegistry.beginRegistration();
            oldTasks.forEach(ScriptExtensionRegistry::stageTask);
            oldRewards.forEach(ScriptExtensionRegistry::stageReward);
            ScriptExtensionRegistry.sealRegistration();
            if (!QuestBookReloadTransaction.install(previous, new DiagnosticReport(), null))
                throw new IllegalStateException("Could not restore the previous script/book pair");
        }
        helper.succeed();
    }

    private static void install(QuestBookDefinition book, TaskType<?> task, RewardType<?> reward) {
        ScriptExtensionRegistry.beginRegistration();
        ScriptExtensionRegistry.stageTask(id("task_type"), task);
        ScriptExtensionRegistry.stageReward(id("reward_type"), reward);
        ScriptExtensionRegistry.sealRegistration();
        if (!QuestBookReloadTransaction.install(book, new DiagnosticReport(), null))
            throw new IllegalStateException("Replacement script/book pair was rejected");
    }
    private static TaskType<Map<String, String>> taskType(boolean enabled) {
        return new TaskType<>() {
            public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
            public int pollingIntervalTicks() { return enabled ? 1 : 0; }
            public boolean reevaluateOnInventoryChange(Map<String, String> config) { return enabled && config.get("mode").equals("inventory"); }
            public long sampledProgress(TaskContext context, Map<String, String> config) { return config.get("mode").equals("poll") ? 2 : context.progress(); }
            public TaskSubmissionResult submit(TaskContext context, Map<String, String> config) { return TaskSubmissionResult.accepted(); }
            public boolean satisfied(TaskContext context, Map<String, String> config) { return context.progress() >= 1; }
            public Component describe(TaskView task, Map<String, String> config) { return Component.literal("Reload fixture"); }
        };
    }
    private static RewardType<Map<String, String>> rewardType(int[] executions, int index) {
        return new RewardType<>() {
            public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
            public RewardResult execute(RewardContext context, Map<String, String> config) {
                executions[index]++; return RewardResult.success("replacement executed");
            }
        };
    }
    private static QuestDefinition quest(String name, TaskDefinition task, List<RewardDefinition> rewards) {
        return new QuestDefinition(id("book"), id(name), id("chapter"), name, "", "", "", 0, 0,
                List.of(), List.of(task), rewards, "");
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("progress_reload", path); }
}
