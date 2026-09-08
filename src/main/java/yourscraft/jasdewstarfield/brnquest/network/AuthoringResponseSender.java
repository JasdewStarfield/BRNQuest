package yourscraft.jasdewstarfield.brnquest.network;

import com.google.gson.Gson;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.author.AuthorOperationResult;
import yourscraft.jasdewstarfield.brnquest.author.DraftCatalogEntry;
import yourscraft.jasdewstarfield.brnquest.author.DraftSnapshot;
import yourscraft.jasdewstarfield.brnquest.author.DraftEditResult;
import yourscraft.jasdewstarfield.brnquest.author.DraftPublishResult;
import yourscraft.jasdewstarfield.brnquest.author.QuestBookDiff;
import yourscraft.jasdewstarfield.brnquest.author.EditSessionHandle;
import yourscraft.jasdewstarfield.brnquest.author.EditSessionView;
import yourscraft.jasdewstarfield.brnquest.author.SemanticDiffEntry;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import static yourscraft.jasdewstarfield.brnquest.network.AuthoringNetwork.*;

/** Sole owner of author response mapping, byte budgets and ordered packet delivery. */
final class AuthoringResponseSender {
    private static final Gson GSON = new Gson();
    private static final int MAX_REVIEW_ROWS = 128;
    private static final int MAX_REVIEW_VALUE_CHARACTERS = 240;
    private final Consumer<CustomPacketPayload> outbound;
    private final LongSupplier currentTick;

    /** A small transport seam lets tests inspect the exact packet stream without a fake player. */
    AuthoringResponseSender(Consumer<CustomPacketPayload> outbound, LongSupplier currentTick) {
        this.outbound = outbound;
        this.currentTick = currentTick;
    }

    static AuthoringResponseSender forPlayer(ServerPlayer player) {
        return new AuthoringResponseSender(payload -> BrnQuestNetwork.send(player, payload),
                () -> player.getServer().getTickCount());
    }

    private static EditorDiagnosticWire boundedDiagnostic(EditorDiagnosticWire wire) {
        return new EditorDiagnosticWire(wire.severity(), wire.code(), boundedMessage(wire.objectId()),
                boundedMessage(wire.path()), boundedMessage(wire.message()));
    }

    private static SemanticDiffWire boundedDiff(SemanticDiffWire wire) {
        return new SemanticDiffWire(wire.kind(), wire.objectKind(), boundedMessage(wire.objectId()),
                boundedMessage(wire.path()), boundedReviewValue(wire.before()), boundedReviewValue(wire.after()));
    }

    void sendCatalog(AuthorOperationResult<List<DraftCatalogEntry>> result) {
        List<CatalogEntryWire> initialEntries = result.success() ? result.value().stream()
                .limit(BrnQuestConstants.MAX_EDITOR_CATALOG_ENTRIES)
                .map(entry -> new CatalogEntryWire(entry.bookId().toString(), boundedTitle(entry.title()),
                        entry.draftRevision(), entry.origin().name()))
                .toList() : List.of();
        List<CatalogEntryWire> entries = new java.util.ArrayList<>(initialEntries);
        String code = result.success() && result.value().size() > entries.size()
                ? "DRAFT_CATALOG_TRUNCATED" : result.code();
        String json;
        do {
            CatalogResponseWire response = new CatalogResponseWire(result.status().name(), code,
                    boundedMessage(result.message()), result.success(), List.copyOf(entries));
            json = GSON.toJson(response);
            if (json.getBytes(StandardCharsets.UTF_8).length <= BrnQuestConstants.MAX_EDITOR_METADATA_BYTES) break;
            if (entries.isEmpty()) return;
            entries.removeLast();
            code = "DRAFT_CATALOG_TRUNCATED";
        } while (true);
        outbound.accept(new CatalogPayload(json));
    }

