package yourscraft.jasdewstarfield.brnquest.compat.kubejs;

import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import yourscraft.jasdewstarfield.brnquest.event.BrnQuestEvents;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Subscribes KubeJS to the loader-neutral observation bus only while KubeJS is present. */
final class BrnQuestKubeJSEventBridge {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private BrnQuestKubeJSEventBridge() {}

    static void install() {
        if (!INSTALLED.compareAndSet(false, true)) return;
        BrnQuestEvents.subscribe(yourscraft.jasdewstarfield.brnquest.event.QuestCompletedEvent.class, event -> {
            ServerPlayer player = player(event.playerId());
            if (player != null && BrnQuestKubeJSEvents.QUEST_COMPLETED.hasListeners(event.questId())) {
                BrnQuestKubeJSEvents.QUEST_COMPLETED.post(ScriptType.SERVER, event.questId(),
                        new QuestCompletedKubeEvent(player, event));
            }
        });
        BrnQuestEvents.subscribe(yourscraft.jasdewstarfield.brnquest.event.TaskProgressChangedEvent.class, event -> {
            ServerPlayer player = player(event.playerId());
            if (player != null && BrnQuestKubeJSEvents.TASK_PROGRESS_CHANGED.hasListeners(event.taskId())) {
                BrnQuestKubeJSEvents.TASK_PROGRESS_CHANGED.post(ScriptType.SERVER, event.taskId(),
                        new TaskProgressChangedKubeEvent(player, event));
            }
        });
        BrnQuestEvents.subscribe(yourscraft.jasdewstarfield.brnquest.event.RewardClaimedEvent.class, event -> {
            ServerPlayer player = player(event.playerId());
            if (player != null && BrnQuestKubeJSEvents.REWARD_CLAIMED.hasListeners(event.rewardId())) {
                BrnQuestKubeJSEvents.REWARD_CLAIMED.post(ScriptType.SERVER, event.rewardId(),
                        new RewardClaimedKubeEvent(player, event));
            }
        });
    }

    private static ServerPlayer player(UUID playerId) {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayer(playerId);
    }
}
