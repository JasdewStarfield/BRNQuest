package yourscraft.jasdewstarfield.brnquest.network;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressSyncFailuresTest {
    @Test void repeatedFailuresUpdateSizeWithoutRepeatedNotificationsAndRecoveryResetsEpisode() {
        var failures = new ProgressSyncFailures();
        var player = UUID.randomUUID();
        assertTrue(failures.rejected(player, "r1", "PROGRESS_TOO_LARGE", 1048577));
        assertFalse(failures.rejected(player, "r1", "PROGRESS_TOO_LARGE", 1048600));
        assertEquals(1048600, failures.inspect(player).orElseThrow().bytes());
        assertTrue(failures.rejected(player, "r2", "PROGRESS_TOO_LARGE", 1048600));
        failures.forget(player);
        assertTrue(failures.inspect(player).isEmpty());
        assertTrue(failures.rejected(player, "r2", "PROGRESS_TOO_LARGE", 1048600));
    }

    @Test void failuresAreBoundedAndIsolatedPerRecipient() {
        var failures = new ProgressSyncFailures();
        var first = new UUID(0, 0);
        for (int i = 0; i < 257; i++) failures.rejected(new UUID(0, i), "r", "ENCODE_FAILED", 0);
        assertTrue(failures.inspect(first).isEmpty());
        assertTrue(failures.inspect(new UUID(0, 256)).isPresent());
        failures.forget(new UUID(0, 255));
        assertTrue(failures.inspect(new UUID(0, 256)).isPresent());
    }

    @Test void diagnosticCodecPreservesActualSizeLimitAndRevision() {
        var payload = new BrnQuestNetwork.ProgressSyncFailurePayload("revision", "PROGRESS_TOO_LARGE", 1048577, 1048576);
        var buffer = Unpooled.buffer();
        try {
            BrnQuestNetwork.ProgressSyncFailurePayload.CODEC.encode(buffer, payload);
            assertEquals(payload, BrnQuestNetwork.ProgressSyncFailurePayload.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
}
