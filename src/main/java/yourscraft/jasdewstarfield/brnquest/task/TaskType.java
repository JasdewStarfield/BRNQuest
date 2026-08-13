package yourscraft.jasdewstarfield.brnquest.task;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.data.StringMapConfigCodec;
import yourscraft.jasdewstarfield.brnquest.progress.PlayerProgress;

import java.util.Optional;

/** Public task extension point for config decoding, tracking and read-only descriptions. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public interface TaskType<TConfig> {
    Codec<TConfig> configCodec();
    boolean satisfied(ServerPlayer player, TaskDefinition definition, TConfig config, PlayerProgress progress);
    default boolean consume(ServerPlayer player, TaskDefinition definition, TConfig config) { return true; }
    default boolean allowsManualSubmission(TConfig config) { return false; }
    default boolean acceptsQuestCompletionIntent(TConfig config) { return false; }
    default boolean reevaluateOnInventoryChange(TConfig config) { return false; }
    default TaskSubmissionResult submit(ServerPlayer player, TaskDefinition definition, TConfig config, PlayerProgress progress) {
        if (!satisfied(player, definition, config, progress)) {
            return TaskSubmissionResult.failure("UNSATISFIED", "Task requirements are incomplete");
        }
        if (!consume(player, definition, config)) {
            return TaskSubmissionResult.failure("CONSUME_FAILED", "Task resources could not be consumed");
        }
        return TaskSubmissionResult.accepted();
    }
    Component describe(TaskDefinition definition, TConfig config);

    /** Returns a diagnostic message when schema-1 config cannot be decoded by this type. */
    default Optional<String> configError(TaskDefinition definition) {
        return StringMapConfigCodec.decode(configCodec(), definition.config()).error().map(error -> error.message());
    }

    default boolean satisfiedDecoded(ServerPlayer player, TaskDefinition definition, PlayerProgress progress) {
        return decoded(definition).map(config -> satisfied(player, definition, config, progress)).orElse(false);
    }

    default boolean consumeDecoded(ServerPlayer player, TaskDefinition definition) {
        return decoded(definition).map(config -> consume(player, definition, config)).orElse(false);
    }

    default boolean allowsManualSubmissionDecoded(TaskDefinition definition) {
        return decoded(definition).map(this::allowsManualSubmission).orElse(false);
    }

    default boolean acceptsQuestCompletionIntentDecoded(TaskDefinition definition) {
        return decoded(definition).map(this::acceptsQuestCompletionIntent).orElse(false);
    }

    default boolean reevaluateOnInventoryChangeDecoded(TaskDefinition definition) {
        return decoded(definition).map(this::reevaluateOnInventoryChange).orElse(false);
    }

    default TaskSubmissionResult submitDecoded(ServerPlayer player, TaskDefinition definition, PlayerProgress progress) {
        return decoded(definition).map(config -> submit(player, definition, config, progress))
                .orElseGet(() -> TaskSubmissionResult.failure("INVALID_CONFIG", "Task configuration is invalid"));
    }

    private Optional<TConfig> decoded(TaskDefinition definition) {
        return StringMapConfigCodec.decode(configCodec(), definition.config()).result();
    }
}
