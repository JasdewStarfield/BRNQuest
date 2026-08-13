package yourscraft.jasdewstarfield.brnquest.event;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;

import java.util.UUID;

/** Read-only notification emitted after task progress is persisted in memory. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record TaskProgressChangedEvent(UUID playerId, String playerName, ResourceLocation bookId,
                                       ResourceLocation questId, ResourceLocation taskId, TaskView task,
                                       long previousValue, long currentValue) implements BrnQuestEvent {}
