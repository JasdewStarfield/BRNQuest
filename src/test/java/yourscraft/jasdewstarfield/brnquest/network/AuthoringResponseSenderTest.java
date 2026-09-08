package yourscraft.jasdewstarfield.brnquest.network;

import com.google.gson.Gson;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.author.*;
import yourscraft.jasdewstarfield.brnquest.data.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static yourscraft.jasdewstarfield.brnquest.network.AuthoringNetwork.*;

/** Exercises the actual sender and captures its ordered packets, including fallback paths. */
class AuthoringResponseSenderTest {
    private static final Gson GSON = new Gson();
    private final List<CustomPacketPayload> packets = new ArrayList<>();
    private final AuthoringResponseSender sender = new AuthoringResponseSender(packets::add, () -> 100L);

    @Test void catalogLimitsTitlesMessagesRowsAndUtf8Bytes() {
        var entries = new ArrayList<DraftCatalogEntry>();
        for (int i = 0; i < 300; i++) entries.add(new DraftCatalogEntry(
                ResourceLocation.parse("test:" + "x".repeat(1800) + i), "标题".repeat(256), "revision", DraftOrigin.UNKNOWN));
        sender.sendCatalog(AuthorOperationResult.success("DRAFT_CATALOG", "消息".repeat(600), entries));
        var payload = (CatalogPayload) packets.getFirst();
        bounded(payload.json());
        var wire = GSON.fromJson(payload.json(), CatalogResponseWire.class);
        assertEquals("DRAFT_CATALOG_TRUNCATED", wire.code());
        assertEquals(512, wire.message().length());
        assertTrue(wire.allowed());
        assertTrue(wire.entries().size() < 256);
        assertTrue(wire.entries().stream().allMatch(e -> e.title().length() == 256));
    }

    @Test void deniedCatalogAndFailureKeepTheirStatusAndBoundDiagnostics() {
        sender.sendCatalog(AuthorOperationResult.failure(AuthorOperationResult.Status.FORBIDDEN, "DENIED", "拒绝"));
        var catalog = GSON.fromJson(((CatalogPayload) packets.getFirst()).json(), CatalogResponseWire.class);
        assertFalse(catalog.allowed());
        assertEquals("DENIED", catalog.code());
        assertTrue(catalog.entries().isEmpty());
        var diagnostic = new EditorDiagnosticWire("ERROR", "INVALID_EDITOR_MUTATION", "对象".repeat(1000),
                "路径".repeat(1000), "错误".repeat(1000));
        sender.sendFailure("MUTATE", AuthorOperationResult.Status.INVALID_REQUEST, "INVALID_EDITOR_MUTATION",
                "失败".repeat(1000), java.util.Collections.nCopies(1000, diagnostic));
        var wire = session(1);
        assertEquals("INVALID_REQUEST", wire.status());
        assertEquals(512, wire.message().length());
        assertEquals(8, wire.diagnostics().size());
        assertEquals(512, wire.diagnostics().getFirst().objectId().length());
        assertEquals(512, wire.diagnostics().getFirst().path().length());
        assertEquals(512, wire.diagnostics().getFirst().message().length());
    }

    @Test void draftMetadataPrecedesEveryRevisionBoundChunkAndPreservesOpaqueConfig() {
        var draft = draft("Book", "长文本".repeat(15000));
        var handle = handle(draft);
        sender.sendDraft("OPEN", "SESSION_OPENED", "opened", handle, draft, () -> fail("Unexpected oversize"));
        var wire = session(0);
        assertEquals("OPEN", wire.action());
        assertEquals(900, wire.remainingTicks());
        assertEquals(2, wire.undoSteps());
        assertEquals(1, wire.redoSteps());
        assertTrue(wire.chunks() > 1);
        assertEquals(wire.chunks() + 1, packets.size());
        var joined = new StringBuilder();
        for (int i = 1; i < packets.size(); i++) {
            var chunk = (DraftChunkPayload) packets.get(i);
            assertEquals(i - 1, chunk.index());
            assertEquals(handle.sessionId().toString(), chunk.sessionId());
            assertEquals(draft.draftRevision(), chunk.revision());
            joined.append(chunk.data());
        }
        assertEquals(NativeBookJson.encode(draft.book()), joined.toString());
        assertEquals(joined.toString().getBytes(StandardCharsets.UTF_8).length, wire.decodedBytes());
        assertEquals(draft.draftRevision(), wire.draftRevision());
    }

    @Test void oversizedDraftClosesOnceBeforeFailureAndSendsNoChunks() {
        var draft = draft("中".repeat(BrnQuestConstants.MAX_BOOK_BYTES / 3 + 1), "");
        var closed = new AtomicInteger();
        sender.sendDraft("OPEN", "SESSION_OPENED", "opened", handle(draft), draft, () -> {
            assertTrue(packets.isEmpty(), "Close precedes the failure packet");
            closed.incrementAndGet();
        });
        assertEquals(1, closed.get());
        assertEquals(1, packets.size());
        assertEquals("DRAFT_TOO_LARGE", session(0).code());
        assertEquals("INVALID_REQUEST", session(0).status());
    }

    @Test void positionsUsePatchOrFallBackToTheSameFullDraft() {
        var draft = draft("Book", "body");
        var handle = handle(draft);
        var positions = List.of(new PositionWire("test:quest", 5, -3));
        sender.sendPositionPatch("QUESTS_MOVED", "moved", handle, draft, positions, () -> fail("Unexpected close"));
        assertEquals(1, packets.size());
        assertEquals("PATCH", session(0).action());
        assertEquals(positions, session(0).positionPatch());
        assertEquals(draft.draftRevision(), session(0).draftRevision());
        packets.clear();
        var huge = List.of(new PositionWire("test:" + "x".repeat(BrnQuestConstants.MAX_EDITOR_METADATA_BYTES), 1, 2));
        sender.sendPositionPatch("QUESTS_MOVED", "moved", handle, draft, huge, () -> fail("Unexpected close"));
        assertEquals("MUTATE", session(0).action());
        assertTrue(session(0).positionPatch().isEmpty());
        assertEquals(2, packets.size());
        assertEquals(NativeBookJson.encode(draft.book()), ((DraftChunkPayload) packets.get(1)).data());
    }

