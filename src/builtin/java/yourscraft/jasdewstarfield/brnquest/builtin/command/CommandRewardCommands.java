package yourscraft.jasdewstarfield.brnquest.builtin.command;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.*;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.CommandRewardService;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypes;

/** Explicit read/reconcile tools; acknowledgement never retries an arbitrary command. */
@net.neoforged.fml.common.EventBusSubscriber(modid = "brnquest")
public final class CommandRewardCommands {
    @net.neoforged.bus.api.SubscribeEvent
    public static void register(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        // Register only this module's subcommand; the core owns the shared root and ordinary commands.
        event.getDispatcher().register(Commands.literal("brnquest").then(build()));
    }
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
            if (!view.typeId().equals(RewardTypes.COMMAND)) throw new IllegalArgumentException("Not a command reward");
            var context = BrnQuestApi.getRewardClaimState(player, view.id().toString()).orElseThrow().context();
            var reward = context.rewardContext().reward();
            String key = CommandRewardService.key(context);
            var journal = CommandRewardService.journal(player);
            if (acknowledge) {
                journal.acknowledge(key, StringArgumentType.getString(ctx, "attempt"), ctx.getSource().getTextName());
                // Reconcile the consumed reward through the same eligibility/owner gates, without executing it again.
                var result = BrnQuestApi.claimRewardResult(yourscraft.jasdewstarfield.brnquest.api.OperationContext.self(player), player, reward.id().toString());
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
