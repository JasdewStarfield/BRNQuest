package yourscraft.jasdewstarfield.brnquest.network;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BookSyncObservationsTest {
    @Test void onlyActualTransportSubmissionsCountAndReadsDoNotChangeTheRecord() {
        var observations = new BookSyncObservations(); var player = UUID.randomUUID();
        observations.observe(player, new BrnQuestNetwork.BookManifestPayload("test:book", "r1", 2, 20), true);
        observations.observe(player, new BrnQuestNetwork.BookChunkPayload("r1", 0, "first"), true);
        assertEquals(BookSyncObservations.Status.QUEUING, observations.inspect(player).status());
        observations.observe(player, new BrnQuestNetwork.BookChunkPayload("r1", 1, "last"), true);
        var view = observations.inspect(player);
        assertEquals(BookSyncObservations.Status.SUBMITTED, view.status());
        assertEquals(2, view.submittedChunks());
        assertSame(view, observations.inspect(player));
        observations.observe(player, new BrnQuestNetwork.BookManifestPayload("test:book", "r2", 1, 10), false);
        observations.observe(player, new BrnQuestNetwork.BookChunkPayload("r2", 0, "skipped"), false);
        assertEquals(BookSyncObservations.Status.NOT_SUBMITTED, observations.inspect(player).status());
        assertEquals(0, observations.inspect(player).submittedChunks());
    }
    @Test void rejectedEvictedAndDisconnectedRecordsNeverLookLikeClientReceipts() {
        var observations = new BookSyncObservations(); var player = UUID.randomUUID();
        observations.rejected(player, "rejected", "TOO_MANY_QUESTS");
        assertEquals(BookSyncObservations.Status.REJECTED, observations.inspect(player).status());
        assertEquals("TOO_MANY_QUESTS", observations.inspect(player).code());
        observations.forget(player);
        assertEquals(BookSyncObservations.Status.UNKNOWN, observations.inspect(player).status());
        observations.rejected(player, "old", "BOOK_TOO_LARGE");
        for (int i = 0; i < 256; i++) observations.rejected(UUID.randomUUID(), "r", "BOOK_TOO_LARGE");
        assertEquals(BookSyncObservations.Status.UNKNOWN, observations.inspect(player).status());
    }
}
