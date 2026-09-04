package yourscraft.jasdewstarfield.brnquest.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventTargetType;
import dev.latvian.mods.kubejs.event.TargetedEventHandler;
import net.minecraft.resources.ResourceLocation;

/** Server-only event group exposed as {@code BRNQuestEvents} in KubeJS scripts. */
public final class BrnQuestKubeJSEvents {
    public static final EventGroup GROUP = EventGroup.of("BRNQuestEvents");
    private static final EventTargetType<ResourceLocation> ID_TARGET = EventTargetType.ID;

    public static final TargetedEventHandler<ResourceLocation> QUEST_COMPLETED = GROUP
            .server("questCompleted", () -> QuestCompletedKubeEvent.class).supportsTarget(ID_TARGET);
    public static final TargetedEventHandler<ResourceLocation> TASK_PROGRESS_CHANGED = GROUP
            .server("taskProgressChanged", () -> TaskProgressChangedKubeEvent.class).supportsTarget(ID_TARGET);
    public static final TargetedEventHandler<ResourceLocation> REWARD_CLAIMED = GROUP
            .server("rewardClaimed", () -> RewardClaimedKubeEvent.class).supportsTarget(ID_TARGET);
    public static final TargetedEventHandler<ResourceLocation> CUSTOM_REWARD = GROUP
            .server("customReward", () -> ScriptRewardKubeEvent.class).requiredTarget(ID_TARGET);

    private BrnQuestKubeJSEvents() {}
}
