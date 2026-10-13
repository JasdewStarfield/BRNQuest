package yourscraft.jasdewstarfield.brnquest.task;

import yourscraft.jasdewstarfield.brnquest.api.*;
import java.util.Objects;

/** Pure sampling proposal: eligibility preparation may establish a new counter, polling only increases it. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record TaskSamplingResult(long progress, TaskSamplingState state, boolean ready) {
    public TaskSamplingResult {
        if (progress < 0) throw new IllegalArgumentException("Negative sampled progress");
        Objects.requireNonNull(state, "state");
    }
    /** Missing external data can decline readiness and preserve a pending membership rebase. */
    public TaskSamplingResult(long progress, TaskSamplingState state) { this(progress, state, true); }
}
