package yourscraft.jasdewstarfield.brnquest.reward.table;

import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RewardTableDrawsTest {
    private static String leaf(String id, String weight, boolean always) {
        return "{\"entry_id\":\"" + id + "\",\"type\":\"brnquest:xp\",\"weight\":" + weight + ",\"always\":" + always + "}";
    }
    private static RewardTableTree tree(String entries, int rolls, boolean replacement, String empty) {
        return RewardTableTree.parse("{\"mode\":\"random\",\"rolls\":" + rolls + ",\"replacement\":" + replacement
                + ",\"empty_weight\":" + empty + ",\"entries\":[" + entries + "]}");
    }

    @Test void decimalIntervalsIncludeLowerBoundaryAndExcludeUpperBoundary() {
        var table = tree(leaf("a", "0.1", false) + "," + leaf("b", "0.2", false), 4, true, "0.1");
        var ticket = new AtomicInteger();
        var draws = RewardTableDraws.select(table, bound -> {
            assertEquals(BigInteger.valueOf(4), bound);
            return BigInteger.valueOf(ticket.getAndIncrement());
        });
        assertEquals(List.of("", "a", "b", "b"), draws.stream().map(RewardTableDraws.Draw::entryId).toList());
        assertTrue(draws.getFirst().empty());
    }

    @Test void guaranteedEntriesAreNotInPoolAndRepeatedHitsHaveDistinctOrdinals() {
        var table = tree(leaf("gift", "999", true) + "," + leaf("pick", "1", false), 3, true, "0");
        var draws = RewardTableDraws.select(table, bound -> { assertEquals(BigInteger.ONE, bound); return BigInteger.ZERO; });
        assertEquals(List.of("gift", "pick", "pick", "pick"), draws.stream().map(RewardTableDraws.Draw::entryId).toList());
        assertEquals(List.of(-1, 0, 1, 2), draws.stream().map(RewardTableDraws.Draw::ordinal).toList());
        assertTrue(draws.getFirst().guaranteed());
    }

    @Test void noReplacementExhaustsPoolButRetainsEmptyBucket() {
        String pool = leaf("a", "1", false) + "," + leaf("b", "1", false);
        assertEquals(2, RewardTableDraws.select(tree(pool, 5, false, "0"), bound -> BigInteger.ZERO).size());
        var draws = RewardTableDraws.select(tree(pool, 5, false, "1"), bound -> bound.subtract(BigInteger.ONE));
        assertEquals(List.of("b", "a", "", "", ""), draws.stream().map(RewardTableDraws.Draw::entryId).toList());
        assertEquals(3, draws.stream().filter(RewardTableDraws.Draw::empty).count());
    }

    @Test void allAndGuaranteedOnlyNeverConsumeRandomEntropy() {
        var always = tree(leaf("gift", "1", true), 64, false, "0");
        assertEquals(1, RewardTableDraws.select(always, b -> { fail("no pool"); return null; }).size());
        var all = RewardTableTree.parse(always.encode().replace("\"random\"", "\"all\""));
        assertEquals(1, RewardTableDraws.select(all, b -> { fail("all must not draw"); return null; }).size());
        assertEquals(64, all.rolls(), "switching modes retains hidden settings");
        assertThrows(IllegalArgumentException.class, () -> tree("", 1, true, "0"));
        assertTrue(RewardTableDraws.select(tree("", 3, true, "2"), b -> BigInteger.ZERO).stream().allMatch(RewardTableDraws.Draw::empty));
    }

    @Test void extremeLegalRatiosRemainReachableAndInvalidPrecisionIsRejected() {
        var extreme = tree(leaf("tiny", "1e-300", false) + "," + leaf("large", "1e300", false), 1, true, "0");
        assertEquals("tiny", RewardTableDraws.select(extreme, b -> BigInteger.ZERO).getFirst().entryId());
        assertEquals("large", RewardTableDraws.select(extreme, b -> b.subtract(BigInteger.ONE)).getFirst().entryId());
        assertThrows(IllegalArgumentException.class, () -> tree(leaf("a", "0.12345678901234567890123456789012345", false), 1, true, "0"));
        assertThrows(IllegalArgumentException.class, () -> tree(leaf("a", "1e-9999", false), 1, true, "0"));
        assertThrows(IllegalArgumentException.class, () -> tree(leaf("a", "1e308", false) + "," + leaf("b", "1e308", false), 1, true, "0"));
    }

    @Test void injectedOutOfRangeTicketFailsAndSeededSamplerIsRepeatable() {
        var tree = tree(leaf("a", "1", false), 1, true, "0");
        assertThrows(IllegalArgumentException.class, () -> RewardTableDraws.select(tree, b -> b));
        assertThrows(IllegalArgumentException.class, () -> RewardTableDraws.select(tree, b -> BigInteger.valueOf(-1)));
        var first = RewardTableDraws.tickets(new Random(81));
        var second = RewardTableDraws.tickets(new Random(81));
        var bound = BigInteger.TEN.pow(600).add(BigInteger.ONE);
        for (int i = 0; i < 64; i++) {
            var value = first.apply(bound);
            assertEquals(value, second.apply(bound));
            assertTrue(value.signum() >= 0 && value.compareTo(bound) < 0);
        }
    }
}
