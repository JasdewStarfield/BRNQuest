package yourscraft.jasdewstarfield.brnquest.network;

import yourscraft.jasdewstarfield.brnquest.data.BookSettings;
import yourscraft.jasdewstarfield.brnquest.data.QuestCreationDefaults;

import yourscraft.jasdewstarfield.brnquest.author.LocalizedSingleLineEdits;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.api.AuthorApi;
import yourscraft.jasdewstarfield.brnquest.author.*;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Server-authoritative authoring use cases; requests are parsed before entering this boundary. */
final class AuthoringMutationHandler {
    private final ServerPlayer player;
    private final AuthoringResponseSender responses;

    AuthoringMutationHandler(ServerPlayer player) { this(player, AuthoringResponseSender.forPlayer(player)); }

    /** Lets GameTests capture ordered packets while edits still execute through the actual author services. */
    AuthoringMutationHandler(ServerPlayer player, AuthoringResponseSender responses) {
        this.player = player;
        this.responses = responses;
    }

    void mutate(AuthoringRequestDecoder.MutationRequest wire) {
        UUID sessionId = wire.sessionId();
        ResourceLocation bookId = wire.bookId();
        var current = EditSessionService.get().snapshot(player, sessionId, bookId, wire.draftRevision());
        if (!current.success()) {
            responses.sendFailure("MUTATE", current);
            return;
        }
        if (wire.action() == AuthoringMutationAction.REVIEW) {
            new AuthoringPublicationHandler(player, responses).review(
                    new AuthoringRequestDecoder.SessionRequest(sessionId, bookId, wire.draftRevision()));
            return;
        }
        ResourceLocation targetId = wire.targetId();
        ResourceLocation parentId = wire.parentId();
        ResourceLocation sourceId = wire.sourceId();
        var editor = AuthorApi.editor();
        AuthorOperationResult<DraftEditResult> result;
        try {
            result = switch (wire.action()) {
                case MOVE_CANVAS_SELECTION, COPY_CANVAS_SELECTION, DELETE_CANVAS_SELECTION -> editor.editCanvasSelection(
                        player, sessionId, bookId, wire.draftRevision(), requireId(targetId), wire.action().name(),
                        domainPositions(wire.positions()), wire.x(), wire.y());
                case UPDATE_CANVAS -> editor.updateCanvas(player, sessionId, bookId, wire.draftRevision(),
                        requireId(targetId), CanvasScene.decode(wire.config().get("scene")));
                case UNDO -> EditSessionService.get().undo(player, sessionId, bookId, wire.draftRevision());
                case REDO -> EditSessionService.get().redo(player, sessionId, bookId, wire.draftRevision());
                case UPDATE_BOOK_PROPERTIES -> editor.updateBookProperties(player, sessionId, bookId, wire.draftRevision(),
                        readDefaults(wire.config(), current.value().book().questDefaults()),
                        wire.config().getOrDefault("fallback_locale", current.value().book().localization().fallbackLocale()),
                        LocalizedSingleLineEdits.values(wire.config()), wire.config().containsKey("book_settings")
                                ? BookSettings.fromJson(com.google.gson.JsonParser.parseString(wire.config().get("book_settings")))
                                : current.value().book().settings(), wire.config().containsKey("backgrounds") ? CanvasScene.decode(wire.config().get("backgrounds")) : null);
                case ADD_GROUP -> editor.addGroup(player, sessionId, bookId, wire.draftRevision(),
                        new ChapterGroupDefinition(bookId, requireId(targetId), wire.title(), wire.targetIndex()));
                case UPDATE_GROUP -> wire.config().containsKey(LocalizedSingleLineEdits.FIELD)
                        ? editor.updateLocalizedGroup(player, sessionId, bookId, wire.draftRevision(), requireId(targetId),
                                groupReplacement(current.value().book(), targetId, wire.title(), wire.targetIndex(), wire.config()),
                                LocalizedSingleLineEdits.values(wire.config()))
                        : editor.updateGroupProperties(player, sessionId, bookId, wire.draftRevision(),
                        requireId(targetId), groupReplacement(current.value().book(), targetId, wire.title(), wire.targetIndex(), wire.config()), wire.config().getOrDefault("locale", ""));
                case MOVE_GROUP -> editor.moveGroup(player, sessionId, bookId, wire.draftRevision(),
                        requireId(targetId), wire.targetIndex());
                case DELETE_GROUP -> editor.removeGroupWithContents(player, sessionId, bookId,
                        wire.draftRevision(), requireId(targetId));
                case PASTE_QUESTS -> editor.pasteQuestClipboard(player, sessionId, bookId, wire.draftRevision(), requireId(targetId),
                        QuestClipboardSnapshot.decode(wire.config().get("snapshot")), wire.x(), wire.y());
                case COPY_CHAPTER -> editor.duplicateChapter(player, sessionId, bookId, wire.draftRevision(), requireId(targetId));
                case ADD_CHAPTER -> editor.addChapter(player, sessionId, bookId, wire.draftRevision(),
                        new ChapterDefinition(bookId, requireId(targetId), requireId(parentId),
                                wire.title(), "", wire.targetIndex(), List.of()));
                case UPDATE_CHAPTER -> wire.config().containsKey(LocalizedSingleLineEdits.FIELD)
                        ? editor.updateLocalizedChapter(player, sessionId, bookId, wire.draftRevision(), requireId(targetId),
                                chapterReplacement(current.value().book(), targetId, parentId, wire.title(), wire.targetIndex(), wire.config()),
                                LocalizedSingleLineEdits.values(wire.config()))
                        : editor.updateChapter(player, sessionId, bookId, wire.draftRevision(),
                        requireId(targetId), chapterReplacement(current.value().book(), targetId, parentId,
                                wire.title(), wire.targetIndex(), wire.config()));
                case MOVE_CHAPTER -> wire.config().containsKey("group")
                        ? editor.moveChapterToGroup(player, sessionId, bookId, wire.draftRevision(),
                                requireId(targetId), requireId(ResourceLocation.tryParse(wire.config().get("group"))), wire.targetIndex())
                        : editor.moveChapterOrder(player, sessionId, bookId, wire.draftRevision(), requireId(targetId), wire.targetIndex());
                case DELETE_CHAPTER -> editor.removeChapterWithContents(player, sessionId, bookId,
                        wire.draftRevision(), requireId(targetId));
                case ADD_QUEST -> editor.createQuest(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), new QuestDefinition(bookId, requireId(targetId), parentId,
                                wire.title(), "", "", "", wire.x(), wire.y(),
                                List.of(), List.of(), List.of(), ""), readDefaults(wire.config(), QuestCreationDefaults.EMPTY));
                case COPY_QUESTS, DELETE_QUESTS -> editor.editQuestSelection(player, sessionId, bookId, wire.draftRevision(),
                        wire.positions().keySet(), wire.action() == AuthoringMutationAction.COPY_QUESTS, wire.x(), wire.y());
                case COPY_QUEST -> editor.copyQuest(player, sessionId, bookId, wire.draftRevision(),
                        requireId(sourceId), questCopy(current.value().book(), sourceId, requireId(targetId),
                                wire.title(), wire.x(), wire.y()));
                case DELETE_QUEST -> editor.removeQuestAndReferences(player, sessionId, bookId,
                        wire.draftRevision(), requireId(targetId));
                case MOVE_QUESTS -> editor.updateQuestPositions(player, sessionId, bookId, wire.draftRevision(),
                        domainPositions(wire.positions()));
                case UPDATE_QUEST_TRANSLATION -> wire.config().containsKey(LocalizedSingleLineEdits.FIELD)
                        ? editor.updateSingleLineTranslations(player, sessionId, bookId, wire.draftRevision(), requireId(targetId),
                                wire.config().get(LocalizedSingleLineEdits.FIELD), LocalizedSingleLineEdits.values(wire.config()))
                        : editor.updateQuestTranslation(player, sessionId, bookId,
                        wire.draftRevision(), requireId(targetId), wire.locale(),
                        wire.config().getOrDefault("title", ""), wire.config().getOrDefault("subtitle", ""),
                        wire.config().getOrDefault("description", ""),
                        wire.config().get("description_format"));
                case ADD_DEPENDENCY -> editor.addDependency(player, sessionId, bookId, wire.draftRevision(),
                        requireId(targetId), requireId(sourceId));
                case REMOVE_DEPENDENCY -> editor.removeDependency(player, sessionId, bookId,
                        wire.draftRevision(), requireId(targetId), requireId(sourceId));
                case ADD_TASK, UPDATE_TASK, COPY_TASK, PASTE_TASK, MOVE_TASK, DELETE_TASK,
                     ADD_REWARD, UPDATE_REWARD, COPY_REWARD, PASTE_REWARD, MOVE_REWARD, DELETE_REWARD -> mutateTyped(wire, current.value().book());
                default -> AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST,
                        "UNKNOWN_EDITOR_MUTATION", "Unknown editor mutation action");
            };
        } catch (IllegalArgumentException exception) {
            responses.sendFailure("MUTATE", AuthorOperationResult.Status.INVALID_REQUEST,
                    "INVALID_EDITOR_MUTATION", exception.getMessage(), List.of(AuthoringResponseSender.mutationDiagnostic(wire, exception)));
            return;
        }
        complete(wire, result);
    }

    /** Metadata edits preserve fields omitted by older clients and all unknown extension data. */
    private static ChapterGroupDefinition groupReplacement(QuestBookDefinition book, ResourceLocation id,
                                                            String title, int order, Map<String, String> config) {
        ChapterGroupDefinition source = book.chapterGroups().stream().filter(group -> group.id().equals(id))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown chapter group " + id));
        return new ChapterGroupDefinition(book.id(), id, config.containsKey(LocalizedSingleLineEdits.FIELD) ? source.title() : title, order,
                config.getOrDefault("icon", source.icon()), config.getOrDefault("description", source.description()), source.extensions());
    }

    private static Boolean readConsumeItems(Map<String, String> config, Boolean fallback) {
        if (!config.containsKey("default_consume_items")) return fallback;
        return switch (config.get("default_consume_items")) {
            case "default" -> null;
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("Invalid consumption default");
        };
    }

    private static QuestCreationDefaults readDefaults(Map<String, String> config, QuestCreationDefaults fallback) {
        return config.containsKey("quest_defaults") ? QuestCreationDefaults.fromJson(
                com.google.gson.JsonParser.parseString(config.get("quest_defaults"))) : fallback;
    }

    void complete(AuthoringRequestDecoder.MutationRequest wire, AuthorOperationResult<DraftEditResult> result) {
        UUID sessionId = wire.sessionId();
        ResourceLocation bookId = wire.bookId();

        if (!result.success()) {
            responses.sendMutationFailure(result, wire);
            return;
        }
        DraftSnapshot draft = result.value().snapshot();
        var renewed = AuthorApi.renew(player, sessionId, draft.draftRevision());
        if (!renewed.success()) {
            responses.sendFailure("MUTATE", renewed);
            return;
        }
        if (wire.action() == AuthoringMutationAction.MOVE_QUESTS) {
            // Dragging is the highest-frequency graph edit. The server returns only
            // the accepted positions plus the authoritative resulting revision;
            // the client verifies that revision before exposing the patched draft.
            responses.sendPositionPatch(result.code(), result.message(), renewed.value(), draft, wire,
                    () -> AuthorApi.close(player, renewed.value().sessionId(), renewed.value().session().draftRevision()));
        } else {
            sendDraft("MUTATE", result.code(), result.message(), renewed.value(), draft);
        }
    }

    static ResourceLocation requireId(ResourceLocation id) {
        if (id == null) throw new IllegalArgumentException("A valid namespaced ID is required");
        return id;
    }

    // Missing icon retains legacy callers; an explicit empty icon resets the chapter fallback.
    private static boolean strictBoolean(String value) {
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Invalid dependency-line boolean");
        return Boolean.parseBoolean(value);
    }

    static ChapterDefinition chapterReplacement(yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition book,
                                                        ResourceLocation chapterId, ResourceLocation groupId,
                                                        String title, int order, Map<String, String> config) {
        ChapterDefinition chapter = book.chapters().stream().filter(value -> value.id().equals(chapterId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Chapter no longer exists"));
        return new ChapterDefinition(book.id(), chapter.id(), requireId(groupId), config.containsKey(LocalizedSingleLineEdits.FIELD) ? chapter.title() : title,
                config.getOrDefault("icon", chapter.icon()), order, chapter.quests(), config.containsKey("backgrounds")
                        ? chapter.canvasScene().withBackgrounds(CanvasScene.decode(config.get("backgrounds"))).write(chapter.extensions()) : chapter.extensions(), readDefaults(config, chapter.questDefaults()), readConsumeItems(config, chapter.consumeItems()),
                config.containsKey("autofocus_id") ? (config.get("autofocus_id").isBlank() ? null
                        : ResourceLocation.parse(config.get("autofocus_id"))) : chapter.autofocusQuestId(),
                config.containsKey("default_hide_dependency_lines") ? strictBoolean(config.get("default_hide_dependency_lines")) : chapter.defaultHideDependencyLines());
    }

    private static QuestDefinition questCopy(yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition book,
                                             ResourceLocation sourceId, ResourceLocation targetId,
                                             String title, double x, double y) {
        QuestDefinition source = book.quests().stream().filter(value -> value.id().equals(sourceId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Source quest no longer exists"));
        List<TaskDefinition> tasks = new java.util.ArrayList<>();
        for (int index = 0; index < source.tasks().size(); index++) {
            TaskDefinition task = source.tasks().get(index);
            tasks.add(new TaskDefinition(book.id(), nestedCopyId(book, targetId, "task", index),
                    task.typeId(), task.config(), task.optional()));
        }
        List<RewardDefinition> rewards = new java.util.ArrayList<>();
        for (int index = 0; index < source.rewards().size(); index++) {
            RewardDefinition reward = source.rewards().get(index);
            rewards.add(new RewardDefinition(book.id(), nestedCopyId(book, targetId, "reward", index),
                    reward.typeId(), reward.config(), reward.claimPolicy(), reward.teamReward()));
        }
        return new QuestDefinition(book.id(), targetId, source.chapterId(), title, source.subtitle(),
                source.description(), source.descriptionFormat(), source.icon(), x, y, source.dependencies(), tasks, rewards, "",
                source.appearance(), source.behavior(), source.extensions());
    }

    private static ResourceLocation nestedCopyId(yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition book,
                                                 ResourceLocation targetId, String kind, int index) {
        for (int suffix = 0; suffix < 10_000; suffix++) {
            String tail = suffix == 0 ? "" : "_" + suffix;
            ResourceLocation candidate = ResourceLocation.fromNamespaceAndPath(targetId.getNamespace(),
                    targetId.getPath() + "/" + kind + "_" + index + tail);
            boolean exists = book.quests().stream().flatMap(quest -> java.util.stream.Stream.concat(
                            quest.tasks().stream().map(TaskDefinition::id), quest.rewards().stream().map(RewardDefinition::id)))
                    .anyMatch(candidate::equals);
            if (!exists) return candidate;
        }
        throw new IllegalArgumentException("Unable to allocate copied " + kind + " ID");
    }

    private static Map<ResourceLocation, DraftBookEditor.Position> domainPositions(
            Map<ResourceLocation, AuthoringRequestDecoder.Position> positions) {
        Map<ResourceLocation, DraftBookEditor.Position> result = new LinkedHashMap<>();
        positions.forEach((id, position) -> result.put(id, new DraftBookEditor.Position(position.x(), position.y())));
        return java.util.Collections.unmodifiableMap(result);
    }

    /** Closing an oversized session is a use-case decision supplied to the transport explicitly. */
    private void sendDraft(String action, String code, String message,
                                  EditSessionHandle handle, DraftSnapshot draft) {
        responses.sendDraft(action, code, message, handle, draft,
                () -> AuthorApi.close(player, handle.sessionId(), handle.session().draftRevision()));
    }
    /** Typed entries keep server-owned copies, optional flags and reward-claim semantics together. */
    private AuthorOperationResult<DraftEditResult> mutateTyped(AuthoringRequestDecoder.MutationRequest wire, QuestBookDefinition book) {
        UUID sessionId = wire.sessionId();
        ResourceLocation bookId = wire.bookId();
        ResourceLocation targetId = wire.targetId();
        ResourceLocation parentId = wire.parentId();
        ResourceLocation sourceId = wire.sourceId();
        var editor = AuthorApi.editor();
            return switch (wire.action()) {
                // Paste deliberately bypasses creation defaults: the snapshot already contains explicit values.
                case PASTE_TASK -> editor.addTask(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), TypedEntrySnapshot.decode(wire.config().get("snapshot")).task(requireId(targetId)));
                case PASTE_REWARD -> editor.addReward(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), TypedEntrySnapshot.decode(wire.config().get("snapshot")).reward(requireId(targetId)));
                case ADD_TASK -> editor.addTask(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), new TaskDefinition(bookId, requireId(targetId), requireId(sourceId),
                                AuthoringRequestDecoder.boundedConfig(yourscraft.jasdewstarfield.brnquest.author.EntryCreationPolicy.taskConfig(
                                        book, parentId, sourceId, wire.config())), false));
                case UPDATE_TASK -> editor.updateTask(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), requireId(sourceId), taskReplacement(book,
                                parentId, sourceId, requireId(targetId), wire.config(), wire.targetIndex() != 0));
                case COPY_TASK -> editor.copyTask(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), requireId(sourceId), taskCopy(book,
                                parentId, sourceId, requireId(targetId)));
                case MOVE_TASK -> editor.moveTask(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), requireId(targetId), wire.targetIndex());
                case DELETE_TASK -> editor.removeTask(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), requireId(targetId));
                case ADD_REWARD -> editor.addReward(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), new RewardDefinition(bookId, requireId(targetId), requireId(sourceId),
                                rewardMutationConfig(wire.config()), wire.claimPolicy().isBlank()
                                        ? yourscraft.jasdewstarfield.brnquest.author.EntryCreationPolicy.rewardPolicy(book, sourceId, wire.config()) : wire.claimPolicy(),
                                wire.claimPolicy().isBlank() ? book.settings().rewardTeam() : wire.targetIndex() != 0));
                case UPDATE_REWARD -> editor.updateReward(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), requireId(sourceId), rewardReplacement(book,
                                parentId, sourceId, requireId(targetId), wire.config(), wire.claimPolicy(),
                                wire.targetIndex() != 0));
                case COPY_REWARD -> editor.copyReward(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), requireId(sourceId), rewardCopy(book,
                                parentId, sourceId, requireId(targetId)));
                case MOVE_REWARD -> editor.moveReward(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), requireId(targetId), wire.targetIndex());
                case DELETE_REWARD -> editor.removeReward(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), requireId(targetId));
                default -> AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST,
                        "UNKNOWN_EDITOR_MUTATION", "Unknown editor mutation action");
            };
    }

    private static TaskDefinition taskCopy(QuestBookDefinition book, ResourceLocation questId,
                                           ResourceLocation sourceId, ResourceLocation targetId) {
        QuestDefinition quest = requireQuest(book, questId);
        TaskDefinition source = quest.tasks().stream().filter(task -> task.id().equals(sourceId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Source task no longer exists"));
        return new TaskDefinition(book.id(), targetId, source.typeId(), source.config(), source.optional());
    }

    private static RewardDefinition rewardCopy(QuestBookDefinition book, ResourceLocation questId,
                                                ResourceLocation sourceId, ResourceLocation targetId) {
        QuestDefinition quest = requireQuest(book, questId);
        RewardDefinition source = quest.rewards().stream().filter(reward -> reward.id().equals(sourceId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Source reward no longer exists"));
        return new RewardDefinition(book.id(), targetId, source.typeId(), source.config(),
                source.claimPolicy(), source.teamReward());
    }

    private static QuestDefinition requireQuest(QuestBookDefinition book, ResourceLocation questId) {
        if (questId == null) throw new IllegalArgumentException("Quest ID is required");
        return book.quests().stream().filter(quest -> quest.id().equals(questId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected quest no longer exists"));
    }

    private static TaskDefinition taskReplacement(QuestBookDefinition book,
                                                  ResourceLocation questId, ResourceLocation sourceId,
                                                  ResourceLocation replacementId, Map<String, String> config,
                                                  boolean optional) {
        QuestDefinition quest = requireQuest(book, questId);
        TaskDefinition source = quest.tasks().stream().filter(task -> task.id().equals(sourceId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected task no longer exists"));
        return new TaskDefinition(book.id(), replacementId, source.typeId(),
                AuthoringRequestDecoder.boundedConfig(config), optional);
    }

    static RewardDefinition rewardReplacement(QuestBookDefinition book,
                                                       ResourceLocation questId, ResourceLocation sourceId,
                                                       ResourceLocation replacementId, Map<String, String> config,
                                                       String claimPolicy, boolean teamReward) {
        QuestDefinition quest = requireQuest(book, questId);
        RewardDefinition source = quest.rewards().stream().filter(reward -> reward.id().equals(sourceId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected reward no longer exists"));
        return new RewardDefinition(book.id(), replacementId, source.typeId(),
                rewardMutationConfig(config), claimPolicy, teamReward);
    }

    static Map<String, String> rewardMutationConfig(Map<String, String> config) {
        return AuthoringRequestDecoder.boundedConfig(config);
    }

}
