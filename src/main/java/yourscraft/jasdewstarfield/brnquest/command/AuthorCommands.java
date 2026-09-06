package yourscraft.jasdewstarfield.brnquest.command;

import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.author.*;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.workspace.WorkspaceDeploymentService;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Permission-level-2 command facade over the same author services used by the future editor UI. */
final class AuthorCommands {
    private static final SimpleCommandExceptionType INVALID_CONFIG = new SimpleCommandExceptionType(
            Component.translatable("command.brnquest.author.invalid_config_json"));

    private AuthorCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("author").requires(source -> source.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.literal("active").executes(AuthorCommands::createActive))
                        .then(Commands.literal("empty")
                                .then(Commands.argument("book", ResourceLocationArgument.id())
                                        .then(Commands.argument("title", StringArgumentType.greedyString())
                                                .executes(AuthorCommands::createEmpty))))
                        .then(Commands.literal("workspace")
                                .then(Commands.argument("book", ResourceLocationArgument.id())
                                        .executes(AuthorCommands::createWorkspace))))
                .then(Commands.literal("open")
                        .then(Commands.argument("book", ResourceLocationArgument.id()).executes(AuthorCommands::open)))
                .then(Commands.literal("status")
                        .then(Commands.argument("book", ResourceLocationArgument.id()).executes(AuthorCommands::status)))
                .then(sessionCommand("renew", AuthorCommands::renew))
                .then(sessionCommand("close", AuthorCommands::close))
                .then(sessionCommand("discard", AuthorCommands::discard))
                .then(sessionBookRevisionCommand("validate", AuthorCommands::validate))
                .then(sessionBookRevisionCommand("save", AuthorCommands::save))
                .then(sessionBookRevisionCommand("publish", AuthorCommands::publish))
                .then(Commands.literal("diff")
                        .then(Commands.argument("session", StringArgumentType.word())
                                .then(Commands.argument("book", ResourceLocationArgument.id())
                                        .then(Commands.argument("revision", StringArgumentType.word())
                                                .then(Commands.argument("baseline", StringArgumentType.word())
                                                        .executes(AuthorCommands::diff))))))
                .then(Commands.literal("set_title")
                        .then(Commands.argument("session", StringArgumentType.word())
                                .then(Commands.argument("book", ResourceLocationArgument.id())
                                        .then(Commands.argument("revision", StringArgumentType.word())
                                                .then(Commands.argument("title", StringArgumentType.greedyString())
                                                        .executes(AuthorCommands::setTitle))))))
                .then(Commands.literal("add_group")
                        .then(Commands.argument("session", StringArgumentType.word())
                                .then(Commands.argument("book", ResourceLocationArgument.id())
                                        .then(Commands.argument("revision", StringArgumentType.word())
                                                .then(Commands.argument("group", ResourceLocationArgument.id())
                                                        .then(Commands.argument("order", IntegerArgumentType.integer())
                                                                .then(Commands.argument("title", StringArgumentType.greedyString())
                                                                        .executes(AuthorCommands::addGroup))))))))
                .then(Commands.literal("add_chapter")
                        .then(Commands.argument("session", StringArgumentType.word())
                                .then(Commands.argument("book", ResourceLocationArgument.id())
                                        .then(Commands.argument("revision", StringArgumentType.word())
                                                .then(Commands.argument("chapter", ResourceLocationArgument.id())
                                                        .then(Commands.argument("group", ResourceLocationArgument.id())
                                                                .then(Commands.argument("order", IntegerArgumentType.integer())
                                                                        .then(Commands.argument("icon", ResourceLocationArgument.id())
                                                                                .then(Commands.argument("title", StringArgumentType.greedyString())
                                                                                        .executes(AuthorCommands::addChapter))))))))))
                .then(Commands.literal("add_quest")
                        .then(Commands.argument("session", StringArgumentType.word())
                                .then(Commands.argument("book", ResourceLocationArgument.id())
                                        .then(Commands.argument("revision", StringArgumentType.word())
                                                .then(Commands.argument("quest", ResourceLocationArgument.id())
                                                        .then(Commands.argument("chapter", ResourceLocationArgument.id())
                                                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                                                .then(Commands.argument("icon", ResourceLocationArgument.id())
                                                                                        .then(Commands.argument("title", StringArgumentType.greedyString())
                                                                                                .executes(AuthorCommands::addQuest)))))))))))
                .then(Commands.literal("add_dependency")
                        .then(Commands.argument("session", StringArgumentType.word())
                                .then(Commands.argument("book", ResourceLocationArgument.id())
                                        .then(Commands.argument("revision", StringArgumentType.word())
                                                .then(Commands.argument("quest", ResourceLocationArgument.id())
                                                        .then(Commands.argument("dependency", ResourceLocationArgument.id())
                                                                .executes(AuthorCommands::addDependency)))))))
                .then(Commands.literal("add_task")
                        .then(Commands.argument("session", StringArgumentType.word())
                                .then(Commands.argument("book", ResourceLocationArgument.id())
                                        .then(Commands.argument("revision", StringArgumentType.word())
                                                .then(Commands.argument("quest", ResourceLocationArgument.id())
                                                        .then(Commands.argument("task", ResourceLocationArgument.id())
                                                                .then(Commands.argument("type", ResourceLocationArgument.id())
                                                                        .then(Commands.argument("optional", BoolArgumentType.bool())
                                                                                .then(Commands.argument("config", StringArgumentType.greedyString())
                                                                                        .executes(AuthorCommands::addTask))))))))))
                .then(Commands.literal("add_reward")
                        .then(Commands.argument("session", StringArgumentType.word())
                                .then(Commands.argument("book", ResourceLocationArgument.id())
                                        .then(Commands.argument("revision", StringArgumentType.word())
                                                .then(Commands.argument("quest", ResourceLocationArgument.id())
                                                        .then(Commands.argument("reward", ResourceLocationArgument.id())
                                                                .then(Commands.argument("type", ResourceLocationArgument.id())
                                                                        .then(Commands.argument("claim_policy", StringArgumentType.word())
                                                                                .then(Commands.argument("team", BoolArgumentType.bool())
                                                                                        .then(Commands.argument("config", StringArgumentType.greedyString())
                                                                                                .executes(AuthorCommands::addReward)))))))))))
                .then(Commands.literal("deploy")
                        .executes(context -> deploy(context, false))
                        .then(Commands.literal("--replace").executes(context -> deploy(context, true))))
                .then(Commands.literal("backups")
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .executes(AuthorCommands::listBackups)))
                .then(Commands.literal("restore_preview")
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .then(Commands.argument("backup", StringArgumentType.word())
                                        .executes(AuthorCommands::previewRestore))))
                .then(Commands.literal("restore")
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .then(Commands.argument("backup", StringArgumentType.word())
                                        .then(Commands.argument("current_revision", StringArgumentType.word())
                                                .executes(AuthorCommands::restore)))))
                .then(Commands.literal("reload").executes(AuthorCommands::reload));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sessionCommand(
            String name, com.mojang.brigadier.Command<CommandSourceStack> command) {
        return Commands.literal(name).then(Commands.argument("session", StringArgumentType.word())
                .then(Commands.argument("revision", StringArgumentType.word()).executes(command)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sessionBookRevisionCommand(
            String name, com.mojang.brigadier.Command<CommandSourceStack> command) {
        return Commands.literal(name).then(Commands.argument("session", StringArgumentType.word())
                .then(Commands.argument("book", ResourceLocationArgument.id())
                        .then(Commands.argument("revision", StringArgumentType.word()).executes(command))));
    }

    private static int createActive(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var result = new DraftService().createFromActive(player(context));
        return report(context, "draft_create_active", result.optionalValue().map(v -> v.book().id().toString()).orElse(""), result);
    }

    private static int createEmpty(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation book = book(context);
        var result = new DraftService().createEmpty(player(context), book,
                StringArgumentType.getString(context, "title"));
        return report(context, "draft_create_empty", book.toString(), result);
    }

    private static int createWorkspace(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation book = book(context);
        var result = new DraftService().createFromWorkspace(player(context), book);
        return report(context, "draft_create_workspace", book.toString(), result);
    }

    private static int open(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation book = book(context);
        AuthorOperationResult<DraftSnapshot> loaded = new DraftRepository().load(context.getSource().getServer(), book);
        if (!loaded.success()) return report(context, "session_open", book.toString(), loaded);
        var result = EditSessionService.get().open(player(context), loaded.value());
        if (result.success()) {
            var handle = result.value();
            context.getSource().sendSuccess(() -> Component.translatable("command.brnquest.author.session",
                    handle.sessionId().toString(), handle.session().draftRevision(), handle.session().savedRevision()), false);
        }
        return report(context, "session_open", book.toString(), result);
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation book = book(context);
        var result = EditSessionService.get().inspect(player(context), book);
        if (result.success()) {
            var view = result.value();
            context.getSource().sendSuccess(() -> Component.translatable("command.brnquest.author.status",
                    view.editorName(), view.draftRevision(), view.savedRevision(), view.dirty(), view.expiresAtTick()), false);
        }
        return report(context, "session_status", book.toString(), result);
    }

    private static int renew(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var result = EditSessionService.get().renew(player(context), session(context), revision(context));
        return report(context, "session_renew", "", result);
    }

    private static int close(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var result = EditSessionService.get().close(player(context), session(context), revision(context));
        return report(context, "session_close", "", result);
    }

    private static int discard(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        // Discard intentionally drops only the in-memory lease. The last saved draft
        // remains on disk and can be opened again by the same or another administrator.
        var result = EditSessionService.get().close(player(context), session(context), revision(context));
        return report(context, "session_discard", "", result);
    }

    private static int validate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation book = book(context);
        var result = new DraftEditService().validate(player(context), session(context), book, revision(context));
        if (result.value() != null) context.getSource().sendSuccess(() -> Component.translatable(
                "command.brnquest.author.diagnostics", result.value().diagnostics().size()), false);
        return report(context, "draft_validate", book.toString(), result);
    }

    private static int setTitle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation book = book(context);
        var result = new DraftEditService().setBookTitle(player(context), session(context), book,
                revision(context), StringArgumentType.getString(context, "title"));
        if (result.success()) context.getSource().sendSuccess(() -> Component.translatable(
                "command.brnquest.author.revision", result.value().snapshot().draftRevision()), false);
        return report(context, "draft_set_title", book.toString(), result);
    }

    private static int addGroup(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation book = book(context);
        ResourceLocation group = id(context, "group");
        var definition = new ChapterGroupDefinition(book, group,
                StringArgumentType.getString(context, "title"), IntegerArgumentType.getInteger(context, "order"));
        return reportEdit(context, "draft_add_group", group, new DraftEditService().addGroup(
                player(context), session(context), book, revision(context), definition));
    }

    private static int addChapter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation book = book(context);
        ResourceLocation chapter = id(context, "chapter");
        var definition = new ChapterDefinition(book, chapter, id(context, "group"),
                StringArgumentType.getString(context, "title"), id(context, "icon").toString(),
                IntegerArgumentType.getInteger(context, "order"), List.of());
        return reportEdit(context, "draft_add_chapter", chapter, new DraftEditService().addChapter(
                player(context), session(context), book, revision(context), definition));
    }

    private static int addQuest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation book = book(context);
        ResourceLocation quest = id(context, "quest");
        ResourceLocation chapter = id(context, "chapter");
        var definition = new QuestDefinition(book, quest, chapter, StringArgumentType.getString(context, "title"),
                "", "", id(context, "icon").toString(), DoubleArgumentType.getDouble(context, "x"),
                DoubleArgumentType.getDouble(context, "y"), List.of(), List.of(), List.of(), "");
        return reportEdit(context, "draft_add_quest", quest, new DraftEditService().addQuest(
                player(context), session(context), book, revision(context), chapter, definition));
    }

    private static int addDependency(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation book = book(context);
        ResourceLocation quest = id(context, "quest");
        return reportEdit(context, "draft_add_dependency", quest, new DraftEditService().addDependency(
                player(context), session(context), book, revision(context), quest, id(context, "dependency")));
    }

    private static int addTask(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation book = book(context);
        ResourceLocation quest = id(context, "quest");
        ResourceLocation task = id(context, "task");
        var definition = new TaskDefinition(book, task, id(context, "type"), config(context),
                BoolArgumentType.getBool(context, "optional"));
        return reportEdit(context, "draft_add_task", task, new DraftEditService().addTask(
                player(context), session(context), book, revision(context), quest, definition));
    }

    private static int addReward(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation book = book(context);
        ResourceLocation quest = id(context, "quest");
        ResourceLocation reward = id(context, "reward");
        var definition = new RewardDefinition(book, reward, id(context, "type"), config(context),
                StringArgumentType.getString(context, "claim_policy"), BoolArgumentType.getBool(context, "team"));
        return reportEdit(context, "draft_add_reward", reward, new DraftEditService().addReward(
                player(context), session(context), book, revision(context), quest, definition));
    }

    private static int reportEdit(CommandContext<CommandSourceStack> context, String action, ResourceLocation object,
                                  AuthorOperationResult<DraftEditResult> result) {
        if (result.success() && result.value() != null) context.getSource().sendSuccess(() -> Component.translatable(
                "command.brnquest.author.revision", result.value().snapshot().draftRevision()), false);
        return report(context, action, object.toString(), result);
    }

    private static int diff(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation book = book(context);
        DraftDiffService.Baseline baseline = DraftDiffService.Baseline.valueOf(
                StringArgumentType.getString(context, "baseline").toUpperCase(Locale.ROOT));
        var result = new DraftDiffService().preview(player(context), session(context), book, revision(context), baseline);
        if (result.success()) {
            context.getSource().sendSuccess(() -> Component.translatable("command.brnquest.author.changes",
                    result.value().entries().size()), false);
            // Keep command feedback bounded while retaining the complete structured result in the service API.
            result.value().entries().stream().limit(100).forEach(entry -> context.getSource().sendSuccess(
                    () -> Component.translatable("command.brnquest.author.diff_entry", entry.kind().name(),
                            entry.objectKind().name(), String.valueOf(entry.objectId()), entry.path(),
                            entry.before(), entry.after()), false));
        }
        return report(context, "draft_diff_" + baseline.name().toLowerCase(Locale.ROOT), book.toString(), result);
    }

    private static int save(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation book = book(context);
        var result = new DraftPersistenceService().save(player(context), session(context), book, revision(context));
        return report(context, "draft_save", book.toString(), result);
    }

    private static int publish(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation book = book(context);
        var result = new DraftPublishService().publish(player(context), session(context), book, revision(context));
        return report(context, "draft_publish", book.toString(), result);
    }

    private static int deploy(CommandContext<CommandSourceStack> context, boolean replace) {
        try {
            var result = new WorkspaceDeploymentService().deploy(context.getSource().getServer(), replace);
            if (result.status() == WorkspaceDeploymentService.Status.ALREADY_DEPLOYED) {
                context.getSource().sendFailure(Component.translatable("command.brnquest.author.world_pack_exists"));
                audit(context, "workspace_deploy", "", "CONFLICT", "WORLD_PACK_EXISTS");
                return 0;
            }
            context.getSource().sendSuccess(() -> Component.translatable("command.brnquest.author.deployed",
                    result.files(), result.backup() == null ? "" : result.backup().toString()), true);
            audit(context, "workspace_deploy", "", "SUCCESS", result.status().name());
            return 1;
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.translatable("command.brnquest.author.workspace_deploy_failed"));
            audit(context, "workspace_deploy", "", "IO_FAILURE", "WORKSPACE_DEPLOY_FAILED");
            return 0;
        }
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        new WorkspaceDeploymentService().reloadIncludingWorkspace(source.getServer()).whenComplete((ignored, error) -> {
            if (error == null && !yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager.get()
                    .lastReport().hasFatal()) {
                source.sendSuccess(() -> Component.translatable("command.brnquest.author.reload_complete"), true);
                audit(context, "workspace_reload", "", "SUCCESS", "RELOAD_COMPLETE");
            } else {
                source.sendFailure(Component.translatable("command.brnquest.author.reload_failed"));
                audit(context, "workspace_reload", "", "IO_FAILURE", "RELOAD_FAILED");
            }
        });
        source.sendSuccess(() -> Component.translatable("command.brnquest.author.reload_requested"), false);
        return 1;
    }

    private static int listBackups(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BackupKind kind = backupKind(context);
        if (kind == null) return 0;
        var result = new AuthorBackupService().list(player(context), kind);
        if (result.success()) result.value().forEach(backup -> context.getSource().sendSuccess(
                () -> Component.translatable("command.brnquest.author.backup_entry", backup.id(),
                        backup.revision(), backup.fileCount()), false));
        return report(context, "backup_list_" + kind.name().toLowerCase(Locale.ROOT), "", result);
    }

    private static int previewRestore(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BackupKind kind = backupKind(context);
        if (kind == null) return 0;
        String backup = StringArgumentType.getString(context, "backup");
        var result = new AuthorBackupService().preview(player(context), kind, backup);
        if (result.success()) context.getSource().sendSuccess(() -> Component.translatable(
                "command.brnquest.author.backup_preview", printableRevision(result.value().currentRevision()),
                result.value().backup().revision(), result.value().willReplace()), false);
        return report(context, "backup_preview_" + kind.name().toLowerCase(Locale.ROOT), backup, result);
    }

    private static int restore(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BackupKind kind = backupKind(context);
        if (kind == null) return 0;
        String backup = StringArgumentType.getString(context, "backup");
        String expected = StringArgumentType.getString(context, "current_revision");
        if (expected.equals("-")) expected = "";
        var result = new AuthorBackupService().restore(player(context), kind, backup, expected);
        return report(context, "backup_restore_" + kind.name().toLowerCase(Locale.ROOT), backup, result);
    }

    private static int report(CommandContext<CommandSourceStack> context, String action, String object,
                              AuthorOperationResult<?> result) {
        Component message = Component.translatable(result.success()
                ? "command.brnquest.author.result.success" : "command.brnquest.author.result.failure", result.code());
        if (result.success()) context.getSource().sendSuccess(() -> message, false);
        else context.getSource().sendFailure(message);
        audit(context, action, object, result.status().name(), result.code());
        return result.success() ? 1 : 0;
    }

    private static void audit(CommandContext<CommandSourceStack> context, String action, String object,
                              String status, String code) {
        BRNQuest.LOGGER.debug("[BRNQuest/AUDIT] actor={} action={} object={} status={} code={}",
                context.getSource().getTextName(), action, object, status, code);
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getSource().getPlayerOrException();
    }

    private static ResourceLocation book(CommandContext<CommandSourceStack> context) {
        return ResourceLocationArgument.getId(context, "book");
    }

    private static ResourceLocation id(CommandContext<CommandSourceStack> context, String name) {
        return ResourceLocationArgument.getId(context, name);
    }

    private static Map<String, String> config(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        try {
            var object = JsonParser.parseString(StringArgumentType.getString(context, "config")).getAsJsonObject();
            Map<String, String> values = new TreeMap<>();
            // Author commands accept only the schema-1 string map. Nested JSON would
            // otherwise be flattened ambiguously and could not round-trip losslessly.
            object.entrySet().forEach(entry -> {
                if (!entry.getValue().isJsonPrimitive()) throw new IllegalArgumentException("non-primitive value");
                values.put(entry.getKey(), entry.getValue().getAsString());
            });
            return Map.copyOf(values);
        } catch (RuntimeException exception) {
            throw INVALID_CONFIG.create();
        }
    }

    private static UUID session(CommandContext<CommandSourceStack> context) {
        return UUID.fromString(StringArgumentType.getString(context, "session"));
    }

    private static String revision(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "revision");
    }

    private static BackupKind backupKind(CommandContext<CommandSourceStack> context) {
        String value = StringArgumentType.getString(context, "kind");
        try {
            return BackupKind.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.translatable("command.brnquest.author.invalid_backup_kind"));
            return null;
        }
    }

    private static String printableRevision(String revision) {
        return revision.isBlank() ? "-" : revision;
    }
}