    /** Maps the authoritative preview and diff without repeating their publication gates. */
    void sendPublishReview(EditSessionHandle handle, boolean publishAllowed, ResourceLocation bookId,
                           DraftPublishResult preview, QuestBookDiff diff) {
        List<EditorDiagnosticWire> allDiagnostics = new java.util.ArrayList<>(preview.diagnostics().stream()
                .map(diagnostic -> new EditorDiagnosticWire(diagnostic.severity().name(), diagnostic.code(),
                        diagnostic.objectId(), diagnostic.path(), boundedReviewValue(diagnostic.message())))
                .toList());
        if (preview.revisionCheck() != null) {
            preview.revisionCheck().conflicts().forEach(conflict -> allDiagnostics.add(
                    new EditorDiagnosticWire("ERROR", conflict.code(), bookId.toString(), "revision",
                            boundedReviewValue(conflict.message()))));
        }
        List<SemanticDiffWire> allChanges = diff.entries().stream().map(AuthoringResponseSender::diffWire).toList();
        sendPublishReview(handle, publishAllowed, diff.fromRevision(),
                diff.toRevision(), allDiagnostics, allChanges);
    }

    static SemanticDiffWire diffWire(SemanticDiffEntry entry) {
        return new SemanticDiffWire(entry.kind().name(), entry.objectKind().name(), entry.objectId().toString(),
                entry.path(), boundedReviewValue(entry.before()), boundedReviewValue(entry.after()));
    }

    static String boundedReviewValue(String value) {
        String safe = value == null ? "" : value;
        return safe.length() <= MAX_REVIEW_VALUE_CHARACTERS ? safe
                : safe.substring(0, MAX_REVIEW_VALUE_CHARACTERS - 1) + "…";
    }

    void sendPublishReview(EditSessionHandle handle, boolean publishAllowed,
                           String fromRevision, String targetRevision,
                           List<EditorDiagnosticWire> allDiagnostics,
                           List<SemanticDiffWire> allChanges) {
        List<EditorDiagnosticWire> diagnostics = new java.util.ArrayList<>(
                allDiagnostics.stream().limit(MAX_REVIEW_ROWS)
                        .map(AuthoringResponseSender::boundedDiagnostic).toList());
        List<SemanticDiffWire> changes = new java.util.ArrayList<>(allChanges.stream().limit(MAX_REVIEW_ROWS)
                .map(AuthoringResponseSender::boundedDiff).toList());
        boolean truncated = diagnostics.size() < allDiagnostics.size() || changes.size() < allChanges.size();
        String json;
        do {
            PublishReviewWire review = new PublishReviewWire(publishAllowed, "WORKSPACE", fromRevision,
                    targetRevision, "BACKUP_AND_REPLACE", allDiagnostics.size(), allChanges.size(), truncated,
                    diagnostics, changes);
            EditSessionView view = handle.session();
            long remaining = Math.max(0L, view.expiresAtTick() - currentTick.getAsLong());
            SessionResponseWire response = new SessionResponseWire("REVIEW", "SUCCESS", "PUBLISH_REVIEW_READY",
                    "Publish review generated", handle.sessionId().toString(), view.bookId().toString(),
                    view.baseRevision(), view.draftRevision(), view.savedRevision(), remaining, 0, 0,
                    view.undoSteps(), view.redoSteps(), List.of(), review, List.of());
            json = GSON.toJson(response);
            if (json.getBytes(StandardCharsets.UTF_8).length <= BrnQuestConstants.MAX_EDITOR_METADATA_BYTES) break;
            truncated = true;
            if (!changes.isEmpty()) changes.removeLast();
            else if (!diagnostics.isEmpty()) diagnostics.removeLast();
            else {
                sendFailure("REVIEW", AuthorOperationResult.Status.IO_FAILURE,
                        "PUBLISH_REVIEW_TOO_LARGE", "Publish review exceeds the editor protocol limit");
                return;
            }
        } while (true);
        outbound.accept(new SessionPayload(json));
    }

