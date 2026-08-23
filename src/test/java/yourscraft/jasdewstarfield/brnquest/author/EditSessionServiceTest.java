package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EditSessionServiceTest {
    private static final UUID ALICE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test void enforcesOneWriterPerBookButIsolatesDifferentServers() {
        EditSessionService service = new EditSessionService();
        DraftSnapshot draft = draft("test:main");
        Object serverA = new Object();
        Object serverB = new Object();

        EditSessionHandle first = service.openAuthorized(serverA, ALICE, "Alice", draft, 10L, 100L).value();
        AuthorOperationResult<EditSessionHandle> repeated =
                service.openAuthorized(serverA, ALICE, "Alice", draft, 11L, 100L);
        AuthorOperationResult<EditSessionHandle> conflict =
                service.openAuthorized(serverA, BOB, "Bob", draft, 11L, 100L);

        assertEquals(AuthorOperationResult.Status.NO_CHANGE, repeated.status());
        assertEquals(first.sessionId(), repeated.value().sessionId());
        assertEquals(AuthorOperationResult.Status.CONFLICT, conflict.status());
        assertEquals("BOOK_ALREADY_EDITED", conflict.code());
        assertEquals(AuthorOperationResult.Status.SUCCESS,
                service.openAuthorized(serverB, BOB, "Bob", draft, 11L, 100L).status());
    }

    @Test void rejectsStaleRevisionAndHidesSessionTokenFromObservers() {
        EditSessionService service = new EditSessionService();
        Object server = new Object();
        DraftSnapshot draft = draft("test:stale");
        EditSessionHandle handle = service.openAuthorized(server, ALICE, "Alice", draft, 0L, 100L).value();

        AuthorOperationResult<EditSessionHandle> stale =
                service.renewAuthorized(server, ALICE, handle.sessionId(), "old-client-revision", 1L, 100L);
        EditSessionView visible = service.inspectAuthorized(server, draft.book().id(), 1L).value();

        assertEquals(AuthorOperationResult.Status.CONFLICT, stale.status());
        assertEquals("STALE_DRAFT_REVISION", stale.code());
        assertEquals(ALICE, visible.editorId());
        assertEquals(draft.draftRevision(), visible.draftRevision());
        assertFalse(EditSessionView.class.getRecordComponents()[0].getName().equals("sessionId"));
    }

    @Test void expiresAndReleasesLeasesForDisconnectedEditors() {
        EditSessionService service = new EditSessionService();
        Object server = new Object();
        DraftSnapshot draft = draft("test:lifecycle");
        EditSessionHandle expired = service.openAuthorized(server, ALICE, "Alice", draft, 10L, 5L).value();

        assertEquals(AuthorOperationResult.Status.EXPIRED,
                service.renewAuthorized(server, ALICE, expired.sessionId(), draft.draftRevision(), 15L, 5L).status());
        assertEquals(AuthorOperationResult.Status.SUCCESS,
                service.openAuthorized(server, BOB, "Bob", draft, 15L, 5L).status());

        service.releasePlayer(server, BOB);
        assertEquals(AuthorOperationResult.Status.NOT_FOUND,
                service.inspectAuthorized(server, draft.book().id(), 16L).status());
        assertEquals(AuthorOperationResult.Status.SUCCESS,
                service.openAuthorized(server, ALICE, "Alice", draft, 16L, 5L).status());
    }

    @Test void mutationSwapsTheDraftOnceAndRejectsTheOldRevision() {
        EditSessionService service = new EditSessionService();
        Object server = new Object();
        DraftSnapshot original = draft("test:mutation");
        EditSessionHandle handle = service.openAuthorized(server, ALICE, "Alice", original, 0L, 100L).value();
        QuestBookDefinition changedBook = new QuestBookDefinition(original.book().id(), 1, "Changed",
                List.of(), List.of(), Map.of());
        DraftSnapshot changed = DraftSnapshot.of(changedBook, original.baseRevision());

        var first = service.mutateAuthorized(server, ALICE, handle.sessionId(), original.book().id(),
                original.draftRevision(), 1L, 100L, ignored -> AuthorOperationResult.success("OK", "changed",
                        new DraftEditResult(changed, List.of(changedBook.id()), List.of())));
        var stale = service.mutateAuthorized(server, ALICE, handle.sessionId(), original.book().id(),
                original.draftRevision(), 2L, 100L, ignored -> fail("stale callback must not execute"));

        assertTrue(first.success());
        assertEquals(AuthorOperationResult.Status.CONFLICT, stale.status());
        assertEquals("STALE_DRAFT_REVISION", stale.code());
        assertEquals(changed.draftRevision(), service.inspectAuthorized(server, changedBook.id(), 2L).value().draftRevision());
    }

    @Test void undoRedoTracksConfirmedRevisionsAndNewEditsDiscardTheRedoBranch() {
        EditSessionService service = new EditSessionService();
        Object server = new Object();
        DraftSnapshot original = draft("test:history");
        EditSessionHandle handle = service.openAuthorized(server, ALICE, "Alice", original, 0L, 100L).value();
        DraftSnapshot first = renamed(original, "First");
        DraftSnapshot second = renamed(original, "Second");
        DraftSnapshot branch = renamed(original, "Branch");

        mutateTo(service, server, handle, original, first, 1L);
        mutateTo(service, server, handle, first, second, 2L);
        EditSessionView afterEdits = service.inspectAuthorized(server, original.book().id(), 2L).value();
        assertEquals(2, afterEdits.undoSteps());
        assertEquals(0, afterEdits.redoSteps());

        var undone = service.historyAuthorized(server, ALICE, handle.sessionId(), original.book().id(),
                second.draftRevision(), 3L, 100L, false);
        assertEquals(first.draftRevision(), undone.value().snapshot().draftRevision());
        assertEquals(1, service.inspectAuthorized(server, original.book().id(), 3L).value().undoSteps());
        assertEquals(1, service.inspectAuthorized(server, original.book().id(), 3L).value().redoSteps());

        var redone = service.historyAuthorized(server, ALICE, handle.sessionId(), original.book().id(),
                first.draftRevision(), 4L, 100L, true);
        assertEquals(second.draftRevision(), redone.value().snapshot().draftRevision());

        service.historyAuthorized(server, ALICE, handle.sessionId(), original.book().id(),
                second.draftRevision(), 5L, 100L, false);
        mutateTo(service, server, handle, first, branch, 6L);
        EditSessionView branched = service.inspectAuthorized(server, original.book().id(), 6L).value();
        assertEquals(2, branched.undoSteps());
        assertEquals(0, branched.redoSteps());
        assertEquals(branch.draftRevision(), branched.draftRevision());
    }

    @Test void revisionConflictClearsHistoryAndHistoryRetainsOnlyTheBoundedTail() {
        EditSessionService service = new EditSessionService();
        Object server = new Object();
        DraftSnapshot current = draft("test:bounded_history");
        EditSessionHandle handle = service.openAuthorized(server, ALICE, "Alice", current, 0L, 10_000L).value();
        for (int step = 1; step <= EditSessionService.MAX_HISTORY_STEPS + 4; step++) {
            DraftSnapshot next = renamed(current, "Step " + step);
            mutateTo(service, server, handle, current, next, step);
            current = next;
        }
        assertEquals(EditSessionService.MAX_HISTORY_STEPS,
                service.inspectAuthorized(server, current.book().id(), 100L).value().undoSteps());

        var stale = service.mutateAuthorized(server, ALICE, handle.sessionId(), current.book().id(),
                "stale-revision", 101L, 10_000L, ignored -> fail("stale callback must not execute"));

        assertEquals(AuthorOperationResult.Status.CONFLICT, stale.status());
        EditSessionView afterConflict = service.inspectAuthorized(server, current.book().id(), 101L).value();
        assertEquals(0, afterConflict.undoSteps());
        assertEquals(0, afterConflict.redoSteps());
    }

    private static void mutateTo(EditSessionService service, Object server, EditSessionHandle handle,
                                 DraftSnapshot before, DraftSnapshot after, long tick) {
        var result = service.mutateAuthorized(server, ALICE, handle.sessionId(), before.book().id(),
                before.draftRevision(), tick, 100L, ignored -> AuthorOperationResult.success("OK", "changed",
                        new DraftEditResult(after, List.of(after.book().id()), List.of())));
        assertTrue(result.success());
    }

    private static DraftSnapshot renamed(DraftSnapshot original, String title) {
        QuestBookDefinition changed = new QuestBookDefinition(original.book().id(), 1, title,
                List.of(), List.of(), Map.of());
        return DraftSnapshot.of(changed, original.baseRevision());
    }

    private static DraftSnapshot draft(String id) {
        QuestBookDefinition book = new QuestBookDefinition(ResourceLocation.parse(id), 1, "Book",
                List.of(), List.of(), Map.of());
        return DraftSnapshot.of(book, "published-revision");
    }
}
