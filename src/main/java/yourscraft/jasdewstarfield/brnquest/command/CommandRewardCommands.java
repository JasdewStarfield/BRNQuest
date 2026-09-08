package yourscraft.jasdewstarfield.brnquest.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi;
import yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine;
import yourscraft.jasdewstarfield.brnquest.reward.CommandRewardService;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypes;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;

/** Explicit read/reconcile tools; acknowledgement never retries an arbitrary command. */
final class CommandRewardCommands {
    private CommandRewardCommands() {}
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("command_reward").requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("reward", StringArgumentType.string()).executes(ctx -> run(ctx, false)))))
                .then(Commands.literal("acknowledge").requires(source -> source.hasPermission(4))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("reward", StringArgumentType.string())
                                        .then(Commands.argument("attempt", StringArgumentType.word()).executes(ctx -> run(ctx, true))))));
    }
    private static int run(CommandContext<CommandSourceStack> ctx, boolean acknowledge) {
        try {
            var player = EntityArgument.getPlayer(ctx, "player");
            var view = BrnQuestApi.getReward(StringArgumentType.getString(ctx, "reward")).orElseThrow();
            var snapshot = QuestBookManager.get().active().orElseThrow();
            var quest = snapshot.book().quests().stream().filter(q -> q.rewards().stream().anyMatch(r -> r.id().equals(view.id()))).findFirst().orElseThrow();
            var reward = quest.rewards().stream().filter(r -> r.id().equals(view.id())).findFirst().orElseThrow();
            if (!reward.typeId().equals(RewardTypes.COMMAND)) throw new IllegalArgumentException("Not a command reward");
            String key = CommandRewardService.key(player, quest, reward, ProgressEngine.get().progress(player));
            var journal = CommandRewardService.journal(player);
            if (acknowledge) {
                journal.acknowledge(key, StringArgumentType.getString(ctx, "attempt"), ctx.getSource().getTextName());
                // Reconcile the consumed reward through the same eligibility/owner gates, without executing it again.
                var result = ProgressEngine.get().claim(player, reward.id());
                ctx.getSource().sendSuccess(() -> Component.translatable("command.brnquest.command_reward.acknowledged", result.code()), true);
            } else {
                var receipt = journal.read(key);
                ctx.getSource().sendSuccess(() -> receipt == null
                        ? Component.translatable("command.brnquest.command_reward.absent")
                        : Component.translatable("command.brnquest.command_reward.status", receipt.outcome().state(),
                                receipt.intent().attempt(), receipt.outcome().callbacks(), receipt.outcome().result(), receipt.outcome().detail()), false);
            }
            return 1;
        } catch (Exception error) {
            ctx.getSource().sendFailure(Component.translatable("command.brnquest.command_reward.error",
                    error.getMessage() == null ? "Unknown reward or receipt" : error.getMessage()));
            return 0;
        }
    }
}
