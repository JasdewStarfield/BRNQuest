package yourscraft.jasdewstarfield.brnquest.event;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerId;

import java.util.UUID;

/** Published after a player is safely rebound from one stable owner to another. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record ProgressOwnerChangedEvent(UUID playerId, ProgressOwnerId previousOwner, ProgressOwnerId currentOwner)
        implements BrnQuestEvent {}
