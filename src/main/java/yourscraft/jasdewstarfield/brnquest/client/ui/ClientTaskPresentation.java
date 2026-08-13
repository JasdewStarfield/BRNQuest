package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;

/** Client-only presentation contract paired with a server task type by full ID. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public interface ClientTaskPresentation {
    enum NodeStyle { ITEM, CHECKMARK, CUSTOM, PLACEHOLDER }

    default NodeStyle nodeStyle(TaskView task) { return NodeStyle.PLACEHOLDER; }
    default String itemSnbt(TaskView task) { return ""; }
    default String symbol(TaskView task) { return "?"; }
    default boolean interactive(TaskView task) { return false; }
    default boolean acceptsQuestCompletionIntent(TaskView task) { return false; }

    default boolean satisfied(TaskPresentationContext context) {
        return context.questStatus() == QuestStatus.COMPLETED
                || context.questStatus() == QuestStatus.REWARD_CLAIMED
                || context.storedProgress() >= 1;
    }

    default Component progressText(TaskPresentationContext context, boolean satisfied) {
        return Component.literal(context.storedProgress() + " / 1");
    }

    default Component title(TaskPresentationContext context) {
        String configured = context.task().config().getOrDefault("title", "");
        return Component.literal(configured.isBlank() ? context.task().typeId().toString() : configured);
    }

    /** Tooltip shown for a task without an item tooltip. */
    default Component interactionHint(TaskPresentationContext context, boolean interactive) {
        return interactive ? Component.translatable("screen.brnquest.task.click_to_submit") : title(context);
    }
}
