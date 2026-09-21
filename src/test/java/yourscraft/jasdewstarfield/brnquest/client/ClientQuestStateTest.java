package yourscraft.jasdewstarfield.brnquest.client;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientQuestStateTest {
    @Test void oldRevisionProgressCannotReplaceCurrentBookStateOrReleaseTaskWait() {
        var state = ClientQuestState.get();
        var current = snapshot("current");
        String json = NativeBookJson.encode(current.book());
        state.begin(current.revision(), 1, json.getBytes(StandardCharsets.UTF_8).length);
        assertTrue(state.acceptChunk(current.revision(), 0, json));
        assertTrue(state.beginTaskSubmission("test:task"));
        state.progress("{\"revision\":\"obsolete\",\"quests\":{},\"tasks\":{\"test:task\":99},\"claimed\":[]}");
        assertTrue(state.isTaskSubmissionPending("test:task"));
        assertFalse(state.taskProgress().containsKey("test:task"));
    }
    @Test void unrelatedProgressCannotAcknowledgeRewardAndDisconnectClearsWaits() {
        var state = ClientQuestState.get();
        assertTrue(state.beginRewardClaim("test:reward"));
        state.progress("{\"quests\":{},\"tasks\":{},\"claimed\":[]}");
        assertTrue(state.rewardClaimPending("test:reward"));
        assertFalse(state.beginRewardClaim("test:reward"));
        state.progress("{\"quests\":{},\"tasks\":{},\"claimed\":[\"test:reward\"]}");
        assertFalse(state.rewardClaimPending("test:reward"));
        state.beginRewardClaim("test:other");
        state.beginQuestCompletion("test:quest");
        state.disconnected();
        assertFalse(state.rewardClaimPending("test:other"));
        assertFalse(state.questCompletionPending("test:quest"));
    }
    @AfterEach void resetSingleton() {
        ClientQuestState.get().resetForTest();
    }

    @Test void retainsTaskProgressFromServerSnapshot() {
        ClientQuestState state = ClientQuestState.get();
        state.progress("{\"quests\":{},\"tasks\":{\"test:task\":3},\"claimed\":[],\"revision\":\"r1\"}");

        assertEquals(3L, state.taskProgress().get("test:task"));
    }

    @Test void progressResponseAcknowledgesPendingTaskSubmission() {
        ClientQuestState state = ClientQuestState.get();
        assertTrue(state.beginTaskSubmission("test:pending"));
        assertFalse(state.beginTaskSubmission("test:pending"));

        state.progress("{\"quests\":{},\"tasks\":{},\"claimed\":[],\"revision\":\"r1\"}");

        assertFalse(state.isTaskSubmissionPending("test:pending"));
        assertTrue(state.beginTaskSubmission("test:pending"));
    }

    @Test void malformedReplacementRetainsThePreviousVerifiedBook() {
        ClientQuestState state = ClientQuestState.get();
        QuestBookSnapshot baseline = snapshot("baseline");
        String baselineJson = NativeBookJson.encode(baseline.book());
        assertTrue(state.begin(baseline.revision(), 1, baselineJson.getBytes(StandardCharsets.UTF_8).length));
        assertTrue(state.acceptChunk(baseline.revision(), 0, baselineJson));

        assertTrue(state.begin("replacement", 1, 1));
        assertFalse(state.acceptChunk("replacement", 0, "{"));

        assertEquals(baseline.revision(), state.revision());
        assertEquals("INVALID_BOOK", state.bookSyncFailure());
    }

    @Test void invalidManifestIsRejectedWithoutOpeningATransfer() {
        ClientQuestState state = ClientQuestState.get();

        assertFalse(state.begin("", 0, -1));
        assertEquals("INVALID_MANIFEST", state.bookSyncFailure());
        assertFalse(state.acceptChunk("", 0, "{}"));
    }

    @Test void explicitServerFailureAbortsOnlyTheCandidateTransfer() {
        ClientQuestState state = ClientQuestState.get();
        QuestBookSnapshot baseline = snapshot("baseline");
        String json = NativeBookJson.encode(baseline.book());
        state.begin(baseline.revision(), 1, json.getBytes(StandardCharsets.UTF_8).length);
        state.acceptChunk(baseline.revision(), 0, json);
        state.begin("replacement", 1, 2);

        state.bookSyncFailed("BOOK_TOO_LARGE");

        assertEquals(baseline.revision(), state.revision());
        assertEquals("BOOK_TOO_LARGE", state.bookSyncFailure());
        assertFalse(state.acceptChunk("replacement", 0, "{}"));
    }

    @Test void staleChunkDoesNotCancelTheCurrentManifest() {
        ClientQuestState state = ClientQuestState.get();
        QuestBookSnapshot candidate = snapshot("candidate");
        String json = NativeBookJson.encode(candidate.book());
        assertTrue(state.begin(candidate.revision(), 1, json.getBytes(StandardCharsets.UTF_8).length));

        assertFalse(state.acceptChunk("older-revision", 0, "{}"));
        assertTrue(state.acceptChunk(candidate.revision(), 0, json));

        assertEquals(candidate.revision(), state.revision());
        assertEquals("", state.bookSyncFailure());
    }

    @Test void conflictingDuplicateChunkAbortsTheCandidate() {
        ClientQuestState state = ClientQuestState.get();
        assertTrue(state.begin("replacement", 2, 4));
        assertFalse(state.acceptChunk("replacement", 0, "{}"));

        assertFalse(state.acceptChunk("replacement", 0, "[]"));

        assertEquals("CONFLICTING_CHUNK", state.bookSyncFailure());
    }

    @Test void failureRetainsProgressAndWaitsUntilValidRecovery() {
        var state = ClientQuestState.get();
        state.progress("{\"tasks\":{\"test:task\":3},\"revision\":\"r1\"}");
        state.beginTaskSubmission("test:task");
        state.beginRewardClaim("test:reward");
        var failure = new yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork.ProgressSyncFailurePayload(
                "r1", "PROGRESS_TOO_LARGE", 1048577, 1048576);
        assertTrue(state.progressSyncFailed(failure));
        assertFalse(state.progressSyncFailed(failure));
        assertEquals(3L, state.taskProgress().get("test:task"));
        assertTrue(state.isTaskSubmissionPending("test:task"));
        assertTrue(state.rewardClaimPending("test:reward"));
        state.progress("{\"tasks\":{\"test:task\":4},\"revision\":\"r1\"}");
        assertTrue(state.progressSyncFailure().isEmpty());
        assertEquals(4L, state.taskProgress().get("test:task"));
        assertFalse(state.isTaskSubmissionPending("test:task"));
        assertTrue(state.rewardClaimPending("test:reward"));
    }

    @Test void priorBookProgressCannotClearANewerAdvertisedFailure() {
        var state = ClientQuestState.get();
        state.progress("{\"tasks\":{\"test:task\":3},\"revision\":\"old\"}");
        state.advertised("new");
        state.progressSyncFailed(new yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork.ProgressSyncFailurePayload(
                "new", "PROGRESS_TOO_LARGE", 1048577, 1048576));
        state.progress("{\"tasks\":{\"test:task\":9},\"revision\":\"old\"}");
        assertTrue(state.progressSyncFailure().isPresent());
        assertEquals(3L, state.taskProgress().get("test:task"));
    }

    @Test void malformedTailDoesNotPartiallyReplaceProgress() {
        var state = ClientQuestState.get();
        state.progress("{\"quests\":{\"test:q\":\"ACTIVE\"},\"tasks\":{\"test:task\":3}}");
        state.progress("{\"quests\":{},\"tasks\":{\"test:task\":9},\"nextAvailable\":{\"test:q\":null}}");
        assertEquals(3L, state.taskProgress().get("test:task"));
        assertTrue(state.statuses().containsKey("test:q"));
        assertEquals("DECODE_FAILED", state.progressSyncFailure().orElseThrow().code());
        state.progress("{");
        assertEquals(3L, state.taskProgress().get("test:task"));
    }

    @Test void oversizeAndStaleFailuresNeverEraseValidState() {
        var state = ClientQuestState.get();
        state.progress("{\"tasks\":{\"test:task\":3}}");
        state.advertised("current");
        assertFalse(state.progressSyncFailed(new yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork.ProgressSyncFailurePayload(
                "old", "PROGRESS_TOO_LARGE", 1048577, 1048576)));
        state.progress("中".repeat(350000));
        assertEquals("PROGRESS_TOO_LARGE", state.progressSyncFailure().orElseThrow().code());
        assertEquals(3L, state.taskProgress().get("test:task"));
        state.disconnected();
        assertTrue(state.progressSyncFailure().isEmpty());
    }

    private static QuestBookSnapshot snapshot(String path) {
        ResourceLocation bookId = ResourceLocation.fromNamespaceAndPath("test", path);
        ResourceLocation groupId = ResourceLocation.fromNamespaceAndPath("test", path + "_group");
        ResourceLocation chapterId = ResourceLocation.fromNamespaceAndPath("test", path + "_chapter");
        ChapterDefinition chapter = new ChapterDefinition(bookId, chapterId, groupId,
                "Chapter", "", 0, List.of());
        QuestBookDefinition book = new QuestBookDefinition(bookId, 1, "Book",
                List.of(new ChapterGroupDefinition(bookId, groupId, "Group", 0)),
                List.of(chapter), Map.of());
        return QuestBookSnapshot.of(book);
    }
}
