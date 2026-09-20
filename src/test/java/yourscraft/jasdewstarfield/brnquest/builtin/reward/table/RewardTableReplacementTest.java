package yourscraft.jasdewstarfield.brnquest.builtin.reward.table;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static yourscraft.jasdewstarfield.brnquest.builtin.reward.table.RewardTableJournal.*;

/** Replacement retries must preserve the frozen attempt and never weaken the durable execution gate. */
class RewardTableReplacementTest {
    @TempDir Path directory;
    private Attempt attempt() {
        return new Attempt(1, "owner/root", UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "{\"frozen\":true}", "resources", State.READY, List.of(), 1, "prepared");
    }
    @Test void transientDenialRetriesTheSameSnapshot() throws Exception {
        var calls = new AtomicInteger();
        var journal = new RewardTableJournal(directory, path -> {}, path -> {
            if (calls.incrementAndGet() < 3) throw new AccessDeniedException(path.toString());
        });
        var original = attempt();
        journal.begin(original);
        journal.save(original.state(State.SUCCEEDED, "empty draw consumed"));
        var restored = new RewardTableJournal(directory).read(original.key());
        assertEquals(3, calls.get());
        assertEquals(State.SUCCEEDED, restored.state());
        assertEquals(original.attemptId(), restored.attemptId());
        assertEquals(original.snapshot(), restored.snapshot());
    }
    @Test void permanentDenialAndUnsupportedAtomicMoveKeepPreviousReceipt() throws Exception {
        var calls = new AtomicInteger();
        var original = attempt();
        var journal = new RewardTableJournal(directory, path -> {}, path -> {
            calls.incrementAndGet();
            throw new AccessDeniedException(path.toString());
        });
        journal.begin(original);
        assertThrows(AccessDeniedException.class, () -> journal.save(original.state(State.SUCCEEDED, "consumed")));
        assertEquals(4, calls.get(), "denials have a bounded retry budget");
        assertEquals(original, journal.read(original.key()));
        var unsupported = new RewardTableJournal(directory, path -> {}, path -> {
            throw new AtomicMoveNotSupportedException(path.toString(), path.toString(), "unsupported");
        });
        assertThrows(AtomicMoveNotSupportedException.class, () -> unsupported.save(original.state(State.SUCCEEDED, "consumed")));
        assertEquals(original, journal.read(original.key()));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count(), "failed replacement cleans its temporary file only");
        }
    }
}
