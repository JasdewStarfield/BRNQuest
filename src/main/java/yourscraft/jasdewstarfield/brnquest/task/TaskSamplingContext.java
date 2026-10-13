package yourscraft.jasdewstarfield.brnquest.task;

import yourscraft.jasdewstarfield.brnquest.api.*;
import java.util.Objects;

/** Current viewer's immutable state, routed independently from the shared or all-member objective count. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record TaskSamplingContext(TaskContext task, TaskSamplingState state, boolean shared, TaskSampleValues values, boolean resuming) {
    public TaskSamplingContext { Objects.requireNonNull(task); Objects.requireNonNull(state); Objects.requireNonNull(values); }
    public TaskSamplingContext(TaskContext task, TaskSamplingState state, boolean shared, TaskSampleValues values) {
        this(task, state, shared, values, false);
    }
    public TaskSamplingContext(TaskContext task, TaskSamplingState state, boolean shared) {
        this(task, state, shared, new TaskSampleValues());
    }
}
