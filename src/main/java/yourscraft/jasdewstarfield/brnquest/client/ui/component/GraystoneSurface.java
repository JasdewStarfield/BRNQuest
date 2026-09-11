package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Small shared pixel borders; changes paint only and never expand a control's hit area. */
public final class GraystoneSurface {
    private static final ResourceLocation FILL = sprite("fill");
    private static final ResourceLocation BORDER = sprite("border");
    private static final ResourceLocation DISABLED_BORDER = sprite("border_disabled");

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath("brnquest", "editor/graystone/" + name);
    }

    private GraystoneSurface() {}
    public static void raised(GuiGraphics graphics, UiRect bounds, int fill, boolean enabled) {
        if (bounds.width() < 4 || bounds.height() < 4) {
            graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), fill);
            return;
        }
        // Separate tinted fill from untinted bevel so semantic palettes keep their existing colors.
        // The sprites' nine-slice metadata keeps both border pixels fixed at any control size.
        StretchedGuiSprite.render(graphics, FILL, bounds, fill);
        StretchedGuiSprite.render(graphics, enabled ? BORDER : DISABLED_BORDER, bounds, 0xFFFFFFFF);
    }
}
