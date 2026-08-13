package yourscraft.jasdewstarfield.brnquest.api;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerId;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;
import java.util.Map;
import java.util.Set;

/** Immutable projection of one owner's quest progress for public API consumers. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record ProgressView(ProgressOwnerId owner, ResourceLocation bookId, ResourceLocation questId, QuestStatus status,
                           Map<ResourceLocation, Long> taskProgress, Set<ResourceLocation> claimedRewards,
                           long completedAtEpochMillis, String revision) {
    public ProgressView {
        taskProgress = Map.copyOf(taskProgress);
        claimedRewards = Set.copyOf(claimedRewards);
    }
}
