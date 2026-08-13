package yourscraft.jasdewstarfield.brnquest.owner;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of one progress ledger within a named owner provider. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record ProgressOwnerId(ResourceLocation providerId, UUID ownerId) {
    public ProgressOwnerId {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(ownerId, "ownerId");
    }
}
