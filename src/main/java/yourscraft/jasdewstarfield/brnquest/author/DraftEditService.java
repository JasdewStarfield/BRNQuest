package yourscraft.jasdewstarfield.brnquest.author;

import yourscraft.jasdewstarfield.brnquest.data.BookSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.data.*;

import java.util.List;
import java.util.Map;
import yourscraft.jasdewstarfield.brnquest.data.QuestCreationDefaults;
import java.util.UUID;
import java.util.function.Function;

/** Permission- and revision-checked entry point for all stage-4 draft CRUD. */
public final class DraftEditService {
    private final EditSessionService sessions;

    public DraftEditService() {
        this(EditSessionService.get());
    }

    DraftEditService(EditSessionService sessions) {
        this.sessions = sessions;
    }

    public AuthorOperationResult<DraftEditResult> setBookTitle(ServerPlayer player, UUID sessionId,
                                                               ResourceLocation bookId, String revision, String title) {
        return apply(player, sessionId, bookId, revision, book -> DraftBookEditor.setBookTitle(book, title));
    }

    public AuthorOperationResult<DraftEditResult> addGroup(ServerPlayer player, UUID sessionId,
                                                            ResourceLocation bookId, String revision,
                                                            ChapterGroupDefinition group) {
        return apply(player, sessionId, bookId, revision, book -> DraftBookEditor.addGroup(book, group));
    }

    public AuthorOperationResult<DraftEditResult> copyGroup(ServerPlayer player, UUID sessionId,
                                                             ResourceLocation bookId, String revision,
                                                             ResourceLocation sourceId, ChapterGroupDefinition copy) {
        return apply(player, sessionId, bookId, revision, book -> DraftBookEditor.copyGroup(book, sourceId, copy));
    }

    public AuthorOperationResult<DraftEditResult> updateGroup(ServerPlayer player, UUID sessionId,
                                                               ResourceLocation bookId, String revision,
                                                               ResourceLocation groupId, ChapterGroupDefinition group) {
        return apply(player, sessionId, bookId, revision, book -> DraftBookEditor.updateGroup(book, groupId, group));
    }

    /** Metadata and the edited title locale form one revision-checked transaction and one undo step. */
    public AuthorOperationResult<DraftEditResult> updateGroupProperties(ServerPlayer player, UUID sessionId,
            ResourceLocation bookId, String revision, ResourceLocation groupId, ChapterGroupDefinition group, String locale) {
        return apply(player, sessionId, bookId, revision,
                book -> DraftBookEditor.updateGroupProperties(book, groupId, group, locale));
    }

    /** Metadata and every changed locale are committed together under the same revision and undo entry. */
    public AuthorOperationResult<DraftEditResult> updateLocalizedGroup(ServerPlayer player, UUID sessionId,
            ResourceLocation bookId, String revision, ResourceLocation id, ChapterGroupDefinition replacement,
            Map<String, String> values) {
        return apply(player, sessionId, bookId, revision, book -> {
            var updated = DraftBookEditor.updateGroup(book, id, replacement);
            return localize(updated, "chapter_group", id, "title", values);
        });
    }

    public AuthorOperationResult<DraftEditResult> updateLocalizedChapter(ServerPlayer player, UUID sessionId,
            ResourceLocation bookId, String revision, ResourceLocation id, ChapterDefinition replacement,
            Map<String, String> values) {
        return apply(player, sessionId, bookId, revision, book ->
                localize(DraftBookEditor.updateChapter(book, id, replacement), "chapter", id, "title", values));
    }

    /** A quick edit cannot materialize fallback values for the quest's other text fields. */
    public AuthorOperationResult<DraftEditResult> updateSingleLineTranslations(ServerPlayer player, UUID sessionId,
            ResourceLocation bookId, String revision, ResourceLocation id, String field, Map<String, String> values) {
        return apply(player, sessionId, bookId, revision, book -> AuthorOperationResult.success("DRAFT_UPDATED", "Localized text updated", new DraftChange(
                LocalizedSingleLineEdits.apply(book, "quest", id, field, values), List.of(id))));
    }

    private static AuthorOperationResult<DraftChange> localize(AuthorOperationResult<DraftChange> result,
            String kind, ResourceLocation id, String field, Map<String, String> values) {
        if (!result.success()) return result;
        return AuthorOperationResult.success("DRAFT_UPDATED", "Localized text updated", new DraftChange(LocalizedSingleLineEdits.apply(
                result.value().book(), kind, id, field, values), result.value().affectedObjects()));
    }

