package yourscraft.jasdewstarfield.brnquest.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientActionWaitTest {
    @Test void duplicateClicksWaitButTimeoutOnlyPermitsManualRetry() {
        var wait = new ClientActionWait();
        assertTrue(wait.begin("reward", 100));
        assertFalse(wait.begin("reward", 101));
        assertTrue(wait.pending("reward", 5_000_000_099L));
        assertFalse(wait.pending("reward", 5_000_000_100L));
        assertTrue(wait.expired("reward", 5_000_000_100L));
        assertTrue(wait.begin("reward", 5_000_000_101L));
    }

    @Test void ReceiptsAndWorldResetReleaseOnlyTheIntendedLocks() {
        var wait = new ClientActionWait();
        wait.begin("a", 0); wait.begin("b", 0);
        wait.finish("a");
        assertFalse(wait.pending("a", 1));
        assertTrue(wait.pending("b", 1));
        wait.clear();
        assertFalse(wait.pending("b", 1));
        assertFalse(wait.expired("b", 10_000_000_000L));
    }
}
