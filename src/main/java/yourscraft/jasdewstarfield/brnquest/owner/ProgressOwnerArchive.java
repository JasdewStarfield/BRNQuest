package yourscraft.jasdewstarfield.brnquest.owner;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable provider snapshot retained after an owner is archived. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record ProgressOwnerArchive(ProgressOwnerId owner, Set<UUID> members,
                                   long archivedAtEpochMillis, String reason) {
    public ProgressOwnerArchive {
        Objects.requireNonNull(owner, "owner");
        members = Set.copyOf(members);
        if (archivedAtEpochMillis < 0) throw new IllegalArgumentException("Archive time must not be negative");
        reason = Objects.requireNonNull(reason, "reason");
    }
}
