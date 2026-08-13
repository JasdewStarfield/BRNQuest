package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;

/** Client-only presentation contract paired with a server task type by full ID. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public interface ClientTaskPresentation {
    enum NodeStyle { ITEM, CHECKMARK, CUSTOM, PLACEHOLDER }

    default NodeStyle nodeStyle(TaskDefinition task) { return NodeStyle.PLACEHOLDER; }
    default String itemSnbt(TaskDefinition task) { return ""; }
    default String symbol(TaskDefinition task) { return "?"; }
    default boolean interactive(TaskDefinition task) { return false; }
    default boolean acceptsQuestCompletionIntent(TaskDefinition task) { return false; }

    default boolean satisfied(Minecraft minecraft, TaskDefinition task, QuestStatus status,
                              long storedProgress, ItemStack displayedItem) {
        return status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED || storedProgress >= 1;
    }

    default Component progressText(Minecraft minecraft, TaskDefinition task, boolean satisfied,
                                   long storedProgress, ItemStack displayedItem) {
        return Component.literal(storedProgress + " / 1");
    }

    default Component fallbackTitle(Minecraft minecraft, TaskDefinition task, ItemStack displayedItem) {
        String configured = task.config().getOrDefault("title", "");
        return Component.literal(configured.isBlank() ? task.typeId().toString() : configured);
    }
}
