package yourscraft.jasdewstarfield.brnquest.progress;

import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.event.BrnQuestEvent;
import yourscraft.jasdewstarfield.brnquest.event.BrnQuestEvents;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;

/** The two outward effects of a progress transaction, kept in call order and observable by server fixtures. */
interface ProgressEffects {
    ProgressEffects LIVE = new ProgressEffects() {
        public void publish(BrnQuestEvent event) { BrnQuestEvents.post(event); }
        public void sync(ServerPlayer player, boolean changed) { BrnQuestNetwork.syncProgress(player, changed); }
    };

    void publish(BrnQuestEvent event);
    void sync(ServerPlayer player, boolean changed);
}
