package yourscraft.jasdewstarfield.brnquest.task;

import yourscraft.jasdewstarfield.brnquest.api.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** One server sampling pass shares identical reads. This local cache contains no persistent progress. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public final class TaskSampleValues {
    private final Map<String, Long> values = new HashMap<>();
    public long get(String key, LongSupplier read) { return values.computeIfAbsent(key, ignored -> read.getAsLong()); }
}
