package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;

/** Client-only icon, title and interaction contract paired with a reward type by full ID. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public interface ClientRewardPresentation {
    default String itemSnbt(RewardView reward) { return ""; }
    default String symbol(RewardView reward) { return "?"; }
    /** Returns a defensive display copy; item rewards may apply a separate configured multiplier. */
    default ItemStack displayedItem(RewardView reward, ItemStack parsedItem) {
        return parsedItem == null ? ItemStack.EMPTY : parsedItem.copy();
    }
    /** Localized type label used by authoring lists; unknown extensions retain their full ID. */
    default Component typeName(RewardView reward) { return Component.literal(reward.typeId().toString()); }

    default Component title(RewardPresentationContext context) {
        String configured = context.reward().config().getOrDefault("title", "");
        if (!configured.isBlank()) return Component.literal(configured);
        return context.displayedItem().isEmpty()
                ? Component.literal(context.reward().typeId().toString())
                : context.displayedItem().getHoverName();
    }

    default Component interactionHint(RewardPresentationContext context) {
        if (context.claimed()) return Component.translatable("screen.brnquest.reward.claimed");
        if (context.claimable()) return Component.translatable("screen.brnquest.reward.click_to_claim");
        return title(context);
    }
}
