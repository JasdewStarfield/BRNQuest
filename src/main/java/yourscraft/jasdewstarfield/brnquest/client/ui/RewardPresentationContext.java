package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;

import java.util.Objects;

/** Immutable client-only inputs used to render one reward. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record RewardPresentationContext(Minecraft minecraft, RewardView reward, boolean claimable,
                                        boolean claimed, ItemStack displayedItem) {
    public RewardPresentationContext {
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(reward, "reward");
        displayedItem = displayedItem == null ? ItemStack.EMPTY : displayedItem.copy();
    }
}
