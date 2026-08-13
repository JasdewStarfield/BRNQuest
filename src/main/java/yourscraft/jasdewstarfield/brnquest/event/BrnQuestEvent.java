package yourscraft.jasdewstarfield.brnquest.event;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

/** Marker for immutable BRNQuest observation events. Events are never cancellable. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public sealed interface BrnQuestEvent permits QuestCompletedEvent, RewardClaimedEvent,
        TaskProgressChangedEvent, QuestBookReloadedEvent, ProgressOwnerChangedEvent {}
