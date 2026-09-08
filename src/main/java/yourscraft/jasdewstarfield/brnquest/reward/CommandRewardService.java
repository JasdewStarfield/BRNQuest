package yourscraft.jasdewstarfield.brnquest.reward;

import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestServerConfig;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerService;
import yourscraft.jasdewstarfield.brnquest.progress.PlayerProgress;
import java.io.IOException;

/** Small command-only boundary: preflight, forced intent, native execution and durable observations. */
public final class CommandRewardService {
    private CommandRewardService() {}
    public record Prepared(CommandRewardConfig config, String command, CommandSourceStack source, net.minecraft.resources.ResourceLocation rewardId) {}
    public static CommandRewardJournal journal(ServerPlayer player) {
        return new CommandRewardJournal(player.server.getWorldPath(LevelResource.ROOT).resolve("data/brnquest-command-rewards"));
    }
    public static String key(ServerPlayer player, QuestDefinition quest, RewardDefinition reward, PlayerProgress progress) {
        return key(ProgressOwnerService.require(player), player.getUUID(), quest, reward, progress.completionCycles(quest.id().toString()));
    }
    /** Shared receipts exclude the claimant; individual rewards retain it even inside a shared owner. */
    public static String key(yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerId owner, java.util.UUID player,
                             QuestDefinition quest, RewardDefinition reward, int cycle) {
        // Revision is deliberately excluded: publishing an edit must not reopen the same earned reward.
        return owner.providerId() + "/" + owner.ownerId() + "/" + quest.bookId() + "/" + reward.id()
                + "/" + cycle + "/" + (reward.teamReward() ? "shared" : player);
    }
    public static Prepared prepare(ServerPlayer player, RewardDefinition reward) throws Exception {
        CommandRewardConfig config = CommandRewardConfig.decode(reward.config()).getOrThrow();
        int ceiling = BrnQuestServerConfig.commandPermissionLimit();
        if (config.sourceMode().equals("explicit") && config.permissionLevel() > ceiling)
            throw new IllegalArgumentException("Command permission exceeds the server ceiling");
        int level = config.permissionLevel();
        if (config.sourceMode().equals("player")) {
            level = 0;
            while (level < ceiling && player.hasPermissions(level + 1)) level++;
        }
        var source = player.createCommandSourceStack().withPermission(level);
        if (config.silent()) source = source.withSuppressedOutput();
        var pos = player.blockPosition();
        String command = config.expand(player.getGameProfile().getName(), pos.getX(), pos.getY(), pos.getZ());
        ParseResults<CommandSourceStack> parse = player.server.getCommands().getDispatcher().parse(command, source);
        var error = Commands.getParseException(parse);
        if (error != null) throw error;
        if (parse.getContext().getLastChild().getCommand() == null) throw new IllegalArgumentException("Incomplete command");
        return new Prepared(config, command, source, reward.id());
    }
    private static void reconcile(ServerPlayer player, String expectedKey, net.minecraft.resources.ResourceLocation rewardId, String feedback) {
        try {
            var snapshot = yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager.get().active().orElse(null);
            if (snapshot == null) return;
            var engine = yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine.get();
            for (var quest : snapshot.book().quests()) for (var reward : quest.rewards()) {
                if (!reward.id().equals(rewardId) || !reward.typeId().equals(RewardTypes.COMMAND)) continue;
                if (!expectedKey.equals(key(player, quest, reward, engine.progress(player)))) return;
                var receipt = journal(player).read(expectedKey);
                if (receipt != null && receipt.outcome().state().equals("REPORTED_SUCCESS")) {
                    var result = engine.claim(player, rewardId);
                    if (result.changed() && !feedback.isBlank()) player.displayClientMessage(net.minecraft.network.chat.Component.literal(feedback), false);
                }
                return;
            }
        } catch (Exception error) { BRNQuest.LOGGER.error("Command receipt reconciliation failed for {}", expectedKey, error); }
    }

    public static RewardResult execute(ServerPlayer player, String key, Prepared prepared) {
        var journal = journal(player);
        int[] callbacks = {0, 0, 0};
        var source = prepared.source().withCallback((success, result) -> {
            callbacks[0]++; callbacks[1] += result; if (!success) callbacks[2]++;
            try {
                journal.outcome(key, new CommandRewardJournal.Outcome(callbacks[2] == 0 ? "REPORTED_SUCCESS" : "REPORTED_FAILURE",
                        callbacks[0], callbacks[1], "Native command callback; no rollback or automatic replay"));
            } catch (IOException error) { BRNQuest.LOGGER.error("Command outcome could not be recorded for {}", key, error); }
            // Commands invoked inside another command context may run after performCommand returns.
            // Reconcile later, never recursively from the callback while the outer claim is still active.
            player.server.tell(new net.minecraft.server.TickTask(player.server.getTickCount(), () -> reconcile(player, key, prepared.rewardId(), prepared.config().feedback())));
        });
        try {
            // The Minecraft execution context supports /function and /execute; dispatcher.execute alone does not.
            player.server.getCommands().performCommand(player.server.getCommands().getDispatcher().parse(prepared.command(), source), prepared.command());
            if (callbacks[2] > 0) return RewardResult.failure("Command reported failure; attempt retained, no automatic retry");
            if (callbacks[0] == 0) return RewardResult.failure("Command queued or result unknown; inspect command receipt before recovery");
            return RewardResult.success("Command executed; result=" + callbacks[1]);
        } catch (Exception error) {
            BRNQuest.LOGGER.error("Command reward attempt has an uncertain result for {}", key, error);
            return RewardResult.failure("Command result unknown; attempt retained");
        }
    }
}
