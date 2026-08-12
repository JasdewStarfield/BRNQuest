package yourscraft.jasdewstarfield.brnquest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi;
import yourscraft.jasdewstarfield.brnquest.compat.ftb.FtbImportService;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;

/** Server commands delegate to the same APIs used by integrations and network handlers. */
public final class BrnQuestCommands {
    private BrnQuestCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("brnquest")
                .then(Commands.literal("open").executes(ctx -> open(ctx, ""))
                        .then(Commands.argument("quest", StringArgumentType.string()).executes(ctx -> open(ctx, StringArgumentType.getString(ctx, "quest")))))
                .then(Commands.literal("progress").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("get").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("quest", StringArgumentType.string()).executes(BrnQuestCommands::progressGet))))
                        .then(Commands.literal("complete").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("quest", StringArgumentType.string()).executes(BrnQuestCommands::progressComplete))))
                        .then(Commands.literal("reset").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("quest", StringArgumentType.string()).executes(BrnQuestCommands::progressReset)))))
                .then(Commands.literal("reward").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("claim").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("reward", StringArgumentType.string()).executes(BrnQuestCommands::rewardClaim)))))
                .then(Commands.literal("validate").requires(s -> s.hasPermission(2)).executes(BrnQuestCommands::validate))
                .then(Commands.literal("diagnose").requires(s -> s.hasPermission(2)).executes(BrnQuestCommands::diagnose))
                .then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(ctx -> {
                    ctx.getSource().getServer().reloadResources(ctx.getSource().getServer().getPackRepository().getSelectedIds());
                    ctx.getSource().sendSuccess(() -> Component.literal("BRNQuest reload requested"), true);
                    return 1;
                }))
                .then(Commands.literal("import_ftb").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("source", StringArgumentType.word())
                                .then(Commands.argument("namespace", StringArgumentType.word())
                                        .executes(ctx -> importFtb(ctx, "main", false))
                                        .then(Commands.literal("--dry-run").executes(ctx -> importFtb(ctx, "main", true)))
                                        .then(Commands.argument("book_id", StringArgumentType.word())
                                                .executes(ctx -> importFtb(ctx, StringArgumentType.getString(ctx, "book_id"), false))
                                                .then(Commands.literal("--dry-run").executes(ctx -> importFtb(ctx, StringArgumentType.getString(ctx, "book_id"), true))))))));
    }

    private static int open(CommandContext<CommandSourceStack> context, String quest) {
        try { return BrnQuestApi.openQuestScreen(context.getSource().getPlayerOrException(), quest) ? 1 : 0; }
        catch (Exception exception) { context.getSource().sendFailure(Component.literal(exception.getMessage())); return 0; }
    }

    private static int validate(CommandContext<CommandSourceStack> context) {
        var snapshot = QuestBookManager.get().active();
        context.getSource().sendSuccess(() -> Component.literal(snapshot.map(s -> "BRNQuest valid: " + s.book().quests().size() + " quests, revision " + s.revision()).orElse("No active book")), false);
        return snapshot.isPresent() ? 1 : 0;
    }

    private static int progressGet(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = EntityArgument.getPlayer(context, "player");
        String quest = StringArgumentType.getString(context, "quest");
        var view = BrnQuestApi.getProgress(player, quest);
        context.getSource().sendSuccess(() -> Component.literal(view.map(Object::toString).orElse("Quest not found")), false);
        return view.isPresent() ? 1 : 0;
    }

    private static int progressComplete(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = EntityArgument.getPlayer(context, "player");
        String quest = StringArgumentType.getString(context, "quest");
        var result = BrnQuestApi.completeQuestResult(player, quest);
        audit(context, "complete", player.getScoreboardName(), quest, result.message());
        return result.success() ? 1 : 0;
    }

    private static int progressReset(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = EntityArgument.getPlayer(context, "player");
        String quest = StringArgumentType.getString(context, "quest");
        var id = BrnQuestApi.getQuest(quest).map(view -> view.id()).orElse(null);
        if (id == null) return 0;
        yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine.get().reset(player, id);
        audit(context, "reset", player.getScoreboardName(), quest, "reset");
        return 1;
    }

    private static int rewardClaim(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = EntityArgument.getPlayer(context, "player");
        String reward = StringArgumentType.getString(context, "reward");
        boolean success = BrnQuestApi.claimReward(player, reward);
        audit(context, "claim", player.getScoreboardName(), reward, success ? "success" : "failure");
        return success ? 1 : 0;
    }

    private static void audit(CommandContext<CommandSourceStack> context, String action, String player, String object, String result) {
        yourscraft.jasdewstarfield.brnquest.BRNQuest.LOGGER.info("[BRNQuest/AUDIT] actor={} action={} player={} object={} result={}", context.getSource().getTextName(), action, player, object, result);
    }

    private static int diagnose(CommandContext<CommandSourceStack> context) {
        String json = QuestBookManager.get().lastReport().toJson();
        context.getSource().sendSuccess(() -> Component.literal(json), false);
        return QuestBookManager.get().lastReport().hasFatal() ? 0 : 1;
    }

    private static int importFtb(CommandContext<CommandSourceStack> context, String bookId, boolean dryRun) {
        String source = StringArgumentType.getString(context, "source");
        String namespace = StringArgumentType.getString(context, "namespace");
        try {
            var execution = new FtbImportService().execute(context.getSource().getServer(), source, namespace, bookId, dryRun);
            var result = execution.result();
            long problems = result.report().diagnostics().stream().filter(diagnostic -> diagnostic.severity().ordinal() >= yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic.Severity.ERROR.ordinal()).count();
            context.getSource().sendSuccess(() -> Component.literal("BRNQuest import " + (dryRun ? "dry-run" : "completed") + ": " + result.chapterCount() + " chapters, " + result.questCount() + " quests, " + problems + " errors")
                    .withStyle(problems == 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
            yourscraft.jasdewstarfield.brnquest.BRNQuest.LOGGER.info("[BRNQuest/AUDIT] actor={} action=import_ftb source={} namespace={} book={} dryRun={} chapters={} quests={} problems={}",
                    context.getSource().getTextName(), source, namespace, bookId, dryRun, result.chapterCount(), result.questCount(), problems);
            return result.report().hasFatal() ? 0 : result.questCount();
        } catch (Exception exception) {
            yourscraft.jasdewstarfield.brnquest.BRNQuest.LOGGER.warn("[BRNQuest/AUDIT] actor={} action=import_ftb source={} namespace={} book={} dryRun={} result=failure message={}",
                    context.getSource().getTextName(), source, namespace, bookId, dryRun, exception.getMessage());
            context.getSource().sendFailure(Component.literal("BRNQuest import failed: " + exception.getMessage()));
            return 0;
        }
    }
}
