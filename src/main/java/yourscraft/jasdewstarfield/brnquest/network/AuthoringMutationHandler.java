package yourscraft.jasdewstarfield.brnquest.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.api.AuthorApi;
import yourscraft.jasdewstarfield.brnquest.author.*;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import yourscraft.jasdewstarfield.brnquest.task.ItemChoiceMatcher;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;

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
                case UNDO -> EditSessionService.get().undo(player, sessionId, bookId, wire.draftRevision());
                case REDO -> EditSessionService.get().redo(player, sessionId, bookId, wire.draftRevision());
                case ADD_GROUP -> editor.addGroup(player, sessionId, bookId, wire.draftRevision(),
                        new ChapterGroupDefinition(bookId, requireId(targetId), wire.title(), wire.targetIndex()));
                case UPDATE_GROUP -> editor.updateGroup(player, sessionId, bookId, wire.draftRevision(),
                        requireId(targetId), new ChapterGroupDefinition(bookId, targetId,
                                wire.title(), wire.targetIndex()));
                case MOVE_GROUP -> editor.moveGroup(player, sessionId, bookId, wire.draftRevision(),
                        requireId(targetId), wire.targetIndex());
                case DELETE_GROUP -> editor.removeGroupWithContents(player, sessionId, bookId,
                        wire.draftRevision(), requireId(targetId));
                case ADD_CHAPTER -> editor.addChapter(player, sessionId, bookId, wire.draftRevision(),
                        new ChapterDefinition(bookId, requireId(targetId), requireId(parentId),
                                wire.title(), "", wire.targetIndex(), List.of()));
                case UPDATE_CHAPTER -> editor.updateChapter(player, sessionId, bookId, wire.draftRevision(),
                        requireId(targetId), chapterReplacement(current.value().book(), targetId, parentId,
                                wire.title(), wire.targetIndex()));
                case MOVE_CHAPTER -> editor.moveChapterOrder(player, sessionId, bookId, wire.draftRevision(),
                        requireId(targetId), wire.targetIndex());
                case DELETE_CHAPTER -> editor.removeChapterWithContents(player, sessionId, bookId,
                        wire.draftRevision(), requireId(targetId));
                case ADD_QUEST -> editor.addQuest(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), new QuestDefinition(bookId, requireId(targetId), parentId,
                                wire.title(), "", "", "", wire.x(), wire.y(),
                                List.of(), List.of(), List.of(), ""));
                case COPY_QUEST -> editor.copyQuest(player, sessionId, bookId, wire.draftRevision(),
                        requireId(sourceId), questCopy(current.value().book(), sourceId, requireId(targetId),
                                wire.title(), wire.x(), wire.y()));
                case DELETE_QUEST -> editor.removeQuestAndReferences(player, sessionId, bookId,
                        wire.draftRevision(), requireId(targetId));
                case MOVE_QUESTS -> editor.updateQuestPositions(player, sessionId, bookId, wire.draftRevision(),
                        domainPositions(wire.positions()));
                case UPDATE_QUEST_TRANSLATION -> editor.updateQuestTranslation(player, sessionId, bookId,
                        wire.draftRevision(), requireId(targetId), wire.locale(),
                        wire.config().getOrDefault("title", ""), wire.config().getOrDefault("subtitle", ""),
                        wire.config().getOrDefault("description", ""));
                case ADD_DEPENDENCY -> editor.addDependency(player, sessionId, bookId, wire.draftRevision(),
                        requireId(targetId), requireId(sourceId));
                case REMOVE_DEPENDENCY -> editor.removeDependency(player, sessionId, bookId,
                        wire.draftRevision(), requireId(targetId), requireId(sourceId));
                case ADD_TASK, UPDATE_TASK, COPY_TASK, MOVE_TASK, DELETE_TASK,
                     ADD_REWARD, UPDATE_REWARD, COPY_REWARD, MOVE_REWARD, DELETE_REWARD -> mutateTyped(wire, current.value().book());
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

    private static ChapterDefinition chapterReplacement(yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition book,
                                                        ResourceLocation chapterId, ResourceLocation groupId,
                                                        String title, int order) {
        ChapterDefinition chapter = book.chapters().stream().filter(value -> value.id().equals(chapterId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Chapter no longer exists"));
        return new ChapterDefinition(book.id(), chapter.id(), requireId(groupId), title,
                chapter.icon(), order, chapter.quests(), chapter.extensions());
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
                source.description(), source.icon(), x, y, source.dependencies(), tasks, rewards, "",
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
                case ADD_TASK -> editor.addTask(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), new TaskDefinition(bookId, requireId(targetId), requireId(sourceId),
                                taskMutationConfig(player, sourceId, wire.config()), false));
                case UPDATE_TASK -> editor.updateTask(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), requireId(sourceId), taskReplacement(player, book,
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
                                rewardMutationConfig(wire.config()), "manual", false));
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

    private static Map<String, String> taskMutationConfig(ServerPlayer player, ResourceLocation typeId,
                                                           Map<String, String> config) {
        Map<String, String> bounded = AuthoringRequestDecoder.boundedConfig(config);
        if (TaskTypes.ITEM.equals(typeId) || TaskTypes.ITEM_CHOICE.equals(typeId)) {
            Map<String, String> canonical = ItemChoiceMatcher.canonicalEditorConfig(bounded);
            var normalizedResult = ItemChoiceMatcher.normalizeConfig(player.registryAccess(), canonical);
            ItemChoiceMatcher.Spec normalized = normalizedResult.result().orElseThrow(() ->
                    new IllegalArgumentException(normalizedResult.error()
                            .map(error -> error.message()).orElse("Item matcher is invalid")));
            Map<String, String> normalizedConfig = new LinkedHashMap<>(canonical);
            normalizedConfig.put("matcher", normalized.encode());
            normalizedConfig.put("required_entries", Integer.toString(normalized.requiredEntries()));
            return Map.copyOf(normalizedConfig);
        }
        return bounded;
    }

    private static TaskDefinition taskReplacement(ServerPlayer player, QuestBookDefinition book,
                                                  ResourceLocation questId, ResourceLocation sourceId,
                                                  ResourceLocation replacementId, Map<String, String> config,
                                                  boolean optional) {
        QuestDefinition quest = requireQuest(book, questId);
        TaskDefinition source = quest.tasks().stream().filter(task -> task.id().equals(sourceId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected task no longer exists"));
        return new TaskDefinition(book.id(), replacementId, source.typeId(),
                taskMutationConfig(player, source.typeId(), config), optional);
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
