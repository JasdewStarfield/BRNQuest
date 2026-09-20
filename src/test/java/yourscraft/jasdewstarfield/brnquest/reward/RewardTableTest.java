package yourscraft.jasdewstarfield.brnquest.reward;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.*;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.table.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static yourscraft.jasdewstarfield.brnquest.builtin.reward.table.RewardTableJournal.*;

/** Pure configuration and forced-file tests exercise corruption and identity without granting game rewards. */
class RewardTableTest {
    @TempDir Path directory;
    private static String tree(String entries) { return "{\"version\":1,\"mode\":\"all\",\"extension\":{\"keep\":true},\"entries\":[" + entries + "]}"; }
    private static String leaf(String id) { return "{\"entry_id\":\"" + id + "\",\"type\":\"brnquest:xp\",\"config\":{\"xp\":\"2\",\"future\":\"kept\"},\"extra\":[1,2]}"; }
    @Test void losslessCopiesAndSortingKeepStableIdsAndUnknownFields() {
        var parsed = RewardTableTree.parse(tree(leaf("a") + "," + leaf("b")));
        var entries = parsed.entries();
        var copy = RewardTableTree.copyEntry(entries.getFirst());
        assertNotEquals("a", copy.get("entry_id").getAsString());
        assertEquals(entries.getFirst().get("extra"), copy.get("extra"));
        entries.getFirst().addProperty("entry_id", "changed");
        assertEquals("a", parsed.entries().getFirst().get("entry_id").getAsString());
        assertEquals("kept", RewardTableTree.config(parsed.entries().getFirst()).get("future"));
        assertEquals(JsonParser.parseString(tree(leaf("a") + "," + leaf("b"))), JsonParser.parseString(parsed.encode()));
    }
    @Test void rejectsMalformedHiddenNumbersIdsAndUtf8Budget() {
        assertThrows(IllegalArgumentException.class, () -> RewardTableTree.parse(tree(leaf("a")).replace("\"version\":1", "\"version\":1.00000000000000001")));
        assertThrows(IllegalArgumentException.class, () -> RewardTableTree.parse("[".repeat(65) + "0" + "]".repeat(65)));
        assertThrows(IllegalArgumentException.class, () -> RewardTableTree.parse(tree(leaf("a") + "," + leaf("a"))));
        assertThrows(IllegalArgumentException.class, () -> RewardTableTree.parse(tree(leaf("UPPER"))));
        assertThrows(IllegalArgumentException.class, () -> RewardTableTree.parse(tree(leaf("a")).replace("\"mode\":\"all\"", "\"mode\":\"all\",\"rolls\":1.5")));
        assertThrows(IllegalArgumentException.class, () -> RewardTableTree.parse(tree(leaf("a")).replace("\"mode\":\"all\"", "\"mode\":\"all\",\"empty_weight\":1e999")));
        assertThrows(IllegalArgumentException.class, () -> RewardTableTree.parse(tree(leaf("a")).replace("kept", "中".repeat(22000))));
    }
    @Test void enforcesGlobalNodeDepthAndCheckpointBudgets() {
        String maximum = java.util.stream.IntStream.range(0,255).mapToObj(i -> leaf("e" + i)).collect(java.util.stream.Collectors.joining(","));
        assertEquals(255, RewardTableTree.parse(tree(maximum)).entries().size());
        assertThrows(IllegalArgumentException.class, () -> RewardTableTree.parse(tree(maximum + "," + leaf("overflow"))));
        String nested = tree(leaf("a"));
        for (int i = 0; i < 7; i++) nested = tree("{\"entry_id\":\"nested\",\"type\":\"brnquest:reward_table\",\"table\":" + nested + "}");
        var eight = RewardTableTree.parse(nested);
        assertThrows(IllegalArgumentException.class, eight::requireSingleAll);
        String nine = tree("{\"entry_id\":\"nested\",\"type\":\"brnquest:reward_table\",\"table\":" + nested + "}");
        assertThrows(IllegalArgumentException.class, () -> RewardTableTree.parse(nine));
    }
    private Attempt attempt() {
        return new Attempt(1, "owner/book/root/cycle", UUID.randomUUID().toString(), UUID.randomUUID().toString(), tree(leaf("a")), "resources", State.READY,
                List.of(new Leaf("root/a", "occurrence-a", "brnquest:xp", Map.of("xp","2"), Map.of("xp","2"), "1", LeafState.NOT_STARTED, 0, 0, 1, "prepared")), 1, "ready");
    }
    @Test void forcedStartedSurvivesRestartAndAcknowledgementNeverResetsLeaf() throws Exception {
        var journal = new RewardTableJournal(directory); var attempt = attempt(); journal.begin(attempt);
        assertThrows(FileAlreadyExistsException.class, () -> journal.begin(attempt));
        journal.save(attempt.leaf(0, attempt.leaves().getFirst().state(LeafState.STARTED, "intent")).state(State.EXECUTING, "root/a"));
        var restarted = new RewardTableJournal(directory);
        var found = restarted.read(attempt.key()); assertEquals(LeafState.STARTED, found.leaves().getFirst().state());
        assertThrows(java.io.IOException.class, () -> restarted.acknowledge(attempt.key(), attempt.attemptId(), "occurrence-a", "admin"));
        restarted.save(found.leaf(0, found.leaves().getFirst().state(LeafState.UNKNOWN, "interrupted")).state(State.NEEDS_REVIEW, "review"));
        assertThrows(java.io.IOException.class, () -> restarted.acknowledge(attempt.key(), "stale", "occurrence-a", "admin"));
        restarted.acknowledge(attempt.key(), attempt.attemptId(), "occurrence-a", "admin");
        assertEquals(LeafState.ACKNOWLEDGED, journal.read(attempt.key()).leaves().getFirst().state());
    }
    @Test void tornAttemptBlocksAndSucceededReceiptSurvivesNewInstance() throws Exception {
        var journal = new RewardTableJournal(directory); var attempt = attempt(); journal.begin(attempt);
        journal.save(attempt.leaf(0, attempt.leaves().getFirst().state(LeafState.SUCCEEDED, "effect confirmed"))
                .state(State.SUCCEEDED, "normal ledger not committed yet"));
        assertEquals(State.SUCCEEDED, new RewardTableJournal(directory).read(attempt.key()).state());
        Path file; try (var files = Files.list(directory)) { file = files.findFirst().orElseThrow(); }
        Files.writeString(file, "{torn", StandardCharsets.UTF_8);
        assertThrows(java.io.IOException.class, () -> journal.read(attempt.key()));
    }
    @Test void injectedWriteFailureCannotAdvanceDurableIntent() throws Exception {
        var fail = new java.util.concurrent.atomic.AtomicBoolean(true);
        var journal = new RewardTableJournal(directory, path -> { if (fail.get()) throw new java.io.IOException("Injected before forced write"); });
        var attempt = attempt();
        assertThrows(java.io.IOException.class, () -> journal.begin(attempt));
        assertNull(journal.read(attempt.key()), "failed creation permits no execution intent");
        fail.set(false); journal.begin(attempt); fail.set(true);
        assertThrows(java.io.IOException.class, () -> journal.save(attempt.leaf(0, attempt.leaves().getFirst().state(LeafState.STARTED,"intent"))));
        assertEquals(LeafState.NOT_STARTED, journal.read(attempt.key()).leaves().getFirst().state(), "failed replacement leaves the previous forced snapshot intact");
    }
    @Test void maximumTreeAndStatusPacketsStayBounded() {
        String maximum = tree(java.util.stream.IntStream.range(0,255).mapToObj(i -> leaf("e"+i)).collect(java.util.stream.Collectors.joining(",")));
        long started = System.nanoTime();
        for (int i=0;i<64;i++) assertEquals(255,RewardTableTree.parse(maximum).entries().size());
        System.out.println("F5-A codec: nodes=256 bytes=" + maximum.getBytes(StandardCharsets.UTF_8).length + " meanMicros=" + (System.nanoTime()-started)/64/1000);
        var buffer = io.netty.buffer.Unpooled.buffer();
        try {
            var payload = new yourscraft.jasdewstarfield.brnquest.builtin.network.RewardTableNetwork.Status("a".repeat(128),"b".repeat(256),"c".repeat(64));
            var codec = yourscraft.jasdewstarfield.brnquest.builtin.network.RewardTableNetwork.Status.CODEC;
            codec.encode(buffer,payload); assertTrue(buffer.readableBytes()<512); assertEquals(payload,codec.decode(buffer));
            assertThrows(RuntimeException.class,() -> codec.encode(buffer,new yourscraft.jasdewstarfield.brnquest.builtin.network.RewardTableNetwork.Status("r","b".repeat(257),"READY")));
        } finally { buffer.release(); }
    }
}
