package yourscraft.jasdewstarfield.brnquest.gametest;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.*;

import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.Commands;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.progress.*;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Native command execution and authoritative receipts, using only the isolated GameTest world. */
@GameTestHolder("brnquest")
@SuppressWarnings("removal")
public final class CommandRewardGameTests {
    @GameTest(template = "empty", batch = "commandRewards")
    @PrefixGameTestTemplate(false)
    public static void nativeExecutionPreflightAndNoReplay(GameTestHelper helper) throws Exception {
        var player = helper.makeMockServerPlayerInLevel();
        var reward = reward("success", "experience add @s 3 points");
        var quest = install(reward);
        var engine = ProgressEngine.get();
        var progress = engine.progress(player);
        var beforeState = yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi.getRewardClaimState(player, reward.id().toString()).orElseThrow();
        helper.assertTrue(!beforeState.eligible(), "Read-only identity is available before completion without authorizing a claim");
        helper.assertTrue(journalAbsent(player, beforeState.context()), "Identity lookup must not start an execution attempt");
        progress.status(quest.id().toString(), QuestStatus.COMPLETED);
        helper.assertTrue(yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi.getRewardClaimState(player, reward.id().toString()).orElseThrow().eligible(),
                "Completed personal quest is eligible; the transaction still owns final checks");
        var beforeClaim = QuestProgressData.get(player.server).save(new net.minecraft.nbt.CompoundTag(), player.registryAccess());
        int before = player.totalExperience;
        var first = engine.claim(player, reward.id());
        helper.assertTrue(first.success(), first.code() + ": " + first.message());
        helper.assertTrue(player.totalExperience == before + 3, "native @s targets the non-op recipient with explicit source permission");
        helper.assertTrue(!player.hasPermissions(2), "reward must not grant operator permission");
        engine.claim(player, reward.id());
        helper.assertTrue(player.totalExperience == before + 3, "duplicate claim never executes again");
        // Simulate a stale SavedData snapshot while the forced receipt survives.
        player.server.overworld().getDataStorage().set("brnquest_progress", QuestProgressData.load(beforeClaim, player.registryAccess()));
        progress = engine.progress(player);
        helper.assertTrue(engine.claim(player, reward.id()).success(), "durable outcome reconciles a missing progress receipt");
        helper.assertTrue(player.totalExperience == before + 3, "reconciliation does not replay the command");

        var denied = new RewardDefinition(id("book"), id("player_permission"), RewardTypes.COMMAND,
                Map.of("command", "experience add @s 50 points", "source_mode", "player"), "manual", false);
        quest = install(denied);
        progress.status(quest.id().toString(), QuestStatus.COMPLETED);
        helper.assertTrue(!engine.claim(player, denied.id()).success(), "player mode cannot borrow explicit permissions");
        helper.assertTrue(!progress.isClaimed(denied.id().toString()), "preflight rejection does not consume reward");
        helper.assertTrue(CommandRewardService.journal(player).read(CommandRewardService.key(yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi.getRewardClaimState(player, denied.id().toString()).orElseThrow().context())) == null,
                "preflight never starts an execution attempt");
        var excessive = new RewardDefinition(id("book"), id("ceiling"), RewardTypes.COMMAND,
                Map.of("command", "say prohibited", "permission_level", "4"), "manual", false);
        quest = install(excessive); progress.status(quest.id().toString(), QuestStatus.COMPLETED);
        helper.assertTrue(!engine.claim(player, excessive.id()).success(), "server ceiling rejects elevated reward configuration");
        if (net.neoforged.fml.ModList.get().isLoaded("brnquest_example")) {
            var functionReward = reward("function", "function brnquest_example:reward_experience");
            quest = install(functionReward); progress.status(quest.id().toString(), QuestStatus.COMPLETED);
            var functionResult = engine.claim(player, functionReward.id());
            helper.assertTrue(functionResult.success(), functionResult.message());
            helper.assertTrue(player.totalExperience == before + 7, "native function executes against the reward recipient");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "commandRewardReset")
    @PrefixGameTestTemplate(false)
    public static void explicitResetCreatesANewClaimWhileReloadKeepsIt(GameTestHelper helper) throws Exception {
        var player = helper.makeMockServerPlayerInLevel();
        var reward = reward("reset", "experience add @s 3 points");
        var quest = install(reward);
        var engine = ProgressEngine.get();
        engine.forceComplete(player, quest.id());
        int before = player.totalExperience;
        helper.assertTrue(engine.claim(player, reward.id()).changed(), "first command claim succeeds");
        String previousKey = CommandRewardService.key(yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi.getRewardClaimState(player, reward.id().toString()).orElseThrow().context());
        for (int round = 1; round <= 2; round++) {
            engine.reset(player, quest.id());
            engine.forceComplete(player, quest.id());
            String nextKey = CommandRewardService.key(yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi.getRewardClaimState(player, reward.id().toString()).orElseThrow().context());
            helper.assertTrue(!previousKey.equals(nextKey), "explicit reset creates fresh command identity");
            helper.assertTrue(CommandRewardService.journal(player).read(previousKey) != null, "old receipts remain available");
            // Reload saved owner state before claiming to verify the reset identity survives a restart.
            var saved = QuestProgressData.get(player.server).save(new net.minecraft.nbt.CompoundTag(), player.registryAccess());
            player.server.overworld().getDataStorage().set("brnquest_progress", QuestProgressData.load(saved, player.registryAccess()));
            helper.assertTrue(nextKey.equals(CommandRewardService.key(yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi.getRewardClaimState(player, reward.id().toString()).orElseThrow().context())), "reload preserves identity");
            helper.assertTrue(engine.claim(player, reward.id()).changed(), "new round executes successfully");
            helper.assertTrue(player.totalExperience == before + 3 * (round + 1), "each reset permits one new command effect");
            helper.assertTrue(!engine.claim(player, reward.id()).changed(), "same-round duplicate remains blocked");
            previousKey = nextKey;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "commandRewardFailure")
    @PrefixGameTestTemplate(false)
    public static void runtimeFailureRequiresExplicitAcknowledgement(GameTestHelper helper) throws Exception {
        var player = helper.makeMockServerPlayerInLevel();
        AtomicInteger effects = new AtomicInteger();
        player.server.getCommands().getDispatcher().register(Commands.literal("brnquest_f1_test_failure").requires(s -> s.hasPermission(2))
                .executes(ctx -> { effects.incrementAndGet(); throw new SimpleCommandExceptionType(Component.literal("after effect")).create(); }));
        var reward = reward("failure", "brnquest_f1_test_failure");
        var quest = install(reward);
        var engine = ProgressEngine.get(); var progress = engine.progress(player);
        progress.status(quest.id().toString(), QuestStatus.COMPLETED);
        helper.assertTrue(!engine.claim(player, reward.id()).success(), "runtime failure is returned");
        helper.assertTrue(effects.get() == 1, "command can fail after producing a side effect");
        helper.assertTrue(!engine.claim(player, reward.id()).success() && effects.get() == 1, "failed execution cannot automatically retry");
        String key = CommandRewardService.key(yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi.getRewardClaimState(player, reward.id().toString()).orElseThrow().context());
        var journal = CommandRewardService.journal(player); var receipt = journal.read(key);
        helper.assertTrue(receipt != null, "failure keeps a durable intent");
        helper.assertTrue(!progress.isClaimed(reward.id().toString()), "an uncertain command does not satisfy the quest reward barrier");
        String acknowledge = "brnquest command_reward acknowledge @s \"" + reward.id() + "\" " + receipt.intent().attempt();
        var denied = player.server.getCommands().getDispatcher().parse(acknowledge, player.createCommandSourceStack().withPermission(2));
        helper.assertTrue(Commands.getParseException(denied) != null, "ordinary author permission cannot acknowledge uncertain effects");
        player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), acknowledge);
        helper.assertTrue(progress.isClaimed(reward.id().toString()), "administrator command reconciles consumption");
        helper.assertTrue(engine.claim(player, reward.id()).success(), "acknowledged claim stays idempotent");
        helper.assertTrue(effects.get() == 1, "acknowledgement does not repeat the effect");
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "commandRewardQueued")
    @PrefixGameTestTemplate(false)
    public static void queuedCommandReconcilesAfterNativeContextCompletes(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var reward = reward("queued", "experience add @s 2 points");
        var quest = install(reward);
        var engine = ProgressEngine.get(); var progress = engine.progress(player);
        progress.status(quest.id().toString(), QuestStatus.COMPLETED);
        player.server.getCommands().getDispatcher().register(Commands.literal("brnquest_f1_test_queue")
                .executes(ctx -> { engine.claim(player, reward.id()); return 1; }));
        int before = player.totalExperience;
        player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "brnquest_f1_test_queue");
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(player.totalExperience == before + 2, "queued native command executes exactly once");
            helper.assertTrue(progress.status(quest.id().toString()) == QuestStatus.REWARD_CLAIMED,
                    "deferred callback reconciles the root reward automatically");
            engine.claim(player, reward.id());
            helper.assertTrue(player.totalExperience == before + 2, "late reconciliation does not reopen the attempt");
            helper.succeed();
        });
    }

    private static RewardDefinition reward(String path, String command) {
        return new RewardDefinition(id("book"), id(path), RewardTypes.COMMAND, Map.of("command", command), "manual", false);
    }
    private static QuestDefinition install(RewardDefinition reward) {
        var quest = new QuestDefinition(id("book"), id("quest_" + reward.id().getPath()), id("chapter"), "Command", "", "", "", 0, 0,
                List.of(), List.of(), List.of(reward), "");
        var book = new QuestBookDefinition(id("book"), 1, "Commands", List.of(new ChapterGroupDefinition(id("book"), id("group"), "Group", 0)),
                List.of(new ChapterDefinition(id("book"), id("chapter"), id("group"), "Chapter", "", 0, List.of(quest))), Map.of());
        if (!QuestBookManager.get().install(book, new DiagnosticReport())) throw new IllegalStateException("Command fixture rejected");
        return quest;
    }
    private static ResourceLocation id(String path) { return ResourceLocation.parse("brnquest_f1:" + path); }
    private static boolean journalAbsent(net.minecraft.server.level.ServerPlayer player,
            yourscraft.jasdewstarfield.brnquest.reward.RewardClaimContext context) throws Exception {
        return CommandRewardService.journal(player).read(CommandRewardService.key(context)) == null;
    }

}
