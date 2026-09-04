package yourscraft.jasdewstarfield.brnquest.compat.kubejs;

import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.event.QuestCompletedEvent;

import java.util.Map;

/** Immutable script view emitted after quest completion has entered authoritative progress. */
public final class QuestCompletedKubeEvent extends BrnQuestPlayerKubeEvent {
    private final QuestCompletedEvent event;

    QuestCompletedKubeEvent(ServerPlayer player, QuestCompletedEvent event) {
        super(player);
        this.event = event;
    }

    public String getPlayerId() { return event.playerId().toString(); }
    public String getPlayerName() { return event.playerName(); }
    public String getBookId() { return event.bookId().toString(); }
    public String getQuestId() { return event.questId().toString(); }
    public Map<String, Object> getQuest() { return BrnQuestKubeJSBindings.quest(event.quest()); }
    public Map<String, Object> getProgress() { return BrnQuestKubeJSBindings.progress(event.progress()); }
}
