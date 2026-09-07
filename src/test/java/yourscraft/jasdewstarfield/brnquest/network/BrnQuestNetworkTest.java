package yourscraft.jasdewstarfield.brnquest.network;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrnQuestNetworkTest {
    @Test
    void bookChunksStayBelowMinecraftStringCodecLimit() {
        String original = "x".repeat(BrnQuestNetwork.BOOK_CHUNK_CHARACTERS * 2 + 17);

        var chunks = BrnQuestNetwork.split(original, BrnQuestNetwork.BOOK_CHUNK_CHARACTERS);

        assertEquals(original, String.join("", chunks));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.length() <= 32_767));
    }

    @Test
    void progressCodecSupportsSnapshotsLargerThanVanillaStringDefault() {
        var payload = new BrnQuestNetwork.ProgressSnapshotPayload("x".repeat(40_000), false);
        var buffer = Unpooled.buffer();

        BrnQuestNetwork.ProgressSnapshotPayload.CODEC.encode(buffer, payload);

        assertEquals(payload, BrnQuestNetwork.ProgressSnapshotPayload.CODEC.decode(buffer));
        buffer.release();
    }

    @Test
    void editorDraftChunkCodecCarriesSessionAndRevisionIdentity() {
        var payload = new AuthoringNetwork.DraftChunkPayload(
                "24e6efbf-bf1e-4b35-9206-2db50928a315", "revision", 3, "draft-data");
        var buffer = Unpooled.buffer();

        AuthoringNetwork.DraftChunkPayload.CODEC.encode(buffer, payload);

        assertEquals(payload, AuthoringNetwork.DraftChunkPayload.CODEC.decode(buffer));
        buffer.release();
    }

    @Test
    void editorCurrentBookAndQuestUpdatePayloadsRoundTrip() {
        var current = new AuthoringNetwork.OpenCurrentSessionPayload(
                "test:current", "active-revision", "draft-revision", true);
        var update = new AuthoringNetwork.UpdateQuestPayload("{\"questId\":\"test:root\"}");
        var save = new AuthoringNetwork.SaveSessionPayload("session", "test:current", "revision");
        var mutation = new AuthoringNetwork.EditorMutationPayload("{\"action\":\"ADD_GROUP\"}");
        var buffer = Unpooled.buffer();

        AuthoringNetwork.OpenCurrentSessionPayload.CODEC.encode(buffer, current);
        assertEquals(current, AuthoringNetwork.OpenCurrentSessionPayload.CODEC.decode(buffer));
        buffer.clear();
        AuthoringNetwork.UpdateQuestPayload.CODEC.encode(buffer, update);
        assertEquals(update, AuthoringNetwork.UpdateQuestPayload.CODEC.decode(buffer));
        buffer.clear();
        AuthoringNetwork.SaveSessionPayload.CODEC.encode(buffer, save);
        assertEquals(save, AuthoringNetwork.SaveSessionPayload.CODEC.decode(buffer));
        buffer.clear();
        AuthoringNetwork.EditorMutationPayload.CODEC.encode(buffer, mutation);
        assertEquals(mutation, AuthoringNetwork.EditorMutationPayload.CODEC.decode(buffer));
        buffer.release();
    }
}
