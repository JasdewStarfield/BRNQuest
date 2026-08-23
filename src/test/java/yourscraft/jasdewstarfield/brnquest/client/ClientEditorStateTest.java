package yourscraft.jasdewstarfield.brnquest.client;

import com.google.gson.Gson;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.author.DraftOrigin;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.network.AuthoringNetwork;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClientEditorStateTest {
    private static final Gson GSON = new Gson();
    private final ClientEditorState state = ClientEditorState.get();

    @BeforeEach void reset() {
        state.resetForTest();
    }

    @Test void deniedCatalogNeverExposesEditorEntry() {
        state.beginCatalogRequest();
        var response = new AuthoringNetwork.CatalogResponseWire("FORBIDDEN", "EDITOR_PERMISSION_REQUIRED",
                "denied", false, List.of());

        state.acceptCatalog(GSON.toJson(response));

        assertFalse(state.allowed());
        assertEquals(ClientEditorState.Mode.VIEW, state.mode());
        assertTrue(state.catalog().isEmpty());
    }

    @Test void catalogPrioritizesDisplayedBookAndMovesBackupLikeEntriesToTheEnd() {
        ResourceLocation displayed = ResourceLocation.parse("test:current");
        var response = new AuthoringNetwork.CatalogResponseWire("SUCCESS", "DRAFT_CATALOG", "ok", true,
                List.of(
                        new AuthoringNetwork.CatalogEntryWire("test:backup_old", "Backup old", "r1",
                                DraftOrigin.ACTIVE.name()),
                        new AuthoringNetwork.CatalogEntryWire("test:zeta", "Zeta", "r2",
                                DraftOrigin.EMPTY.name()),
                        new AuthoringNetwork.CatalogEntryWire(displayed.toString(), "Current", "r3",
                                DraftOrigin.ACTIVE.name()),
                        new AuthoringNetwork.CatalogEntryWire("test:alpha", "Alpha", "r4",
                                DraftOrigin.EMPTY.name()),
                        new AuthoringNetwork.CatalogEntryWire("test:old_cn", "旧备份", "r5",
                                DraftOrigin.UNKNOWN.name())));
        state.acceptCatalog(GSON.toJson(response));

        assertEquals(List.of("test:current", "test:alpha", "test:zeta", "test:backup_old", "test:old_cn"),
                state.orderedCatalog(displayed).stream().map(entry -> entry.bookId().toString()).toList());
        assertEquals(List.of("test:old_cn"), state.filteredCatalog(displayed, "备份").stream()
                .map(entry -> entry.bookId().toString()).toList());
        assertEquals(List.of("test:backup_old"), state.filteredCatalog(displayed, "BACKUP_OLD").stream()
                .map(entry -> entry.bookId().toString()).toList());
    }

    @Test void verifiedDraftBecomesSeparateEditableSnapshot() {
        ResourceLocation bookId = ResourceLocation.parse("test:editor");
        QuestBookDefinition book = new QuestBookDefinition(bookId, 1, "Editor book",
                List.of(), List.of(), Map.of());
        QuestBookSnapshot snapshot = QuestBookSnapshot.of(book);
        var catalog = new AuthoringNetwork.CatalogResponseWire("SUCCESS", "DRAFT_CATALOG", "ok", true,
                List.of(new AuthoringNetwork.CatalogEntryWire(bookId.toString(), book.title(),
                        snapshot.revision(), DraftOrigin.EMPTY.name())));
        state.acceptCatalog(GSON.toJson(catalog));
        assertTrue(state.beginOpen(bookId));

        UUID sessionId = UUID.randomUUID();
        String json = NativeBookJson.encode(book);
        var opened = new AuthoringNetwork.SessionResponseWire("OPEN", "SUCCESS", "SESSION_OPENED", "ok",
                sessionId.toString(), bookId.toString(), "", snapshot.revision(), snapshot.revision(),
                36_000L, 1, json.getBytes(StandardCharsets.UTF_8).length);
        state.acceptSession(GSON.toJson(opened));

        assertEquals(ClientEditorState.Mode.RECEIVING_DRAFT, state.mode());
        assertTrue(state.acceptDraftChunk(sessionId.toString(), snapshot.revision(), 0, json));
        assertEquals(ClientEditorState.Mode.EDITING, state.mode());
        assertEquals(book, state.draft().orElseThrow().book());
        assertFalse(state.dirty());
        assertEquals(36_000L, state.remainingLeaseTicks());
    }

    @Test void authoritativeQuestUpdateKeepsPreviousDraftVisibleUntilReplacementIsVerified() {
        ResourceLocation bookId = ResourceLocation.parse("test:update");
        QuestBookDefinition before = new QuestBookDefinition(bookId, 1, "Before",
                List.of(), List.of(), Map.of());
        QuestBookSnapshot beforeSnapshot = QuestBookSnapshot.of(before);
        state.acceptCatalog(GSON.toJson(new AuthoringNetwork.CatalogResponseWire(
                "SUCCESS", "DRAFT_CATALOG", "ok", true, List.of())));
        assertTrue(state.beginOpenCurrent(bookId));
        UUID sessionId = UUID.randomUUID();
        acceptTransfer("OPEN", sessionId, beforeSnapshot, beforeSnapshot.revision());

        QuestBookDefinition after = new QuestBookDefinition(bookId, 1, "After",
                List.of(), List.of(), Map.of());
        QuestBookSnapshot afterSnapshot = QuestBookSnapshot.of(after);
        String afterJson = NativeBookJson.encode(after);
        assertTrue(state.beginMutation());
        assertEquals(ClientEditorState.Mode.MUTATING, state.mode());
        var update = new AuthoringNetwork.SessionResponseWire("MUTATE", "SUCCESS", "DRAFT_UPDATED", "ok",
                sessionId.toString(), bookId.toString(), "", afterSnapshot.revision(), beforeSnapshot.revision(),
                36_000L, 1, afterJson.getBytes(StandardCharsets.UTF_8).length);

        state.acceptSession(GSON.toJson(update));

        assertEquals("Before", state.draft().orElseThrow().book().title());
        assertTrue(state.editing());
        assertTrue(state.acceptDraftChunk(sessionId.toString(), afterSnapshot.revision(), 0, afterJson));
        assertEquals("After", state.draft().orElseThrow().book().title());
        assertTrue(state.dirty());
    }

    @Test void explicitSaveAdvancesSavedRevisionWithoutReplacingVerifiedDraft() {
        ResourceLocation bookId = ResourceLocation.parse("test:save");
        QuestBookDefinition before = new QuestBookDefinition(bookId, 1, "Before",
                List.of(), List.of(), Map.of());
        QuestBookSnapshot beforeSnapshot = QuestBookSnapshot.of(before);
        state.acceptCatalog(GSON.toJson(new AuthoringNetwork.CatalogResponseWire(
                "SUCCESS", "DRAFT_CATALOG", "ok", true, List.of())));
        assertTrue(state.beginOpenCurrent(bookId));
        UUID sessionId = UUID.randomUUID();
        acceptTransfer("OPEN", sessionId, beforeSnapshot, beforeSnapshot.revision());

        QuestBookDefinition after = new QuestBookDefinition(bookId, 1, "After",
                List.of(), List.of(), Map.of());
        QuestBookSnapshot afterSnapshot = QuestBookSnapshot.of(after);
        acceptTransfer("UPDATE", sessionId, afterSnapshot, beforeSnapshot.revision());
        assertTrue(state.dirty());
        assertTrue(state.beginSave().isPresent());
        assertEquals(ClientEditorState.Mode.SAVING, state.mode());

        var saved = new AuthoringNetwork.SessionResponseWire("SAVE", "SUCCESS", "DRAFT_SAVED", "saved",
                sessionId.toString(), bookId.toString(), "", afterSnapshot.revision(), afterSnapshot.revision(),
                36_000L, 0, 0);
        state.acceptSession(GSON.toJson(saved));

        assertEquals(ClientEditorState.Mode.EDITING, state.mode());
        assertFalse(state.dirty());
        assertEquals("After", state.draft().orElseThrow().book().title());
    }

    @Test void publishPipelineCanStartFromDirtyDraftAndReturnsToSavedEditingState() {
        ResourceLocation bookId = ResourceLocation.parse("test:publish");
        QuestBookSnapshot before = QuestBookSnapshot.of(new QuestBookDefinition(bookId, 1, "Before",
                List.of(), List.of(), Map.of()));
        QuestBookSnapshot after = QuestBookSnapshot.of(new QuestBookDefinition(bookId, 1, "After",
                List.of(), List.of(), Map.of()));
        state.acceptCatalog(GSON.toJson(new AuthoringNetwork.CatalogResponseWire(
                "SUCCESS", "DRAFT_CATALOG", "ok", true, List.of())));
        assertTrue(state.beginOpenCurrent(bookId));
        UUID sessionId = UUID.randomUUID();
        acceptTransfer("OPEN", sessionId, before, before.revision());
        acceptTransfer("UPDATE", sessionId, after, before.revision());

        assertTrue(state.beginPublish().isPresent());
        assertEquals(ClientEditorState.Mode.PUBLISHING, state.mode());
        var applied = new AuthoringNetwork.SessionResponseWire("PUBLISH", "SUCCESS", "PUBLISH_APPLY_COMPLETE", "ok",
                sessionId.toString(), bookId.toString(), "", after.revision(), after.revision(),
                36_000L, 0, 0);
        state.acceptSession(GSON.toJson(applied));

        assertEquals(ClientEditorState.Mode.EDITING, state.mode());
        assertFalse(state.dirty());
        assertEquals("PUBLISH_APPLY_COMPLETE", state.statusCode());
    }

    @Test void publishReviewIsAcceptedOnlyForTheCurrentAuthoritativeRevision() {
        ResourceLocation bookId = ResourceLocation.parse("test:publish_review");
        QuestBookSnapshot snapshot = QuestBookSnapshot.of(new QuestBookDefinition(bookId, 1, "Review",
                List.of(), List.of(), Map.of()));
        state.acceptCatalog(GSON.toJson(new AuthoringNetwork.CatalogResponseWire(
                "SUCCESS", "DRAFT_CATALOG", "ok", true, List.of())));
        assertTrue(state.beginOpenCurrent(bookId));
        UUID sessionId = UUID.randomUUID();
        acceptTransfer("OPEN", sessionId, snapshot, snapshot.revision());
        assertTrue(state.beginPublishReview().isPresent());

        var review = new AuthoringNetwork.PublishReviewWire(true, "WORKSPACE", "old",
                snapshot.revision(), "BACKUP_AND_REPLACE", 1, 1, false,
                List.of(new AuthoringNetwork.EditorDiagnosticWire("WARN", "BQR-TEST", bookId.toString(),
                        "title", "warning")),
                List.of(new AuthoringNetwork.SemanticDiffWire("PROPERTY_CHANGED", "BOOK",
                        bookId.toString(), "title", "Before", "Review")));
        state.acceptSession(GSON.toJson(new AuthoringNetwork.SessionResponseWire(
                "REVIEW", "SUCCESS", "PUBLISH_REVIEW_READY", "ok", sessionId.toString(),
                bookId.toString(), "", snapshot.revision(), snapshot.revision(), 36_000L,
                0, 0, 0, 0, List.of(), review)));

        assertEquals(ClientEditorState.Mode.EDITING, state.mode());
        assertEquals(review, state.pollPublishReview().orElseThrow());
        assertTrue(state.pollPublishReview().isEmpty());
    }

    @Test void leaseHeartbeatCannotUnlockAnInFlightPublish() {
        ResourceLocation bookId = ResourceLocation.parse("test:publish_renewal");
        QuestBookSnapshot snapshot = QuestBookSnapshot.of(new QuestBookDefinition(bookId, 1, "Publish renewal",
                List.of(), List.of(), Map.of()));
        state.acceptCatalog(GSON.toJson(new AuthoringNetwork.CatalogResponseWire(
                "SUCCESS", "DRAFT_CATALOG", "ok", true, List.of())));
        assertTrue(state.beginOpenCurrent(bookId));
        UUID sessionId = UUID.randomUUID();
        acceptTransfer("OPEN", sessionId, snapshot, snapshot.revision());

        // Model a heartbeat that was already in flight when the player confirmed publish.
        for (int tick = 0; tick < 20 * 30; tick++) state.tick();
        assertTrue(state.pollRenewRequest().isPresent());
        assertTrue(state.beginPublish().isPresent());
        var renewed = new AuthoringNetwork.SessionResponseWire("RENEW", "SUCCESS", "SESSION_RENEWED", "ok",
                sessionId.toString(), bookId.toString(), "", snapshot.revision(), snapshot.revision(),
                36_000L, 0, 0);

        state.acceptSession(GSON.toJson(renewed));

        assertEquals(ClientEditorState.Mode.PUBLISHING, state.mode());
        assertTrue(state.beginPublish().isEmpty());
        assertTrue(state.pollRenewRequest().isEmpty());
    }

    @Test void historyCountsOnlyUnlockAvailableServerConfirmedActions() {
        ResourceLocation bookId = ResourceLocation.parse("test:history_controls");
        QuestBookSnapshot before = QuestBookSnapshot.of(new QuestBookDefinition(bookId, 1, "Before",
                List.of(), List.of(), Map.of()));
        QuestBookSnapshot after = QuestBookSnapshot.of(new QuestBookDefinition(bookId, 1, "After",
                List.of(), List.of(), Map.of()));
        state.acceptCatalog(GSON.toJson(new AuthoringNetwork.CatalogResponseWire(
                "SUCCESS", "DRAFT_CATALOG", "ok", true, List.of())));
        assertTrue(state.beginOpenCurrent(bookId));
        UUID sessionId = UUID.randomUUID();
        acceptTransfer("OPEN", sessionId, before, before.revision());
        acceptHistoryTransfer(sessionId, after, before.revision(), 1, 0, "DRAFT_UPDATED");

        assertTrue(state.canUndo());
        assertFalse(state.canRedo());
        assertTrue(state.beginUndo().isPresent());
        assertTrue(state.beginRedo().isEmpty());
        acceptHistoryTransfer(sessionId, before, before.revision(), 0, 1, "DRAFT_UNDONE");

        assertFalse(state.canUndo());
        assertTrue(state.canRedo());
        assertFalse(state.dirty());
    }

    @Test void mismatchedDraftRevisionIsRejectedWithoutEnteringEditMode() {
        ResourceLocation bookId = ResourceLocation.parse("test:mismatch");
        QuestBookDefinition book = new QuestBookDefinition(bookId, 1, "Mismatch",
                List.of(), List.of(), Map.of());
        String json = NativeBookJson.encode(book);
        var catalog = new AuthoringNetwork.CatalogResponseWire("SUCCESS", "DRAFT_CATALOG", "ok", true,
                List.of(new AuthoringNetwork.CatalogEntryWire(bookId.toString(), book.title(),
                        "wrong", DraftOrigin.EMPTY.name())));
        state.acceptCatalog(GSON.toJson(catalog));
        assertTrue(state.beginOpen(bookId));
        UUID sessionId = UUID.randomUUID();
        var opened = new AuthoringNetwork.SessionResponseWire("OPEN", "SUCCESS", "SESSION_OPENED", "ok",
                sessionId.toString(), bookId.toString(), "", "wrong", "wrong",
                36_000L, 1, json.getBytes(StandardCharsets.UTF_8).length);
        state.acceptSession(GSON.toJson(opened));

        assertFalse(state.acceptDraftChunk(sessionId.toString(), "wrong", 0, json));
        assertEquals(ClientEditorState.Mode.ERROR, state.mode());
        assertTrue(state.draft().isEmpty());
    }

    @Test void closingScreenDuringOpenSchedulesGrantedLeaseForImmediateClose() {
        ResourceLocation bookId = ResourceLocation.parse("test:orphan_guard");
        var catalog = new AuthoringNetwork.CatalogResponseWire("SUCCESS", "DRAFT_CATALOG", "ok", true,
                List.of(new AuthoringNetwork.CatalogEntryWire(bookId.toString(), "Guard", "revision",
                        DraftOrigin.EMPTY.name())));
        state.acceptCatalog(GSON.toJson(catalog));
        assertTrue(state.beginOpen(bookId));
        state.abandonLocalSession();
        UUID sessionId = UUID.randomUUID();
        var opened = new AuthoringNetwork.SessionResponseWire("OPEN", "SUCCESS", "SESSION_OPENED", "ok",
                sessionId.toString(), bookId.toString(), "", "revision", "revision",
                36_000L, 1, 2);

        state.acceptSession(GSON.toJson(opened));

        assertEquals(new ClientEditorState.LeaseRequest(sessionId, "revision"),
                state.pollImmediateClose().orElseThrow());
        assertEquals(ClientEditorState.Mode.CLOSING, state.mode());
    }

    @Test void disconnectClearsServerSpecificCatalogAndDraftState() {
        ResourceLocation bookId = ResourceLocation.parse("test:disconnect");
        var catalog = new AuthoringNetwork.CatalogResponseWire("SUCCESS", "DRAFT_CATALOG", "ok", true,
                List.of(new AuthoringNetwork.CatalogEntryWire(bookId.toString(), "Disconnect", "revision",
                        DraftOrigin.EMPTY.name())));
        state.acceptCatalog(GSON.toJson(catalog));
        assertTrue(state.allowed());

        state.disconnected();

        assertFalse(state.allowed());
        assertTrue(state.catalog().isEmpty());
        assertTrue(state.draft().isEmpty());
        assertEquals(ClientEditorState.Mode.VIEW, state.mode());
    }

    @Test void delayedOpenFromPreviousConnectionCannotRestoreAStaleDraft() {
        ResourceLocation bookId = ResourceLocation.parse("test:old_connection");
        QuestBookDefinition book = new QuestBookDefinition(bookId, 1, "Old",
                List.of(), List.of(), Map.of());
        QuestBookSnapshot snapshot = QuestBookSnapshot.of(book);
        state.disconnected();
        String json = NativeBookJson.encode(book);
        var delayed = new AuthoringNetwork.SessionResponseWire("OPEN", "SUCCESS", "SESSION_OPENED", "late",
                UUID.randomUUID().toString(), bookId.toString(), "", snapshot.revision(), snapshot.revision(),
                36_000L, 1, json.getBytes(StandardCharsets.UTF_8).length);

        state.acceptSession(GSON.toJson(delayed));

        assertEquals(ClientEditorState.Mode.VIEW, state.mode());
        assertTrue(state.draft().isEmpty());
        assertFalse(state.hasLease());
    }

    @Test void verifiedPositionPatchUpdatesDraftWithoutAFullBookTransfer() {
        ResourceLocation bookId = ResourceLocation.parse("test:position_patch");
        QuestBookSnapshot before = positionedBook(bookId, 1.0, 2.0);
        QuestBookSnapshot after = positionedBook(bookId, 7.0, 9.0);
        state.acceptCatalog(GSON.toJson(new AuthoringNetwork.CatalogResponseWire(
                "SUCCESS", "DRAFT_CATALOG", "ok", true, List.of())));
        assertTrue(state.beginOpenCurrent(bookId));
        UUID sessionId = UUID.randomUUID();
        acceptTransfer("OPEN", sessionId, before, before.revision());
        assertTrue(state.beginMutation());
        var patch = new AuthoringNetwork.SessionResponseWire("PATCH", "SUCCESS", "QUESTS_MOVED", "ok",
                sessionId.toString(), bookId.toString(), "", after.revision(), before.revision(),
                36_000L, 0, 0, 1, 0, List.of(), null,
                List.of(new AuthoringNetwork.PositionWire("test:quest", 7.0, 9.0)));

        state.acceptSession(GSON.toJson(patch));

        QuestDefinition moved = state.draft().orElseThrow().book().quests().getFirst();
        assertEquals(7.0, moved.x());
        assertEquals(9.0, moved.y());
        assertEquals(after.revision(), state.draftRevision());
        assertEquals(ClientEditorState.Mode.EDITING, state.mode());
    }

    @Test void rejectedMutationRetainsStructuredFieldDiagnosticsForTheOpenForm() {
        ResourceLocation bookId = ResourceLocation.parse("test:diagnostics");
        QuestBookSnapshot snapshot = QuestBookSnapshot.of(new QuestBookDefinition(bookId, 1, "Diagnostics",
                List.of(), List.of(), Map.of()));
        state.acceptCatalog(GSON.toJson(new AuthoringNetwork.CatalogResponseWire(
                "SUCCESS", "DRAFT_CATALOG", "ok", true, List.of())));
        assertTrue(state.beginOpenCurrent(bookId));
        acceptTransfer("OPEN", UUID.randomUUID(), snapshot, snapshot.revision());
        assertTrue(state.beginMutation());
        var diagnostic = new AuthoringNetwork.EditorDiagnosticWire(
                "ERROR", "BQA-T-TYPE", "test:task", "config.count", "Value does not match INTEGER");
        var rejected = new AuthoringNetwork.SessionResponseWire("MUTATE", "INVALID_REQUEST",
                "INVALID_EDITOR_MUTATION", "invalid count", "", "", "", "", "", 0L, 0, 0,
                List.of(diagnostic));

        state.acceptSession(GSON.toJson(rejected));

        assertEquals(ClientEditorState.Mode.ERROR, state.mode());
        assertEquals(List.of(diagnostic), state.diagnostics());
        assertEquals(snapshot.book(), state.draft().orElseThrow().book());
        assertTrue(state.beginMutation(), "Corrected form input must remain retryable after validation rejection");
    }

    @Test void revisionConflictLocksWritesAndOffersExplicitRecovery() {
        ResourceLocation bookId = ResourceLocation.parse("test:conflict");
        QuestBookSnapshot snapshot = QuestBookSnapshot.of(new QuestBookDefinition(bookId, 1, "Conflict",
                List.of(), List.of(), Map.of()));
        state.acceptCatalog(GSON.toJson(new AuthoringNetwork.CatalogResponseWire(
                "SUCCESS", "DRAFT_CATALOG", "ok", true, List.of())));
        assertTrue(state.beginOpenCurrent(bookId));
        acceptTransfer("OPEN", UUID.randomUUID(), snapshot, snapshot.revision());
        assertTrue(state.beginMutation());

        state.acceptSession(GSON.toJson(new AuthoringNetwork.SessionResponseWire(
                "MUTATE", "CONFLICT", "STALE_DRAFT_REVISION", "server advanced", "", "", "", "", "",
                0L, 0, 0)));

        assertEquals(ClientEditorState.Mode.ERROR, state.mode());
        assertTrue(state.recoverableConflict());
        assertFalse(state.beginMutation());
        assertTrue(state.beginRecovery());
        assertEquals(ClientEditorState.Mode.MUTATING, state.mode());
    }

    private void acceptTransfer(String action, UUID sessionId, QuestBookSnapshot snapshot, String savedRevision) {
        String json = NativeBookJson.encode(snapshot.book());
        var response = new AuthoringNetwork.SessionResponseWire(action, "SUCCESS", "SESSION_OPENED", "ok",
                sessionId.toString(), snapshot.book().id().toString(), "", snapshot.revision(), savedRevision,
                36_000L, 1, json.getBytes(StandardCharsets.UTF_8).length);
        state.acceptSession(GSON.toJson(response));
        assertTrue(state.acceptDraftChunk(sessionId.toString(), snapshot.revision(), 0, json));
    }

    private void acceptHistoryTransfer(UUID sessionId, QuestBookSnapshot snapshot, String savedRevision,
                                       int undoSteps, int redoSteps, String code) {
        String json = NativeBookJson.encode(snapshot.book());
        var response = new AuthoringNetwork.SessionResponseWire("MUTATE", "SUCCESS", code, "ok",
                sessionId.toString(), snapshot.book().id().toString(), "", snapshot.revision(), savedRevision,
                36_000L, 1, json.getBytes(StandardCharsets.UTF_8).length, undoSteps, redoSteps, List.of());
        state.acceptSession(GSON.toJson(response));
        assertTrue(state.acceptDraftChunk(sessionId.toString(), snapshot.revision(), 0, json));
    }

    private static QuestBookSnapshot positionedBook(ResourceLocation bookId, double x, double y) {
        ResourceLocation groupId = ResourceLocation.parse("test:group");
        ResourceLocation chapterId = ResourceLocation.parse("test:chapter");
        QuestDefinition quest = new QuestDefinition(bookId, ResourceLocation.parse("test:quest"), chapterId,
                "Quest", "", "", "", x, y, List.of(), List.of(), List.of(), "");
        ChapterDefinition chapter = new ChapterDefinition(bookId, chapterId, groupId, "Chapter", "", 0,
                List.of(quest));
        return QuestBookSnapshot.of(new QuestBookDefinition(bookId, 1, "Book",
                List.of(new ChapterGroupDefinition(bookId, groupId, "Group", 0)), List.of(chapter), Map.of()));
    }
}
