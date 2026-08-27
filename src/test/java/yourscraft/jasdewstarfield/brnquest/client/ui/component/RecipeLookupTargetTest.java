package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeLookupTargetTest {
    @Test void clippedTargetUsesOnlyTheRenderedIntersection() {
        RecipeLookupTarget target = RecipeLookupTarget.clipped(new ItemStack(Items.STONE, 4),
                new UiRect(10, 20, 26, 36), new UiRect(18, 12, 40, 30)).orElseThrow();

        assertEquals(new UiRect(18, 20, 26, 30), target.bounds());
        assertTrue(target.contains(20, 24));
        assertFalse(target.contains(12, 24));
        assertFalse(target.contains(26, 24));
        assertFalse(target.contains(20, 30));
        assertEquals(1, target.stack().getCount());
    }

    @Test void returnedStackCannotMutateTheStoredLookupIngredient() {
        RecipeLookupTarget target = new RecipeLookupTarget(new ItemStack(Items.DIAMOND, 8),
                new UiRect(0, 0, 16, 16));

        ItemStack first = target.stack();
        first.setCount(32);

        assertEquals(1, target.stack().getCount());
    }

    @Test void fullyClippedOrEmptyItemsDoNotCreateTargets() {
        assertTrue(RecipeLookupTarget.clipped(ItemStack.EMPTY,
                new UiRect(0, 0, 16, 16), new UiRect(0, 0, 16, 16)).isEmpty());
        assertTrue(RecipeLookupTarget.clipped(new ItemStack(Items.STONE),
                new UiRect(0, 0, 16, 16), new UiRect(20, 20, 40, 40)).isEmpty());
    }
}