    @Test void reviewTrimsRowsAndThenBytesWithoutChangingTotalsOrPublishDecision() {
        var draft = draft("Book", "");
        var diagnostic = new EditorDiagnosticWire("WARN", "WARNING", "对象".repeat(512),
                "路径".repeat(512), "诊断".repeat(512));
        var change = new SemanticDiffWire("UPDATE", "QUEST", "对象".repeat(512),
                "路径".repeat(512), "之前".repeat(512), "之后".repeat(512));
        sender.sendPublishReview(handle(draft), false, "base", draft.draftRevision(),
                java.util.Collections.nCopies(150, diagnostic), java.util.Collections.nCopies(150, change));
        var review = session(0).review();
        assertFalse(review.publishAllowed());
        assertTrue(review.truncated());
        assertEquals(150, review.diagnosticCount());
        assertEquals(150, review.changeCount());
        assertTrue(review.diagnostics().size() < 128 || review.changes().size() < 128);
        assertEquals("BACKUP_AND_REPLACE", review.backupStrategy());
        packets.clear();
        sender.sendPublishReview(handle(draft), true, "base", draft.draftRevision(), List.of(diagnostic), List.of(change));
        review = session(0).review();
        assertTrue(review.publishAllowed());
        assertFalse(review.truncated());
        assertEquals(240, review.changes().getFirst().before().length());
        assertTrue(review.changes().getFirst().before().endsWith("…"));
    }

    @Test void reviewMapsServiceDiagnosticsRevisionConflictsAndDiff() {
        var draft = draft("Book", "");
        var diagnostic = new yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic(
                yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic.Severity.WARN,
                "WARNING", "", "config", "test:task", "诊断".repeat(300));
        var preview = new DraftPublishResult(draft, "previous", null,
                new RevisionCheck(null, List.of(new RevisionConflict("WORKSPACE_BASE_CHANGED",
                        "expected", "actual", "冲突".repeat(300)))), List.of(diagnostic));
        var diff = new QuestBookDiff("from", "to", List.of(new SemanticDiffEntry(
                SemanticDiffEntry.Kind.CONFIG_CHANGED, SemanticDiffEntry.ObjectKind.TASK,
                ResourceLocation.parse("test:task"), "config", "之前".repeat(300), "之后".repeat(300))));
        sender.sendPublishReview(handle(draft), false, draft.book().id(), preview, diff);
        var review = session(0).review();
        assertEquals(2, review.diagnosticCount());
        assertEquals(1, review.changeCount());
        assertEquals("from", review.fromRevision());
        assertEquals("to", review.targetRevision());
        assertEquals("WORKSPACE_BASE_CHANGED", review.diagnostics().get(1).code());
        assertEquals("revision", review.diagnostics().get(1).path());
        assertEquals(240, review.diagnostics().getFirst().message().length());
        assertEquals("CONFIG_CHANGED", review.changes().getFirst().kind());
        assertFalse(review.publishAllowed());
    }

    @Test void mutationFailureKeepsTypedIdPathAndOriginalServiceCode() {
        var wire = new EditorMutationWire("00000000-0000-0000-0000-000000000001", "test:book", "revision", "UPDATE_TASK",
                "test:new", "test:quest", "test:old", "", 0, 0, 0, List.of(), Map.of());
        sender.sendMutationFailure(AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT,
                "DUPLICATE_TYPED_ID", "duplicate"), AuthoringRequestDecoder.mutation(GSON.toJson(wire)).value());
        var response = session(0);
        assertEquals("CONFLICT", response.status());
        assertEquals("DUPLICATE_TYPED_ID", response.code());
        assertEquals("id", response.diagnostics().getFirst().path());
        assertEquals("test:old", response.diagnostics().getFirst().objectId());
    }

    private SessionResponseWire session(int index) {
        String json = ((SessionPayload) packets.get(index)).json();
        bounded(json);
        return GSON.fromJson(json, SessionResponseWire.class);
    }

    private static void bounded(String json) {
        assertTrue(json.getBytes(StandardCharsets.UTF_8).length <= BrnQuestConstants.MAX_EDITOR_METADATA_BYTES);
    }

    private static DraftSnapshot draft(String title, String description) {
        var book = ResourceLocation.parse("test:book");
        var group = ResourceLocation.parse("test:group");
        var chapter = ResourceLocation.parse("test:chapter");
        var task = new TaskDefinition(book, ResourceLocation.parse("test:task"), ResourceLocation.parse("extension:custom"),
                Map.of("opaque", "unknown配置"), true);
        var quest = new QuestDefinition(book, ResourceLocation.parse("test:quest"), chapter, "Quest", "", description,
                "", 0, 0, List.of(), List.of(task), List.of(), "");
        return DraftSnapshot.of(new QuestBookDefinition(book, 1, title,
                List.of(new ChapterGroupDefinition(book, group, "Group", 0)),
                List.of(new ChapterDefinition(book, chapter, group, "Chapter", "", 0, List.of(quest))), Map.of()), "base");
    }

    private static EditSessionHandle handle(DraftSnapshot draft) {
        return new EditSessionHandle(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new EditSessionView(draft.book().id(), UUID.randomUUID(), "Editor", "base", draft.draftRevision(),
                        "saved", 1000, 2, 1));
    }
}
