package yourscraft.jasdewstarfield.brnquest.compat.kubejs;

import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.event.RewardClaimedEvent;

import java.util.Map;

/** Immutable observation emitted only after a reward side effect reports success. */
public final class RewardClaimedKubeEvent extends BrnQuestPlayerKubeEvent {
    private final RewardClaimedEvent event;

    RewardClaimedKubeEvent(ServerPlayer player, RewardClaimedEvent event) {
        super(player);
        this.event = event;
    }

    public String getPlayerId() { return event.playerId().toString(); }
    public String getPlayerName() { return event.playerName(); }
    public String getBookId() { return event.bookId().toString(); }
    public String getQuestId() { return event.questId().toString(); }
    public String getRewardId() { return event.rewardId().toString(); }
    public Map<String, Object> getReward() { return BrnQuestKubeJSBindings.reward(event.reward()); }
    public Map<String, Object> getProgress() { return BrnQuestKubeJSBindings.progress(event.progress()); }
}
