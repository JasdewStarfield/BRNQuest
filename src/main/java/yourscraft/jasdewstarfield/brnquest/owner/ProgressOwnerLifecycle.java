package yourscraft.jasdewstarfield.brnquest.owner;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

/** Provider-reported lifecycle of a stable progress owner. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public enum ProgressOwnerLifecycle {
    ACTIVE,
    ARCHIVED,
    UNAVAILABLE
}
