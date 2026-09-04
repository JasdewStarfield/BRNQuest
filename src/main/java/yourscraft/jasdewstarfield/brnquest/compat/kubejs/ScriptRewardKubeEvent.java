package yourscraft.jasdewstarfield.brnquest.compat.kubejs;

import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.reward.RewardContext;

import java.util.Map;

/** Script reward invocation carrying a stable key that survives retries and restarts. */
public final class ScriptRewardKubeEvent extends BrnQuestPlayerKubeEvent {
    private final RewardContext context;
    private final String idempotencyKey;

    ScriptRewardKubeEvent(ServerPlayer player, RewardContext context, String idempotencyKey) {
        super(player);
        this.context = context;
        this.idempotencyKey = idempotencyKey;
    }

    public String getBookId() { return context.bookId().toString(); }
    public String getQuestId() { return context.questId().toString(); }
    public String getRewardId() { return context.reward().id().toString(); }
    public String getTypeId() { return context.reward().typeId().toString(); }
    public Map<String, String> getConfig() { return context.reward().config(); }
    public String getIdempotencyKey() { return idempotencyKey; }
}
