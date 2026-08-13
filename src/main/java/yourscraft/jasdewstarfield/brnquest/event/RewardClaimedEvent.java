package yourscraft.jasdewstarfield.brnquest.event;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.ProgressView;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;

import java.util.UUID;

/** Read-only notification emitted after a reward side effect succeeds. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record RewardClaimedEvent(UUID playerId, String playerName, ResourceLocation bookId,
                                 ResourceLocation questId, ResourceLocation rewardId,
                                 RewardView reward, ProgressView progress) implements BrnQuestEvent {}
