package yourscraft.jasdewstarfield.brnquest.task;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;

import java.util.List;
import java.util.Map;

/** Public task extension point using immutable definition and progress projections. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public interface TaskType<TConfig> {
    Codec<TConfig> configCodec();
    /** Pure author-write normalization. Preserve keys the type does not own; never perform side effects. */
    default java.util.Map<String, String> normalizeConfig(java.util.Map<String, String> config) { return config; }
    /** Optional server polling. Zero disables polling; samples may only increase persistent progress. */
    default int pollingIntervalTicks() { return 0; }
    default long sampledProgress(TaskContext context, TConfig config) { return context.progress(); }
    /** Clear player-local transient state after an explicit reset, including when saved progress was zero.
     * Called for each online player sharing the reset owner; must not write persistent progress. */
    default void resetTransientState(TaskContext context) {}
    boolean satisfied(TaskContext context, TConfig config);
    default boolean consume(TaskContext context, TConfig config) { return true; }
    default boolean allowsManualSubmission(TConfig config) { return false; }
    default boolean acceptsQuestCompletionIntent(TConfig config) { return false; }
    default boolean reevaluateOnInventoryChange(TConfig config) { return false; }
    /** Optional editor metadata; an empty list selects the safe raw-config fallback. */
    default List<ConfigFieldDescriptor> configFields() { return List.of(); }
    /** Optional canonical editor projection for transparently adapting legacy config shapes. */
    @ApiStatus(ApiStability.INTERNAL)
    default Map<String, String> editorConfig(TaskView task) { return task.config(); }

    default TaskSubmissionResult submit(TaskContext context, TConfig config) {
        if (!satisfied(context, config)) {
            return TaskSubmissionResult.failure("UNSATISFIED", "Task requirements are incomplete");
        }
        if (!consume(context, config)) {
            return TaskSubmissionResult.failure("CONSUME_FAILED", "Task resources could not be consumed");
        }
        return TaskSubmissionResult.accepted();
    }

    /** Player-selected child entries are advisory until this server-side method validates them. */
    @ApiStatus(ApiStability.INTERNAL)
    default TaskSubmissionResult submit(TaskContext context, TConfig config, TaskSubmissionSelection selection) {
        return submit(context, config);
    }

    Component describe(TaskView task, TConfig config);
}
