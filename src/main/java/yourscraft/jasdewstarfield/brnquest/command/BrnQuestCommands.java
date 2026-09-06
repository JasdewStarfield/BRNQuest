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
import yourscraft.jasdewstarfield.brnquest.api.OperationContext;
import yourscraft.jasdewstarfield.brnquest.compat.ftb.FtbImportService;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import yourscraft.jasdewstarfield.brnquest.workspace.WorkspaceDeploymentService;
import yourscraft.jasdewstarfield.brnquest.workspace.WorkspacePaths;

/** Server commands delegate to the same APIs used by integrations and network handlers. */
public final class BrnQuestCommands {
    private BrnQuestCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("brnquest")
                .then(AuthorCommands.build())
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
                .then(Commands.literal("workspace").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("deploy")
                                .executes(ctx -> deployWorkspace(ctx, false))
                                .then(Commands.literal("--replace").executes(ctx -> deployWorkspace(ctx, true))))
                        .then(Commands.literal("reload").executes(BrnQuestCommands::reloadWorkspace))
                        .then(Commands.literal("import_ftb")
                                .then(Commands.argument("source", StringArgumentType.word())
                                        .then(Commands.argument("namespace", StringArgumentType.word())
                                                .executes(ctx -> importFtb(ctx, "main", false))
                                                .then(Commands.literal("--dry-run").executes(ctx -> importFtb(ctx, "main", true)))
                                                .then(Commands.argument("book_id", StringArgumentType.word())
                                                        .executes(ctx -> importFtb(ctx, StringArgumentType.getString(ctx, "book_id"), false))
                                                        .then(Commands.literal("--dry-run").executes(ctx -> importFtb(ctx, StringArgumentType.getString(ctx, "book_id"), true))))))))
                .then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(ctx -> {
                    new WorkspaceDeploymentService().reloadSelected(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.translatable("command.brnquest.reload.requested"), true);
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
        catch (Exception exception) {
            context.getSource().sendFailure(Component.translatable("command.brnquest.open.failed"));
            return 0;
        }
    }

    private static int validate(CommandContext<CommandSourceStack> context) {
        var snapshot = QuestBookManager.get().active();
        context.getSource().sendSuccess(() -> snapshot.<Component>map(s -> Component.translatable(
                "command.brnquest.validate.active", s.book().quests().size(), s.revision()))
                .orElseGet(() -> Component.translatable("command.brnquest.validate.no_active")), false);
        return snapshot.isPresent() ? 1 : 0;
    }

    private static int progressGet(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = EntityArgument.getPlayer(context, "player");
        String quest = StringArgumentType.getString(context, "quest");
        var view = BrnQuestApi.getProgress(player, quest);
        context.getSource().sendSuccess(() -> view.<Component>map(value -> Component.translatable(
                "command.brnquest.progress.found", value.toString()))
                .orElseGet(() -> Component.translatable("command.brnquest.progress.not_found")), false);
        return view.isPresent() ? 1 : 0;
    }

    private static int progressComplete(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = EntityArgument.getPlayer(context, "player");
        String quest = StringArgumentType.getString(context, "quest");
        var result = yourscraft.jasdewstarfield.brnquest.progress.AdminProgressService.get().command(
                context.getSource(), player, quest,
                yourscraft.jasdewstarfield.brnquest.progress.AdminProgressAction.FORCE_QUEST);
        if (!result.success()) context.getSource().sendFailure(Component.translatable(
                "command.brnquest.progress.failed", result.code()));
        return result.success() ? 1 : 0;
    }

    private static int progressReset(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = EntityArgument.getPlayer(context, "player");
        String quest = StringArgumentType.getString(context, "quest");
        var result = yourscraft.jasdewstarfield.brnquest.progress.AdminProgressService.get().command(
                context.getSource(), player, quest,
                yourscraft.jasdewstarfield.brnquest.progress.AdminProgressAction.RESET_QUEST);
        if (!result.success()) context.getSource().sendFailure(Component.translatable(
                "command.brnquest.progress.failed", result.code()));
        return result.success() ? 1 : 0;
    }

    private static int rewardClaim(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = EntityArgument.getPlayer(context, "player");
        String reward = StringArgumentType.getString(context, "reward");
        var actor = OperationContext.administrator(context.getSource()).orElseThrow();
        var result = BrnQuestApi.claimRewardResult(actor, player, reward);
        return result.success() ? 1 : 0;
    }

    private static void audit(CommandContext<CommandSourceStack> context, String action, String player, String object, String result) {
        yourscraft.jasdewstarfield.brnquest.BRNQuest.LOGGER.debug("[BRNQuest/AUDIT] actor={} action={} player={} object={} result={}", context.getSource().getTextName(), action, player, object, result);
    }

    private static int diagnose(CommandContext<CommandSourceStack> context) {
        String json = QuestBookManager.get().lastReport().toJson();
        context.getSource().sendSuccess(() -> Component.literal(json), false);
        return QuestBookManager.get().lastReport().hasFatal() ? 0 : 1;
    }

    private static int deployWorkspace(CommandContext<CommandSourceStack> context, boolean replace) {
        try {
            var service = new WorkspaceDeploymentService();
            var result = service.deploy(context.getSource().getServer(), replace);
            if (result.status() == WorkspaceDeploymentService.Status.ALREADY_DEPLOYED) {
                context.getSource().sendFailure(Component.translatable("command.brnquest.workspace.already_deployed"));
                return 0;
            }
            Component backup = result.backup() == null ? Component.empty()
                    : Component.translatable("command.brnquest.workspace.backup", result.backup().toString());
            context.getSource().sendSuccess(() -> Component.translatable("command.brnquest.workspace.deployed",
                    result.files(), backup), true);
            audit(context, "workspace_deploy", context.getSource().getTextName(), WorkspacePaths.workspace(context.getSource().getServer()).toString(), result.status().name());
            return 1;
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.translatable("command.brnquest.workspace.failed"));
            return 0;
        }
    }

    private static int reloadWorkspace(CommandContext<CommandSourceStack> context) {
        new WorkspaceDeploymentService().reloadIncludingWorkspace(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.translatable("command.brnquest.workspace.reload_requested"), true);
        return 1;
    }

    private static int importFtb(CommandContext<CommandSourceStack> context, String bookId, boolean dryRun) {
        String source = StringArgumentType.getString(context, "source");
        String namespace = StringArgumentType.getString(context, "namespace");
        try {
            var execution = new FtbImportService().execute(context.getSource().getServer(), source, namespace, bookId, dryRun);
            var result = execution.result();
            long errors = result.report().diagnostics().stream().filter(diagnostic -> diagnostic.severity().ordinal() >= yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic.Severity.ERROR.ordinal()).count();
            long warnings = result.report().diagnostics().stream().filter(diagnostic -> diagnostic.severity() == yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic.Severity.WARN).count();
            if (!dryRun && execution.draftResult() != null && !execution.draftResult().success()) {
                context.getSource().sendFailure(Component.translatable("command.brnquest.import.failed_code",
                        execution.draftResult().code()));
                return 0;
            }
            context.getSource().sendSuccess(() -> Component.translatable(dryRun
                            ? "command.brnquest.import.summary_dry_run" : "command.brnquest.import.summary",
                            result.chapterCount(), result.questCount(), errors, warnings)
                    .withStyle(errors == 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
            yourscraft.jasdewstarfield.brnquest.BRNQuest.LOGGER.debug("[BRNQuest/AUDIT] actor={} action=import_ftb source={} namespace={} book={} dryRun={} chapters={} quests={} errors={} warnings={}",
                    context.getSource().getTextName(), source, namespace, bookId, dryRun, result.chapterCount(), result.questCount(), errors, warnings);
            return result.report().hasFatal() ? 0 : result.questCount();
        } catch (Exception exception) {
            yourscraft.jasdewstarfield.brnquest.BRNQuest.LOGGER.warn("[BRNQuest/AUDIT] actor={} action=import_ftb source={} namespace={} book={} dryRun={} result=failure message={}",
                    context.getSource().getTextName(), source, namespace, bookId, dryRun, exception.getMessage());
            context.getSource().sendFailure(Component.translatable("command.brnquest.import.failed"));
            return 0;
        }
    }
}
