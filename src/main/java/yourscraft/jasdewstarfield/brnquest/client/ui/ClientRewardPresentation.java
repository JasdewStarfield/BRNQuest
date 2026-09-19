package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;

/** Client-only icon, title and interaction contract paired with a reward type by full ID. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public interface ClientRewardPresentation {
    /** Type-picker icon without a task/reward instance; empty keeps registration metadata or the generic fallback. */
    default java.util.Optional<yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon> typeIcon() {
        return java.util.Optional.empty();
    }

    /** Decorative icon only: no item tooltip, count overlay, ingredient lookup or submission semantics. */
    default java.util.Optional<yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon> icon(RewardView view) {
        return java.util.Optional.empty();
    }

    /** Resolved, read-only choices. Empty Optional means no browser; an empty list means no resolved members. */
    default java.util.Optional<java.util.List<Component>> resolvedOptions(RewardView view) { return java.util.Optional.empty(); }

    default String itemSnbt(RewardView reward) { return ""; }
    default String symbol(RewardView reward) { return "?"; }
    /** Returns a defensive display copy; item rewards may apply a separate configured multiplier. */
    default ItemStack displayedItem(RewardView reward, ItemStack parsedItem) {
        return parsedItem == null ? ItemStack.EMPTY : parsedItem.copy();
    }
    /** Player-facing fallback stays localizable; authoring technical details retain the full type ID separately. */
    default Component typeName(RewardView reward) { return Component.translatable("screen.brnquest.type.reward.unknown"); }

    /**
     * Configuration-only contents for live rows, author previews and frozen choices.
     * Omit the configured title: the shared layout adds it once. Do not query claim state
     * or send requests here. Empty preserves the legacy item/title fallback.
     */
    default java.util.Optional<Component> contentSummary(RewardView reward) {
        return java.util.Optional.empty();
    }

    default Component title(RewardPresentationContext context) {
        String configured = context.reward().config().getOrDefault("title", "");
        if (!configured.isBlank()) return Component.literal(configured);
        return context.displayedItem().isEmpty()
                ? Component.translatable("screen.brnquest.reward.unknown")
                : context.displayedItem().getHoverName();
    }

    default Component interactionHint(RewardPresentationContext context) {
        if (context.claimed()) return Component.translatable("screen.brnquest.reward.claimed");
        if (context.claimable()) return Component.translatable("screen.brnquest.reward.click_to_claim");
        return title(context);
    }
}