    void sendDraft(String action, String code, String message,
                   EditSessionHandle handle, DraftSnapshot draft, Runnable onTooLarge) {
        String json = NativeBookJson.encode(draft.book());
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > BrnQuestConstants.MAX_BOOK_BYTES
                || draft.book().quests().size() > BrnQuestConstants.MAX_QUESTS) {
            onTooLarge.run();
            sendFailure(action, AuthorOperationResult.Status.INVALID_REQUEST,
                    "DRAFT_TOO_LARGE", "Draft exceeds the editor protocol limits");
            return;
        }
        List<String> chunks = BrnQuestNetwork.split(json, BrnQuestNetwork.BOOK_CHUNK_CHARACTERS);
        sendSession(action, AuthorOperationResult.Status.SUCCESS, code,
                message, handle, chunks.size(), bytes.length);
        for (int index = 0; index < chunks.size(); index++) {
            outbound.accept(new DraftChunkPayload(handle.sessionId().toString(),
                    draft.draftRevision(), index, chunks.get(index)));
        }
    }

    void sendPositionPatch(String code, String message,
                           EditSessionHandle handle, DraftSnapshot draft,
                           List<PositionWire> positions, Runnable onTooLarge) {
        EditSessionView view = handle.session();
        long remaining = Math.max(0L, view.expiresAtTick() - currentTick.getAsLong());
        SessionResponseWire response = new SessionResponseWire("PATCH", "SUCCESS", code,
                boundedMessage(message), handle.sessionId().toString(), view.bookId().toString(),
                view.baseRevision(), view.draftRevision(), view.savedRevision(), remaining, 0, 0,
                view.undoSteps(), view.redoSteps(), List.of(), null,
                positions == null ? List.of() : positions);
        String json = GSON.toJson(response);
        if (json.getBytes(StandardCharsets.UTF_8).length > BrnQuestConstants.MAX_EDITOR_METADATA_BYTES) {
            // Extremely long IDs can make even a bounded 4096-node delta larger
            // than metadata. Fall back to the verified chunk transport so a
            // successful server mutation never strands the client on a stale revision.
            sendDraft("MUTATE", code, message, handle, draft, onTooLarge);
            return;
        }
        outbound.accept(new SessionPayload(json));
    }

    void sendSession(String action, AuthorOperationResult.Status status,
                     String code, String message, EditSessionHandle handle,
                     int chunks, int decodedBytes) {
        EditSessionView view = handle.session();
        long remaining = Math.max(0L, view.expiresAtTick() - currentTick.getAsLong());
        SessionResponseWire response = new SessionResponseWire(action, status.name(), code,
                boundedMessage(message), handle.sessionId().toString(), view.bookId().toString(),
                view.baseRevision(), view.draftRevision(), view.savedRevision(), remaining, chunks, decodedBytes,
                view.undoSteps(), view.redoSteps(), List.of(), null, List.of());
        outbound.accept(new SessionPayload(GSON.toJson(response)));
    }

    void sendFailure(String action, AuthorOperationResult.Status status,
                     String code, String message) {
        sendFailure(action, status, code, message, List.of());
    }

    void sendFailure(String action, AuthorOperationResult.Status status,
                     String code, String message, List<EditorDiagnosticWire> diagnostics) {
        SessionResponseWire response = new SessionResponseWire(action, status.name(), code,
                boundedMessage(message), "", "", "", "", "", 0L, 0, 0,
                diagnostics.stream().limit(8).map(AuthoringResponseSender::boundedDiagnostic).toList());
        outbound.accept(new SessionPayload(GSON.toJson(response)));
    }

    static EditorDiagnosticWire mutationDiagnostic(String sourceId, IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "Invalid editor mutation" : exception.getMessage();
        String path = message.startsWith("Item config") ? "config.item"
                : message.startsWith("Reward claim policy") ? "claim_policy" : "";
        return new EditorDiagnosticWire("ERROR", "INVALID_EDITOR_MUTATION",
                sourceId == null ? "" : sourceId, path, boundedMessage(message));
    }

    static List<EditorDiagnosticWire> diagnosticWires(
            List<yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic> diagnostics) {
        return diagnostics.stream().limit(8).map(diagnostic -> new EditorDiagnosticWire(
                diagnostic.severity().name(), diagnostic.code(), boundedMessage(diagnostic.objectId()),
                boundedMessage(diagnostic.path()), boundedMessage(diagnostic.message()))).toList();
    }

    private static List<EditorDiagnosticWire> mutationDiagnosticWires(String action,
            List<yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic> diagnostics) {
        boolean typedUpdate = "UPDATE_TASK".equals(action) || "UPDATE_REWARD".equals(action);
        return diagnostics.stream().limit(8).map(diagnostic -> {
            String path = diagnostic.path();
            if (typedUpdate && path.isBlank()
                    && ("BQV-119".equals(diagnostic.code()) || "BQV-120".equals(diagnostic.code()))) {
                // Codec errors concern the complete raw map when no descriptor can identify one field.
                path = "config";
            }
            return new EditorDiagnosticWire(diagnostic.severity().name(), diagnostic.code(),
                    boundedMessage(diagnostic.objectId()), boundedMessage(path), boundedMessage(diagnostic.message()));
        }).toList();
    }

    static String boundedMessage(String message) {
        String value = message == null ? "" : message;
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    static String boundedTitle(String title) {
        String value = title == null ? "" : title;
        return value.length() <= 256 ? value : value.substring(0, 256);
    }

    /** Preserves typed-field diagnostics while consuming the service's failure unchanged. */
    private void sendMutationFailure(AuthorOperationResult<DraftEditResult> result, String action, String sourceId) {
        String message = result.message();
        List<EditorDiagnosticWire> diagnostics = List.of();
        if (result.value() != null && !result.value().diagnostics().isEmpty()) {
            var first = result.value().diagnostics().getFirst();
            message += ": " + first.code() + " " + first.message();
            diagnostics = mutationDiagnosticWires(action, result.value().diagnostics());
        } else if ("UPDATE_TASK".equals(action) || "UPDATE_REWARD".equals(action)) {
            String path = "DUPLICATE_TYPED_ID".equals(result.code())
                    || "RETIRED_TYPED_ID".equals(result.code()) ? "id" : "";
            diagnostics = List.of(new EditorDiagnosticWire("ERROR", result.code(),
                    sourceId, path, result.message()));
        }
        sendFailure("MUTATE", result.status(), result.code(), message, diagnostics);
    }
    void sendDecodeFailure(String action, AuthoringRequestDecoder.Failure failure) {
        List<EditorDiagnosticWire> diagnostics = ("MUTATE".equals(action) || "UPDATE".equals(action))
                && !failure.path().isBlank() ? List.of(new EditorDiagnosticWire("ERROR", failure.code(),
                failure.objectId(), failure.path(), failure.message())) : List.of();
        sendFailure(action, AuthorOperationResult.Status.INVALID_REQUEST, failure.code(), failure.message(), diagnostics);
    }
    void sendSaveFailure(AuthorOperationResult<yourscraft.jasdewstarfield.brnquest.author.DraftSaveResult> saved) {
            String message = saved.message();
            if (saved.value() != null && saved.value().revisionCheck() != null
                    && saved.value().revisionCheck().hasConflicts()) {
                var first = saved.value().revisionCheck().conflicts().getFirst();
                message += ": expected " + shortRevision(first.expectedRevision())
                        + ", actual " + shortRevision(first.actualRevision());
            } else if (saved.value() != null && !saved.value().diagnostics().isEmpty()) {
                var first = saved.value().diagnostics().getFirst();
                message += ": " + first.code() + " " + first.message();
            }
            sendFailure("SAVE", saved.status(), saved.code(), message);
    }

    /** Compact revision labels preserve the existing error summary and phase log format. */
    static String shortRevision(String revision) {
        if (revision == null || revision.isBlank()) return "<none>";
        return revision.length() <= 12 ? revision : revision.substring(0, 12);
    }
    static EditorDiagnosticWire mutationDiagnostic(EditorMutationWire wire, IllegalArgumentException exception) {
        return mutationDiagnostic(wire.sourceId(), exception);
    }

    static EditorDiagnosticWire mutationDiagnostic(AuthoringRequestDecoder.MutationRequest request, IllegalArgumentException exception) {
        return mutationDiagnostic(request.sourceId() == null ? "" : request.sourceId().toString(), exception);
    }

    static List<EditorDiagnosticWire> mutationDiagnosticWires(EditorMutationWire wire,
            List<yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic> diagnostics) {
        return mutationDiagnosticWires(wire.action(), diagnostics);
    }

    void sendMutationFailure(AuthorOperationResult<DraftEditResult> result, EditorMutationWire wire) {
        sendMutationFailure(result, wire.action(), wire.sourceId());
    }

    void sendMutationFailure(AuthorOperationResult<DraftEditResult> result, AuthoringRequestDecoder.MutationRequest request) {
        sendMutationFailure(result, request.action().wireName(), request.sourceId() == null ? "" : request.sourceId().toString());
    }

    void sendPositionPatch(String code, String message, EditSessionHandle handle, DraftSnapshot draft,
                           AuthoringRequestDecoder.MutationRequest request, Runnable onTooLarge) {
        var positions = request.positions().entrySet().stream().map(entry ->
                new PositionWire(entry.getKey().toString(), entry.getValue().x(), entry.getValue().y())).toList();
        sendPositionPatch(code, message, handle, draft, positions, onTooLarge);
    }
}
