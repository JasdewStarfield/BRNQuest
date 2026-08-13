package yourscraft.jasdewstarfield.brnquest.owner;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable current owner projection returned to integrations. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record ProgressOwnerView(ProgressOwnerId id, Set<UUID> members, ProgressOwnerLifecycle lifecycle) {
    public ProgressOwnerView {
        Objects.requireNonNull(id, "id");
        members = Set.copyOf(members);
        Objects.requireNonNull(lifecycle, "lifecycle");
    }
}