    public AuthorOperationResult<DraftEditResult> moveGroup(ServerPlayer player, UUID sessionId,
                                                             ResourceLocation bookId, String revision,
                                                             ResourceLocation groupId, int targetIndex) {
        return apply(player, sessionId, bookId, revision,
                book -> DraftBookEditor.moveGroup(book, groupId, targetIndex));
    }

    public AuthorOperationResult<DraftEditResult> removeGroup(ServerPlayer player, UUID sessionId,
                                                               ResourceLocation bookId, String revision,
                                                               ResourceLocation groupId) {
        return apply(player, sessionId, bookId, revision, book -> DraftBookEditor.removeGroup(book, groupId));
    }

    public AuthorOperationResult<DraftEditResult> removeGroupWithContents(ServerPlayer player, UUID sessionId,
                                                                           ResourceLocation bookId, String revision,
                                                                           ResourceLocation groupId) {
        return apply(player, sessionId, bookId, revision,
                book -> DraftBookEditor.removeGroupWithContents(book, groupId));
    }

    public AuthorOperationResult<DraftEditResult> addChapter(ServerPlayer player, UUID sessionId,
                                                              ResourceLocation bookId, String revision,
                                                              ChapterDefinition chapter) {
        return apply(player, sessionId, bookId, revision, book -> DraftBookEditor.addChapter(book, chapter));
    }

    public AuthorOperationResult<DraftEditResult> copyChapter(ServerPlayer player, UUID sessionId,
                                                               ResourceLocation bookId, String revision,
                                                               ResourceLocation sourceId, ChapterDefinition copy) {
        return apply(player, sessionId, bookId, revision, book -> DraftBookEditor.copyChapter(book, sourceId, copy));
    }

    public AuthorOperationResult<DraftEditResult> updateChapter(ServerPlayer player, UUID sessionId,
                                                                 ResourceLocation bookId, String revision,
                                                                 ResourceLocation chapterId, ChapterDefinition chapter) {
        return apply(player, sessionId, bookId, revision, book -> DraftBookEditor.updateChapter(book, chapterId, chapter));
    }

    public AuthorOperationResult<DraftEditResult> moveChapterOrder(ServerPlayer player, UUID sessionId,
                                                                    ResourceLocation bookId, String revision,
                                                                    ResourceLocation chapterId, int targetIndex) {
        return apply(player, sessionId, bookId, revision,
                book -> DraftBookEditor.moveChapterOrder(book, chapterId, targetIndex));
    }

    /** Cross-group dragging is one revision-checked edit, not a pair of partially applied updates. */
    public AuthorOperationResult<DraftEditResult> moveChapterToGroup(ServerPlayer player, UUID sessionId,
            ResourceLocation bookId, String revision, ResourceLocation chapterId, ResourceLocation groupId, int index) {
        return apply(player, sessionId, bookId, revision,
                book -> DraftBookEditor.moveChapterToGroup(book, chapterId, groupId, index));
    }

    public AuthorOperationResult<DraftEditResult> removeChapter(ServerPlayer player, UUID sessionId,
                                                                 ResourceLocation bookId, String revision,
                                                                 ResourceLocation chapterId) {
        return apply(player, sessionId, bookId, revision, book -> DraftBookEditor.removeChapter(book, chapterId));
    }

    public AuthorOperationResult<DraftEditResult> removeChapterWithContents(ServerPlayer player, UUID sessionId,
                                                                             ResourceLocation bookId, String revision,
                                                                             ResourceLocation chapterId) {
        return apply(player, sessionId, bookId, revision,
                book -> DraftBookEditor.removeChapterWithContents(book, chapterId));
    }

    /** Creation templates are resolved on the server and become explicit quest values. */
    public AuthorOperationResult<DraftEditResult> createQuest(ServerPlayer player, UUID sessionId,
            ResourceLocation bookId, String revision, ResourceLocation chapterId, QuestDefinition quest,
            QuestCreationDefaults explicit) {
        return apply(player, sessionId, bookId, revision, book -> DraftBookEditor.createQuest(book, chapterId, quest, explicit));
    }

