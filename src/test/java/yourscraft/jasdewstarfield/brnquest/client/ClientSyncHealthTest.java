package yourscraft.jasdewstarfield.brnquest.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ClientSyncHealthTest {
    private final ClientQuestState state = ClientQuestState.get();
    @AfterEach void clear() { state.disconnected(); }
    @Test void partialDuplicateAndLateChunksAreObservedWithoutChangingTheTransfer() {
        state.begin("candidate", 2, 2);
        state.acceptChunk("candidate", 0, "{");
        state.acceptChunk("candidate", 0, "{");
        state.acceptChunk("old", 1, "x");
        var view = state.syncHealth();
        assertEquals(1, view.receivedChunks());
        assertEquals(1, view.receivedBytes());
        assertEquals(1, view.ignoredChunks());
        assertEquals("candidate", view.receivingRevision());
        ClientHealthCommand.messages(view, false);
        ClientHealthCommand.messages(view, true);
        assertEquals(view, state.syncHealth(), "formatting and inspection cannot mutate or retry a transfer");
    }
    @Test void rejectionSurvivesSuccessfulRetryAndDisconnectClearsConnectionState() {
        var book = snapshot(); String json = NativeBookJson.encode(book.book());
        state.advertised(book.revision());
        state.begin("broken", 1, 1); state.acceptChunk("broken", 0, "{");
        assertEquals("DECODE_FAILED", state.syncHealth().lastRejectedCode());
        assertEquals("broken", state.syncHealth().lastRejectedRevision());
        state.begin(book.revision(), 1, bytes(json)); state.acceptChunk(book.revision(), 0, json);
        assertEquals(book.revision(), state.syncHealth().lastAppliedRevision());
        assertEquals("DECODE_FAILED", state.syncHealth().lastRejectedCode());
        assertEquals("", state.bookSyncFailure(), "historical diagnostics do not keep a recovered UI in error");
        state.disconnected();
        assertTrue(state.book().isEmpty());
        assertEquals("", state.syncHealth().lastRejectedCode());
        assertEquals("", state.syncHealth().advertisedRevision());
        assertEquals(0, state.syncHealth().receivedChunks());
    }
    @Test void distinctValidationFailuresPreserveThePreviouslyAppliedBook() {
        var book = snapshot(); String json = NativeBookJson.encode(book.book());
        state.begin(book.revision(), 1, bytes(json)); state.acceptChunk(book.revision(), 0, json);
        state.begin("wrong-size", 1, bytes(json) + 1); state.acceptChunk("wrong-size", 0, json);
        assertEquals("BYTE_COUNT_MISMATCH", state.syncHealth().lastRejectedCode());
        state.begin("wrong-revision", 1, bytes(json)); state.acceptChunk("wrong-revision", 0, json);
        assertEquals("REVISION_MISMATCH", state.syncHealth().lastRejectedCode());
        assertEquals(book.revision(), state.revision());
        state.begin("bad-manifest", 0, 0);
        assertEquals("bad-manifest", state.syncHealth().lastRejectedRevision());
        assertEquals("INVALID_MANIFEST", state.syncHealth().lastRejectedCode());
    }
    @Test void localCommandRegistersSeparatelyFromTheServerCommandTree() {
        var dispatcher = new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();
        ClientHealthCommand.register(new net.neoforged.neoforge.client.event.RegisterClientCommandsEvent(dispatcher, null));
        assertNull(dispatcher.getRoot().getChild("brnquest"));
        var health = dispatcher.getRoot().getChild("brnquest_client").getChild("health");
        assertNotNull(health.getCommand());
        assertNotNull(health.getChild("details").getCommand());
    }
    @Test void announcementIsNotAnAppliedRevision() {
        state.advertised("new");
        assertEquals("", state.syncHealth().activeRevision());
        assertEquals("new", state.syncHealth().advertisedRevision());
        assertEquals("", state.syncHealth().lastAppliedRevision());
    }
    private static int bytes(String text) { return text.getBytes(StandardCharsets.UTF_8).length; }
    private static QuestBookSnapshot snapshot() {
        return QuestBookSnapshot.of(new QuestBookDefinition(ResourceLocation.parse("test:health"), 1,
                "Health", List.of(), List.of(), Map.of()));
    }
}
