package yourscraft.jasdewstarfield.brnquest.builtin.reward.table;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.task.*;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import java.util.random.RandomGenerator;

/** Pure bounded selection; callers persist every decision before any selected reward has an effect. */
public final class RewardTableDraws {
    private RewardTableDraws() {}
    public record Draw(String entryId, int ordinal, boolean empty, boolean guaranteed) {}

    public static List<Draw> select(RewardTableTree tree, Function<BigInteger, BigInteger> ticket) {
        if (tree.mode().equals("choice")) throw new IllegalArgumentException("Choice requires explicit confirmation, never random selection");
        List<Draw> result = new ArrayList<>();
        var entries = tree.entries();
        if (tree.mode().equals("all")) {
            for (var entry : entries) result.add(new Draw(entry.get("entry_id").getAsString(), 0, false, false));
            return List.copyOf(result);
        }
        List<JsonObject> pool = new ArrayList<>();
        for (var entry : entries) {
            if (RewardTableTree.always(entry)) result.add(new Draw(entry.get("entry_id").getAsString(), -1, false, true));
            else pool.add(entry);
        }
        // Scaling exact decimals to common integer tickets keeps even small legal weights reachable.
        int scale = Math.max(0, tree.emptyWeight().scale());
        for (var entry : pool) scale = Math.max(scale, RewardTableTree.weight(entry).scale());
        BigInteger empty = tree.emptyWeight().scaleByPowerOfTen(scale).toBigIntegerExact();
        for (int roll = 0; roll < tree.rolls(); roll++) {
            var weights = new ArrayList<BigInteger>();
            BigInteger total = empty;
            for (var entry : pool) {
                BigInteger weight = RewardTableTree.weight(entry).scaleByPowerOfTen(scale).toBigIntegerExact();
                weights.add(weight); total = total.add(weight);
            }
            if (total.signum() == 0) break; // Exhaustion never silently changes no-replacement into replacement.
            BigInteger selected = Objects.requireNonNull(ticket.apply(total));
            if (selected.signum() < 0 || selected.compareTo(total) >= 0) throw new IllegalArgumentException("Random ticket outside pool");
            if (selected.compareTo(empty) < 0) {
                result.add(new Draw("", roll, true, false));
                continue;
            }
            selected = selected.subtract(empty);
            for (int i = 0; i < pool.size(); i++) {
                if (selected.compareTo(weights.get(i)) < 0) {
                    result.add(new Draw(pool.get(i).get("entry_id").getAsString(), roll, false, false));
                    if (!tree.replacement()) pool.remove(i);
                    break;
                }
                selected = selected.subtract(weights.get(i));
            }
        }
        return List.copyOf(result);
    }

    /** Rejection sampling avoids modulo bias and supports the full validated decimal range. */
    public static Function<BigInteger, BigInteger> tickets(RandomGenerator random) {
        return bound -> {
            int bits = bound.bitLength();
            byte[] bytes = new byte[(bits + 7) / 8];
            BigInteger value;
            do {
                random.nextBytes(bytes);
                bytes[0] &= (byte) (0xff >>> (bytes.length * 8 - bits));
                value = new BigInteger(1, bytes);
            } while (value.compareTo(bound) >= 0);
            return value;
        };
    }

    public static JsonArray encode(List<Draw> draws) {
        var array = new JsonArray();
        for (var draw : draws) {
            var item = new JsonObject();
            item.addProperty("entry_id", draw.entryId()); item.addProperty("roll", draw.ordinal());
            item.addProperty("empty", draw.empty()); item.addProperty("always", draw.guaranteed());
            array.add(item);
        }
        return array;
    }
}
