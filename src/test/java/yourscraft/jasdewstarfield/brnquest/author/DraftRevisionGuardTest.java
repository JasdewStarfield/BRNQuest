package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DraftRevisionGuardTest {
    @Test void detectsDiskAndOriginSpecificRevisionChanges() {
        DraftSnapshot activeDraft = DraftSnapshot.from(book(), DraftOrigin.ACTIVE, "active-base");
        RevisionCheck active = DraftRevisionGuard.evaluate(activeDraft, "saved", "external-disk",
                "new-active", "workspace");
        assertEquals(List.of("DISK_DRAFT_CHANGED", "ACTIVE_BASE_CHANGED"),
                active.conflicts().stream().map(RevisionConflict::code).toList());

        DraftSnapshot workspaceDraft = DraftSnapshot.from(book(), DraftOrigin.WORKSPACE, "workspace-base");
        RevisionCheck workspace = DraftRevisionGuard.evaluate(workspaceDraft, workspaceDraft.draftRevision(),
                workspaceDraft.draftRevision(), "active", "new-workspace");
        assertEquals("WORKSPACE_BASE_CHANGED", workspace.conflicts().getFirst().code());

        DraftSnapshot emptyDraft = DraftSnapshot.from(book(), DraftOrigin.EMPTY, "");
        assertEquals("WORKSPACE_CREATED", DraftRevisionGuard.evaluate(emptyDraft, emptyDraft.draftRevision(),
                emptyDraft.draftRevision(), "", "created").conflicts().getFirst().code());
    }

    @Test void unknownLegacyOriginOnlyEnforcesTheDiskConcurrencyToken() {
        DraftSnapshot draft = DraftSnapshot.of(book(), "legacy-base");
        RevisionCheck check = DraftRevisionGuard.evaluate(draft, draft.draftRevision(), draft.draftRevision(),
                "changed-active", "changed-workspace");
        assertFalse(check.hasConflicts());
    }

    @Test void savingOldDraftIsSafeButReviewedPublishStillBlocksDiskChanges() {
        DraftSnapshot draft = DraftSnapshot.from(book(), DraftOrigin.WORKSPACE, "old-workspace");
        RevisionCheck sourceDrift = DraftRevisionGuard.evaluate(draft, draft.draftRevision(),
                draft.draftRevision(), "another-active", "new-workspace");
        assertNull(DraftPersistenceService.blockingSaveConflict(sourceDrift));
        assertNotNull(DraftPublishService.blockingPublishConflict(sourceDrift, false));
        assertNull(DraftPublishService.blockingPublishConflict(sourceDrift, true));

        RevisionCheck diskDrift = DraftRevisionGuard.evaluate(draft, "old-saved",
                "different-disk", "another-active", "new-workspace");
        assertEquals("DISK_DRAFT_CHANGED", DraftPersistenceService.blockingSaveConflict(diskDrift).code());
        assertEquals("DISK_DRAFT_CHANGED", DraftPublishService.blockingPublishConflict(diskDrift, true).code());
    }

    @Test void activeContentCapturesAnExistingWorkspaceAsItsPublicationBaseline() {
        DraftSnapshot draft = DraftSnapshot.from(book(), DraftOrigin.ACTIVE, "shared-base");
        RevisionVector matchingVector = new RevisionVector("shared-base", "shared-base",
                draft.draftRevision(), draft.draftRevision(), "shared-base");
        RevisionCheck matching = DraftPublishService.addWorkspaceCreationConflict(draft,
                new RevisionCheck(matchingVector, List.of()));
        assertFalse(matching.hasConflicts(), "An unchanged source workspace is safe to replace");

        DraftSnapshot workspace = DraftSnapshot.from(book(), DraftOrigin.WORKSPACE, "other-workspace");
        DraftSnapshot prepared = DraftService.activeDraft(book(), "shared-base", workspace);
        assertEquals(DraftOrigin.WORKSPACE, prepared.origin());
        assertEquals(workspace.draftRevision(), prepared.baseRevision());

        RevisionCheck unchanged = DraftRevisionGuard.evaluate(prepared, prepared.draftRevision(),
                prepared.draftRevision(), "changed-active", workspace.draftRevision());
        assertFalse(unchanged.hasConflicts(), "Pre-existing active/workspace divergence is part of the chosen source");

        RevisionCheck changedAfterCreation = DraftRevisionGuard.evaluate(prepared, prepared.draftRevision(),
                prepared.draftRevision(), "changed-active", "later-workspace");
        assertEquals("WORKSPACE_BASE_CHANGED", changedAfterCreation.conflicts().getFirst().code(),
                "A later workspace write must still be rejected");
    }

    private static QuestBookDefinition book() {
        return new QuestBookDefinition(ResourceLocation.parse("test:book"), 1, "Book",
                List.of(), List.of(), Map.of());
    }
}
