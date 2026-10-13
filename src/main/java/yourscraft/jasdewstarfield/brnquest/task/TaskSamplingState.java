package yourscraft.jasdewstarfield.brnquest.task;

import yourscraft.jasdewstarfield.brnquest.api.*;
import java.util.Map;

/** Immutable type-owned numeric sampling state; only the engine writes it to the owner ledger. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record TaskSamplingState(Map<String, Long> values) {
    public static final TaskSamplingState EMPTY = new TaskSamplingState(Map.of());
    public TaskSamplingState { values = Map.copyOf(values); }
}
