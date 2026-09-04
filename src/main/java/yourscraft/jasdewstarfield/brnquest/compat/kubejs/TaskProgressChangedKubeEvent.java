package yourscraft.jasdewstarfield.brnquest.compat.kubejs;

import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.event.TaskProgressChangedEvent;

import java.util.Map;

/** Immutable script view emitted for committed task progress changes. */
public final class TaskProgressChangedKubeEvent extends BrnQuestPlayerKubeEvent {
    private final TaskProgressChangedEvent event;

    TaskProgressChangedKubeEvent(ServerPlayer player, TaskProgressChangedEvent event) {
        super(player);
        this.event = event;
    }

    public String getPlayerId() { return event.playerId().toString(); }
    public String getPlayerName() { return event.playerName(); }
    public String getBookId() { return event.bookId().toString(); }
    public String getQuestId() { return event.questId().toString(); }
    public String getTaskId() { return event.taskId().toString(); }
    public Map<String, Object> getTask() { return BrnQuestKubeJSBindings.task(event.task()); }
    public long getPreviousValue() { return event.previousValue(); }
    public long getCurrentValue() { return event.currentValue(); }
    public long getDelta() { return event.currentValue() - event.previousValue(); }
}
