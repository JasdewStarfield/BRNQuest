package yourscraft.jasdewstarfield.brnquest.task;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

/**
 * Internal opt-in for built-ins whose decoded configs are deeply immutable and safe to share.
 * Decoding must depend only on the string map and this type instance, never registries, a world,
 * player state or time. Ordinary add-on TaskType implementations retain fresh decoding per call.
 */
@ApiStatus(ApiStability.INTERNAL)
public interface ReusableTaskConfigType<T> extends TaskType<T> {}
