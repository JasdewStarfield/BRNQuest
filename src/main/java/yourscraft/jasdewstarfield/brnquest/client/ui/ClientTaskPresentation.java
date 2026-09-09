package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;

import java.util.List;

/** Client-only presentation contract paired with a server task type by full ID. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public interface ClientTaskPresentation {
    /** Decorative icon only: no item tooltip, count overlay, ingredient lookup or submission semantics. */
    default java.util.Optional<yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon> icon(TaskView view) {
        return java.util.Optional.empty();
    }

    enum NodeStyle { ITEM, CHECKMARK, CUSTOM, PLACEHOLDER }

    default NodeStyle nodeStyle(TaskView task) { return NodeStyle.PLACEHOLDER; }
    default String itemSnbt(TaskView task) { return ""; }
    default String symbol(TaskView task) { return "?"; }
    /** Player-facing fallback stays localizable; authoring technical details retain the full type ID separately. */
    default Component typeName(TaskView task) { return Component.translatable("screen.brnquest.type.task.unknown"); }
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
        return configured.isBlank() ? Component.translatable("screen.brnquest.task.unknown")
                : Component.literal(configured);
    }

    /** Detail-row title; built-in item tasks add their consume/observe semantics here. */
    default Component objectiveTitle(TaskPresentationContext context) {
        return title(context);
    }

    /** Representative stack rendered in the compact task row. */
    default ItemStack displayedItem(TaskPresentationContext context) {
        return context.displayedItem();
    }

    /** Candidate stacks shown by the optional secondary candidate Screen. */
    default List<ItemStack> acceptedItems(TaskPresentationContext context) {
        ItemStack displayed = displayedItem(context);
        return displayed.isEmpty() ? List.of() : List.of(displayed.copyWithCount(1));
    }

    /** Whether the compact row should expose its candidate-list button. */
    default boolean hasCandidateMenu(TaskPresentationContext context) {
        return false;
    }

    /**
     * Reports whether a row can be submitted from the player's current client-visible state.
     * The permissive default preserves existing interactive extension behavior; presentations
     * with a locally observable prerequisite can narrow it, while the server always rechecks.
     */
    default boolean readyForSubmission(TaskPresentationContext context) {
        return true;
    }

    /** Tooltip shown for a task without an item tooltip. */
    default Component interactionHint(TaskPresentationContext context, boolean interactive) {
        return interactive ? Component.translatable("screen.brnquest.task.click_to_submit") : title(context);
    }
}
