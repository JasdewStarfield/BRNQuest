package yourscraft.jasdewstarfield.brnquest.task;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;

/** Public task extension point using immutable definition and progress projections. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public interface TaskType<TConfig> {
    Codec<TConfig> configCodec();
    boolean satisfied(TaskContext context, TConfig config);
    default boolean consume(TaskContext context, TConfig config) { return true; }
    default boolean allowsManualSubmission(TConfig config) { return false; }
    default boolean acceptsQuestCompletionIntent(TConfig config) { return false; }
    default boolean reevaluateOnInventoryChange(TConfig config) { return false; }

    default TaskSubmissionResult submit(TaskContext context, TConfig config) {
        if (!satisfied(context, config)) {
            return TaskSubmissionResult.failure("UNSATISFIED", "Task requirements are incomplete");
        }
        if (!consume(context, config)) {
            return TaskSubmissionResult.failure("CONSUME_FAILED", "Task resources could not be consumed");
        }
        return TaskSubmissionResult.accepted();
    }

    Component describe(TaskView task, TConfig config);
}
