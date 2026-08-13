package yourscraft.jasdewstarfield.brnquest.task;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.data.StringMapConfigCodec;

import java.util.Optional;

/** Internal generic bridge that keeps decoded helpers out of the public TaskType contract. */
@ApiStatus(ApiStability.INTERNAL)
public final class TaskTypeExecutor {
    private TaskTypeExecutor() {}

    public static Optional<String> configError(TaskType<?> type, TaskView task) {
        return decode(type, task).error().map(error -> error.message());
    }

    public static boolean satisfied(TaskType<?> type, TaskContext context) {
        return execute(type, context, Invocation.SATISFIED).orElse(false);
    }

    public static boolean consume(TaskType<?> type, TaskContext context) {
        return execute(type, context, Invocation.CONSUME).orElse(false);
    }

    public static boolean allowsManualSubmission(TaskType<?> type, TaskView task) {
        return flag(type, task, Flag.MANUAL_SUBMISSION);
    }

    public static boolean acceptsQuestCompletionIntent(TaskType<?> type, TaskView task) {
        return flag(type, task, Flag.QUEST_COMPLETION);
    }

    public static boolean reevaluateOnInventoryChange(TaskType<?> type, TaskView task) {
        return flag(type, task, Flag.INVENTORY_REEVALUATION);
    }

    public static TaskSubmissionResult submit(TaskType<?> type, TaskContext context) {
        return submitTyped(type, context);
    }

    private enum Invocation { SATISFIED, CONSUME }
    private enum Flag { MANUAL_SUBMISSION, QUEST_COMPLETION, INVENTORY_REEVALUATION }

    private static Optional<Boolean> execute(TaskType<?> type, TaskContext context, Invocation invocation) {
        return executeTyped(type, context, invocation);
    }

    private static <T> Optional<Boolean> executeTyped(TaskType<T> type, TaskContext context, Invocation invocation) {
        return StringMapConfigCodec.decode(type.configCodec(), context.task().config()).result().map(config ->
                invocation == Invocation.SATISFIED ? type.satisfied(context, config) : type.consume(context, config));
    }

    private static <T> TaskSubmissionResult submitTyped(TaskType<T> type, TaskContext context) {
        return StringMapConfigCodec.decode(type.configCodec(), context.task().config()).result()
                .map(config -> type.submit(context, config))
                .orElseGet(() -> TaskSubmissionResult.failure("INVALID_CONFIG", "Task configuration is invalid"));
    }

    private static <T> com.mojang.serialization.DataResult<T> decode(TaskType<T> type, TaskView task) {
        return StringMapConfigCodec.decode(type.configCodec(), task.config());
    }

    private static boolean flag(TaskType<?> type, TaskView task, Flag flag) {
        return flagTyped(type, task, flag);
    }

    private static <T> boolean flagTyped(TaskType<T> type, TaskView task, Flag flag) {
        return decode(type, task).result().map(config -> switch (flag) {
            case MANUAL_SUBMISSION -> type.allowsManualSubmission(config);
            case QUEST_COMPLETION -> type.acceptsQuestCompletionIntent(config);
            case INVENTORY_REEVALUATION -> type.reevaluateOnInventoryChange(config);
        }).orElse(false);
    }
}
