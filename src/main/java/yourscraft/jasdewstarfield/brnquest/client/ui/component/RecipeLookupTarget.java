package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Client-neutral description of an item icon that an optional recipe viewer may inspect.
 * Foreign integration types stay outside the normal screen class-loading boundary.
 */
public record RecipeLookupTarget(ItemStack stack, UiRect bounds) {
    public RecipeLookupTarget {
        if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("A visible item stack is required");
        if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) {
            throw new IllegalArgumentException("A visible screen-space area is required");
        }
        stack = stack.copyWithCount(1);
    }

    /** Never exposes the cached rendering stack to optional integrations. */
    @Override
    public ItemStack stack() {
        return stack.copy();
    }

    public boolean contains(double mouseX, double mouseY) {
        // Rendering/scissor rectangles use exclusive right and bottom edges; matching them here
        // prevents a one-pixel lookup target from surviving immediately outside a clipped icon.
        return mouseX >= bounds.left() && mouseX < bounds.right()
                && mouseY >= bounds.top() && mouseY < bounds.bottom();
    }

    /** Clips lookup hit testing to the same viewport that clips the rendered icon. */
    public static Optional<RecipeLookupTarget> clipped(ItemStack stack, UiRect bounds, UiRect viewport) {
        if (stack == null || stack.isEmpty() || bounds == null || viewport == null) return Optional.empty();
        UiRect intersection = new UiRect(
                Math.max(bounds.left(), viewport.left()),
                Math.max(bounds.top(), viewport.top()),
                Math.min(bounds.right(), viewport.right()),
                Math.min(bounds.bottom(), viewport.bottom()));
        return intersection.width() > 0 && intersection.height() > 0
                ? Optional.of(new RecipeLookupTarget(stack, intersection))
                : Optional.empty();
    }
}
