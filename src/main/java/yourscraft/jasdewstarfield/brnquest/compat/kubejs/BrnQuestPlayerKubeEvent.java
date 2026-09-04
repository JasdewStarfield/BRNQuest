package yourscraft.jasdewstarfield.brnquest.compat.kubejs;

import dev.latvian.mods.kubejs.player.KubePlayerEvent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/** Shared player projection for BRNQuest events without exposing mutable progress internals. */
abstract class BrnQuestPlayerKubeEvent implements KubePlayerEvent {
    private final ServerPlayer player;

    BrnQuestPlayerKubeEvent(ServerPlayer player) {
        this.player = Objects.requireNonNull(player, "player");
    }

    @Override
    public ServerPlayer getEntity() {
        return player;
    }
}
