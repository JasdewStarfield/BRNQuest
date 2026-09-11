package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Shared inset slot paint; native items and interaction bounds remain owned by each screen. */
public final class EditorItemSlot {
    private static final ResourceLocation NORMAL = sprite("normal");
    private static final ResourceLocation HOVERED = sprite("hovered");
    private static final ResourceLocation SELECTED = sprite("selected");
    private static final ResourceLocation SELECTED_HOVERED = sprite("selected_hovered");

    private EditorItemSlot() {}

    private static ResourceLocation sprite(String state) {
        return ResourceLocation.fromNamespaceAndPath("brnquest", "editor/slot/" + state);
    }

    public static void render(GuiGraphics graphics, UiRect bounds, boolean hovered, boolean selected) {
        // Fixed 18px sprites leave the inner 16px available for items and submit only one quad per slot.
        ResourceLocation texture = selected ? (hovered ? SELECTED_HOVERED : SELECTED)
                : (hovered ? HOVERED : NORMAL);
        graphics.blitSprite(texture, bounds.left(), bounds.top(), bounds.width(), bounds.height());
    }
}
