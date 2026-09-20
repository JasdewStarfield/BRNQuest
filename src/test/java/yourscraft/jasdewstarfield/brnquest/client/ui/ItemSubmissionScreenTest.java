package yourscraft.jasdewstarfield.brnquest.client.ui;
import yourscraft.jasdewstarfield.brnquest.builtin.client.ItemSubmissionScreen;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.RecipeLookupTarget;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemSubmissionScreenTest {
    @Test void lookupUsesTheWholeSlotWithExclusiveRightAndBottomEdges() {
        var stack = new ItemStack(Items.STONE);
        var slot = new UiRect(10,20,28,38);
        assertTrue(ItemSubmissionScreen.recipeLookupTargetAt(stack,slot,10,20).isPresent());
        assertTrue(ItemSubmissionScreen.recipeLookupTargetAt(stack,slot,27.9,37.9).isPresent());
        assertTrue(ItemSubmissionScreen.recipeLookupTargetAt(stack,slot,28,30).isEmpty());
        assertTrue(ItemSubmissionScreen.recipeLookupTargetAt(stack,slot,15,38).isEmpty());
    }
    private static final UiRect ITEM_BOUNDS = new UiRect(10, 20, 26, 36);

    @Test void emptyInventorySlotDoesNotCreateAJeiLookupTarget() {
        assertTrue(ItemSubmissionScreen.recipeLookupTargetAt(ItemStack.EMPTY, ITEM_BOUNDS, 12, 22).isEmpty());
    }

    @Test void visibleInventoryItemCreatesAJeiLookupTarget() {
        Optional<RecipeLookupTarget> target = ItemSubmissionScreen.recipeLookupTargetAt(
                new ItemStack(Items.STONE, 8), ITEM_BOUNDS, 12, 22);

        assertTrue(target.isPresent());
        assertEquals(Items.STONE, target.orElseThrow().stack().getItem());
        assertEquals(1, target.orElseThrow().stack().getCount());
    }

    @Test void slotBorderDoesNotCreateAJeiLookupTarget() {
        assertTrue(ItemSubmissionScreen.recipeLookupTargetAt(
                new ItemStack(Items.STONE), ITEM_BOUNDS, 9, 22).isEmpty());
    }
}