    /** Combined settings update shares one undo/revision transaction with book metadata. */
    public AuthorOperationResult<DraftEditResult> updateBookProperties(ServerPlayer player, UUID sessionId,
            ResourceLocation bookId, String revision, QuestCreationDefaults defaults, String fallback,
            Map<String, String> titles, BookSettings settings) {
        return apply(player, sessionId, bookId, revision,
                book -> DraftBookEditor.updateBookProperties(book, defaults, fallback, titles, settings));
    }

    public AuthorOperationResult<DraftEditResult> updateBookProperties(ServerPlayer player, UUID sessionId,
            ResourceLocation bookId, String revision, QuestCreationDefaults defaults, String fallback,
            Map<String, String> titles) {
        return apply(player, sessionId, bookId, revision,
                book -> DraftBookEditor.updateBookProperties(book, defaults, fallback, titles));
    }

    public AuthorOperationResult<DraftEditResult> addQuest(ServerPlayer player, UUID sessionId,
                                                            ResourceLocation bookId, String revision,
                                                            ResourceLocation chapterId, QuestDefinition quest) {
        return apply(player, sessionId, bookId, revision, book -> DraftBookEditor.addQuest(book, chapterId, quest));
    }

    public AuthorOperationResult<DraftEditResult> copyQuest(ServerPlayer player, UUID sessionId,
                                                             ResourceLocation bookId, String revision,
                                                             ResourceLocation sourceId, QuestDefinition copy) {
        return apply(player, sessionId, bookId, revision, book -> DraftBookEditor.copyQuest(book, sourceId, copy));
    }

    public AuthorOperationResult<DraftEditResult> updateQuest(ServerPlayer player, UUID sessionId,
                                                               ResourceLocation bookId, String revision,
                                                               ResourceLocation questId, QuestDefinition quest) {
        return apply(player, sessionId, bookId, revision, book -> DraftBookEditor.updateQuest(book, questId, quest));
    }

    /** Server-authoritative basic-property transaction, including explicit stable-ID rename semantics. */
    public AuthorOperationResult<DraftEditResult> updateQuestBasics(ServerPlayer player, UUID sessionId,
                                                                     ResourceLocation bookId, String revision,
                                                                     ResourceLocation questId, QuestDefinition quest) {
        return apply(player, sessionId, bookId, revision,
                book -> DraftBookEditor.updateQuestBasics(book, questId, quest));
    }

    public AuthorOperationResult<DraftEditResult> updateQuestPositions(ServerPlayer player, UUID sessionId,
                                                                        ResourceLocation bookId, String revision,
                                                                        java.util.Map<ResourceLocation,
                                                                                DraftBookEditor.Position> positions) {
        return apply(player, sessionId, bookId, revision,
                book -> DraftBookEditor.updateQuestPositions(book, positions));
    }

    public AuthorOperationResult<DraftEditResult> updateQuestTranslation(ServerPlayer player, UUID sessionId,
                                                                          ResourceLocation bookId, String revision,
                                                                          ResourceLocation questId, String locale,
                                                                          String title, String subtitle,
                                                                          String description) {
        return apply(player, sessionId, bookId, revision, book -> DraftBookEditor.updateQuestTranslation(
                book, questId, locale, title, subtitle, description));
    }

    public AuthorOperationResult<DraftEditResult> moveQuest(ServerPlayer player, UUID sessionId,
                                                             ResourceLocation bookId, String revision,
                                                             ResourceLocation questId, ResourceLocation chapterId,
                                                             int targetIndex) {
        return apply(player, sessionId, bookId, revision,
                book -> DraftBookEditor.moveQuest(book, questId, chapterId, targetIndex));
    }

    public AuthorOperationResult<DraftEditResult> removeQuest(ServerPlayer player, UUID sessionId,
                                                               ResourceLocation bookId, String revision,
                                                               ResourceLocation questId) {
        return apply(player, sessionId, bookId, revision, book -> DraftBookEditor.removeQuest(book, questId));
    }

    public AuthorOperationResult<DraftEditResult> removeQuestAndReferences(ServerPlayer player, UUID sessionId,
                                                                            ResourceLocation bookId, String revision,
                                                                            ResourceLocation questId) {
        return apply(player, sessionId, bookId, revision,
                book -> DraftBookEditor.removeQuestAndReferences(book, questId));
    }

