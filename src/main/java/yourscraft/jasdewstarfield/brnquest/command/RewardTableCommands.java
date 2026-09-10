package yourscraft.jasdewstarfield.brnquest.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerService;
import yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.reward.table.*;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;

/** Minimal recovery commands expose immutable attempt identities before a human consumes uncertain effects. */
final class RewardTableCommands {
    private RewardTableCommands() {}
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("reward_table").requires(s -> s.hasPermission(2))
                .then(Commands.literal("status").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("reward", StringArgumentType.string()).executes(c -> run(c, "status")))))
                .then(Commands.literal("acknowledge").requires(s -> s.hasPermission(4))
                        .then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("reward", StringArgumentType.string())
                                .then(Commands.argument("attempt", StringArgumentType.word()).then(Commands.argument("occurrence", StringArgumentType.string())
                                        .executes(c -> run(c, "acknowledge")))))))
                .then(Commands.literal("retry").requires(s -> s.hasPermission(4))
                        .then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("reward", StringArgumentType.string())
                                .then(Commands.argument("attempt", StringArgumentType.word()).then(Commands.argument("occurrence", StringArgumentType.string())
                                        .executes(c -> run(c, "retry")))))));
    }
    private static int run(CommandContext<CommandSourceStack> command, String action) {
        try {
            var player = EntityArgument.getPlayer(command, "player");
            var reward = BrnQuestApi.getReward(StringArgumentType.getString(command, "reward")).orElseThrow();
            if (!reward.typeId().equals(RewardTableReward.ID) && !reward.typeId().equals(LootTableReward.ID)) throw new IllegalArgumentException("Reward does not use the table coordinator");
            var quest = QuestBookManager.get().active().orElseThrow().book().quests().stream()
                    .filter(q -> q.rewards().stream().anyMatch(r -> r.id().equals(reward.id()))).findFirst().orElseThrow();
            var progress = ProgressEngine.get().progress(player);
            var context = new RewardClaimContext(new RewardContext(player, quest.bookId(), quest.id(), reward), ProgressOwnerService.require(player),
                    progress.completionCycles(quest.id().toString()), progress.claimGeneration(quest.id().toString()));
            String key = RewardTableService.key(context);
            var journal = RewardTableService.journal(player);
            if (!action.equals("status")) {
                if (action.equals("retry")) RewardTableService.retryNoEffect(context, StringArgumentType.getString(command,"attempt"),
                        StringArgumentType.getString(command,"occurrence"), command.getSource().getTextName());
                else journal.acknowledge(key, StringArgumentType.getString(command, "attempt"), StringArgumentType.getString(command, "occurrence"), command.getSource().getTextName());
                ProgressEngine.get().claim(player, reward.id());
            }
            var attempt = journal.read(key);
            command.getSource().sendSuccess(() -> Component.literal(attempt == null ? "No reward table attempt" :
                    "root=" + reward.id() + " cycle=" + context.completionCycle() + " attempt=" + attempt.attemptId()
                            + " executor=" + attempt.executor() + " state=" + attempt.state() + " changed=" + attempt.updatedAt() + " " + attempt.detail()), false);
            if (attempt != null && RewardTablePlan.pending(attempt)!=null) {
                var choice=RewardTablePlan.pending(attempt);
                command.getSource().sendSuccess(() -> Component.literal("pending choice="+choice.occurrence()+" version="+choice.version()+" candidates="+choice.entries().size()),false);
            }
            if (attempt != null) for (var leaf : attempt.leaves()) command.getSource().sendSuccess(() -> Component.literal(
                    leaf.path() + " occurrence=" + leaf.occurrence() + " state=" + leaf.state() + " " + leaf.detail()), false);
            return 1;
        } catch (Exception error) { command.getSource().sendFailure(Component.literal(java.util.Objects.toString(error.getMessage(), "Invalid reward table request"))); return 0; }
    }
}
