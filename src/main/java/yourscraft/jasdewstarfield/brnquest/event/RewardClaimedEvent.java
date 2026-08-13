package yourscraft.jasdewstarfield.brnquest.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

/** Server event emitted once after a reward's idempotent side effect succeeds. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public final class RewardClaimedEvent extends Event {
    private final ServerPlayer player;
    private final ResourceLocation bookId;
    private final ResourceLocation rewardId;
    public RewardClaimedEvent(ServerPlayer player, ResourceLocation bookId, ResourceLocation rewardId) { this.player = player; this.bookId = bookId; this.rewardId = rewardId; }
    public ServerPlayer player() { return player; }
    public ResourceLocation bookId() { return bookId; }
    public ResourceLocation rewardId() { return rewardId; }
}