    public AuthorOperationResult<DraftEditResult> addDependency(ServerPlayer player, UUID sessionId,
                                                                 ResourceLocation bookId, String revision,
                                                                 ResourceLocation questId, ResourceLocation dependencyId) {
        return apply(player, sessionId, bookId, revision,
                book -> DraftBookEditor.addDependency(book, questId, dependencyId));
    }

    public AuthorOperationResult<DraftEditResult> removeDependency(ServerPlayer player, UUID sessionId,
                                                                    ResourceLocation bookId, String revision,
                                                                    ResourceLocation questId, ResourceLocation dependencyId) {
        return apply(player, sessionId, bookId, revision,
                book -> DraftBookEditor.removeDependency(book, questId, dependencyId));
    }

    /** Creation honors type-owned defaults; add/copy/update retain fully specified definitions. */
    public AuthorOperationResult<DraftEditResult> createTask(ServerPlayer player, UUID sessionId,
            ResourceLocation bookId, String revision, ResourceLocation questId, TaskDefinition task) {
        return apply(player, sessionId, bookId, revision, book -> DraftBookEditor.addTask(book, questId,
                new TaskDefinition(task.bookId(), task.id(), task.typeId(),
                        EntryCreationPolicy.taskConfig(book, questId, task.typeId(), task.config()), task.optional())));
    }

    public AuthorOperationResult<DraftEditResult> addTask(ServerPlayer player, UUID sessionId,
                                                           ResourceLocation bookId, String revision,
                                                           ResourceLocation questId, TaskDefinition task) {
        return apply(player, sessionId, bookId, revision, book -> DraftBookEditor.addTask(book, questId, task));
    }

    public AuthorOperationResult<DraftEditResult> copyTask(ServerPlayer player, UUID sessionId,
                                                            ResourceLocation bookId, String revision,
                                                            ResourceLocation questId, ResourceLocation sourceId,
                                                            TaskDefinition copy) {
        return apply(player, sessionId, bookId, revision,
                book -> DraftBookEditor.copyTask(book, questId, sourceId, copy));
    }

    public AuthorOperationResult<DraftEditResult> updateTask(ServerPlayer player, UUID sessionId,
                                                              ResourceLocation bookId, String revision,
                                                              ResourceLocation questId, ResourceLocation taskId,
                                                              TaskDefinition task) {
        return apply(player, sessionId, bookId, revision, book -> DraftBookEditor.updateTask(book, questId, taskId, task));
    }

    public AuthorOperationResult<DraftEditResult> removeTask(ServerPlayer player, UUID sessionId,
                                                              ResourceLocation bookId, String revision,
                                                              ResourceLocation questId, ResourceLocation taskId) {
        return apply(player, sessionId, bookId, revision, book -> DraftBookEditor.removeTask(book, questId, taskId));
    }

    public AuthorOperationResult<DraftEditResult> moveTask(ServerPlayer player, UUID sessionId,
                                                            ResourceLocation bookId, String revision,
                                                            ResourceLocation questId, ResourceLocation taskId,
                                                            int targetIndex) {
        return apply(player, sessionId, bookId, revision, book -> DraftBookEditor.moveTask(book, questId, taskId, targetIndex));
    }

    public AuthorOperationResult<DraftEditResult> addReward(ServerPlayer player, UUID sessionId,
                                                             ResourceLocation bookId, String revision,
                                                             ResourceLocation questId, RewardDefinition reward) {
        return apply(player, sessionId, bookId, revision, book -> DraftBookEditor.addReward(book, questId, reward));
    }

    public AuthorOperationResult<DraftEditResult> copyReward(ServerPlayer player, UUID sessionId,
                                                              ResourceLocation bookId, String revision,
                                                              ResourceLocation questId, ResourceLocation sourceId,
                                                              RewardDefinition copy) {
        return apply(player, sessionId, bookId, revision,
                book -> DraftBookEditor.copyReward(book, questId, sourceId, copy));
    }

    public AuthorOperationResult<DraftEditResult> updateReward(ServerPlayer player, UUID sessionId,
                                                                ResourceLocation bookId, String revision,
                                                                ResourceLocation questId, ResourceLocation rewardId,
                                                                RewardDefinition reward) {
        return apply(player, sessionId, bookId, revision, book -> DraftBookEditor.updateReward(book, questId, rewardId, reward));
    }

