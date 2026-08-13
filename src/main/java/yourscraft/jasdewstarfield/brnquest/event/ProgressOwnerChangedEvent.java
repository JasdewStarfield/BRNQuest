package yourscraft.jasdewstarfield.brnquest.event;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

import java.util.UUID;

/** Owner transition contract reserved for the stage-3 ProgressOwner SPI. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record ProgressOwnerChangedEvent(UUID playerId, ResourceLocation previousProvider, UUID previousOwnerId,
                                        ResourceLocation currentProvider, UUID currentOwnerId)
        implements BrnQuestEvent {}
