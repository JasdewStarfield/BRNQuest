package yourscraft.jasdewstarfield.brnquest.event;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

/** Idempotent listener handle; close it to stop receiving future events. */
@FunctionalInterface
@ApiStatus(ApiStability.EXPERIMENTAL)
public interface EventSubscription extends AutoCloseable {
    @Override
    void close();
}