    public AuthorOperationResult<DraftEditResult> removeReward(ServerPlayer player, UUID sessionId,
                                                                ResourceLocation bookId, String revision,
                                                                ResourceLocation questId, ResourceLocation rewardId) {
        return apply(player, sessionId, bookId, revision, book -> DraftBookEditor.removeReward(book, questId, rewardId));
    }

    public AuthorOperationResult<DraftEditResult> moveReward(ServerPlayer player, UUID sessionId,
                                                              ResourceLocation bookId, String revision,
                                                              ResourceLocation questId, ResourceLocation rewardId,
                                                              int targetIndex) {
        return apply(player, sessionId, bookId, revision, book -> DraftBookEditor.moveReward(book, questId, rewardId, targetIndex));
    }

    public AuthorOperationResult<DraftEditResult> validate(ServerPlayer player, UUID sessionId,
                                                            ResourceLocation bookId, String revision) {
        return sessions.mutate(player, sessionId, bookId, revision, draft -> {
            List<yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic> diagnostics =
                    AuthorValidationService.full(draft.book());
            DraftEditResult result = new DraftEditResult(draft, List.of(), diagnostics);
            if (AuthorValidationService.blocksCommit(diagnostics)) {
                return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST,
                        "DRAFT_VALIDATION_FAILED", "Full draft validation failed", result);
            }
            return AuthorOperationResult.noChange("DRAFT_VALID", "Full draft validation passed", result);
        });
    }

    /** Frozen definitions still pass through normal permissions, revision checks and whole-book validation. */
    public AuthorOperationResult<DraftEditResult> pasteQuestClipboard(ServerPlayer player, UUID sessionId,
            ResourceLocation bookId, String revision, ResourceLocation chapterId, QuestClipboardSnapshot snapshot, double x, double y) {
        return apply(player, sessionId, bookId, revision, book -> snapshot.paste(book, chapterId, x, y));
    }

    /** Copy the complete chapter through the same permission/revision/validation/history transaction. */
    public AuthorOperationResult<DraftEditResult> duplicateChapter(ServerPlayer player, UUID sessionId,
            ResourceLocation bookId, String revision, ResourceLocation sourceId) {
        return apply(player, sessionId, bookId, revision, book -> ChapterCopyEdits.copy(book, sourceId));
    }

    /** Selection edits share the existing revision, validation and atomic undo boundary. */
    public AuthorOperationResult<DraftEditResult> editQuestSelection(ServerPlayer player, UUID sessionId,
            ResourceLocation bookId, String revision, java.util.Set<ResourceLocation> ids, boolean copy, double dx, double dy) {
        var frozenIds = java.util.Set.copyOf(ids);
        return apply(player, sessionId, bookId, revision, book -> copy
                ? QuestSelectionEdits.copy(book, frozenIds, dx, dy) : DraftBookEditor.removeQuestSelection(book, frozenIds));
    }

    private AuthorOperationResult<DraftEditResult> apply(ServerPlayer player, UUID sessionId,
                                                          ResourceLocation bookId, String revision,
                                                          Function<QuestBookDefinition, AuthorOperationResult<DraftChange>> operation) {
        return sessions.mutate(player, sessionId, bookId, revision, current -> {
            AuthorOperationResult<DraftChange> changed = operation.apply(current.book());
            if (!changed.success()) {
                return AuthorOperationResult.failure(changed.status(), changed.code(), changed.message());
            }
            DraftChange change = changed.value();
            List<yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic> baselineDiagnostics =
                    AuthorValidationService.full(current.book());
            List<yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic> diagnostics =
                    AuthorValidationService.incremental(change.book(), change.affectedObjects());
            if (AuthorValidationService.blocksCommit(diagnostics, baselineDiagnostics)) {
                // Invalid candidates never receive a revision and never enter session state.
                DraftEditResult result = new DraftEditResult(current, change.affectedObjects(), diagnostics);
                return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST,
                        "DRAFT_VALIDATION_FAILED", "Draft operation failed validation", result);
            }
            DraftSnapshot candidate = DraftSnapshot.from(change.book(), current.origin(), current.baseRevision());
            DraftEditResult result = new DraftEditResult(candidate, change.affectedObjects(), diagnostics);
            if (candidate.draftRevision().equals(current.draftRevision())) {
                return AuthorOperationResult.noChange("DRAFT_UNCHANGED", "Operation made no semantic change", result);
            }
            return AuthorOperationResult.success("DRAFT_UPDATED", "Draft updated", result);
        });
    }
}
