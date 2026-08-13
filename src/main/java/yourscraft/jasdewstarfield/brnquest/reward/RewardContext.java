package yourscraft.jasdewstarfield.brnquest.reward;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;

import java.util.Objects;

/** Immutable reward execution context; the idempotent ledger remains inside BRNQuest. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record RewardContext(ServerPlayer player, ResourceLocation bookId, ResourceLocation questId,
                            RewardView reward) {
    public RewardContext {
        Objects.requireNonNull(bookId, "bookId");
        Objects.requireNonNull(questId, "questId");
        Objects.requireNonNull(reward, "reward");
        if (!bookId.equals(reward.bookId())) throw new IllegalArgumentException("Reward belongs to a different book");
    }
}
