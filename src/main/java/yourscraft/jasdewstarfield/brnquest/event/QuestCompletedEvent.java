package yourscraft.jasdewstarfield.brnquest.event;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.ProgressView;
import yourscraft.jasdewstarfield.brnquest.api.QuestView;

import java.util.UUID;

/** Read-only notification emitted after quest completion is persisted. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record QuestCompletedEvent(UUID playerId, String playerName, ResourceLocation bookId,
                                  ResourceLocation questId, QuestView quest, ProgressView progress)
        implements BrnQuestEvent {}
