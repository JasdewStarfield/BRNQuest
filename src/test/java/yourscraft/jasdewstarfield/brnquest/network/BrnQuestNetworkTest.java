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
}
