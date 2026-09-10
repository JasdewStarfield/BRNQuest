package yourscraft.jasdewstarfield.brnquest.reward;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** One inventory/overflow implementation for fixed item rewards and frozen native loot stacks. */
final class ItemRewardDelivery {
    private ItemRewardDelivery() {}
    static void deliver(ServerPlayer player, ItemStack expected) {
        ItemStack stack = expected.copy();
        int before = matchingCount(player, expected);
        player.getInventory().add(stack);
        int inserted = Math.max(0, matchingCount(player, expected) - before);
        int remainder = Math.max(0, expected.getCount() - inserted);
        // Creative mode may clear an uninserted stack. Derive overflow from the actual inventory delta.
        // Server reward delivery bypasses the cancellable player-toss event.
        if (remainder > 0 && player.drop(expected.copyWithCount(remainder), false, false) == null)
            throw new IllegalStateException("Could not deliver reward overflow; review partial effects");
    }
    private static int matchingCount(ServerPlayer player, ItemStack expected) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var candidate = player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameComponents(candidate, expected)) total += candidate.getCount();
        }
        return total;
    }
}
