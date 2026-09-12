package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Visual content that can occupy an editor button's icon slot.
 *
 * <p>The interface deliberately does not prescribe glyph, texture, item, or sprite
 * rendering. Later icon classes can implement their native rendering without making
 * the generic button depend on JEI, registries, or a particular texture atlas.</p>
 */
@yourscraft.jasdewstarfield.brnquest.api.ApiStatus(yourscraft.jasdewstarfield.brnquest.api.ApiStability.EXPERIMENTAL)
public interface EditorIcon {
    /** GUI-atlas sprite with the shared pixel shadow; resolves the atlas at render time for resource reloads. */
    static EditorIcon sprite(net.minecraft.resources.ResourceLocation sprite) {
        java.util.Objects.requireNonNull(sprite);
        return new EditorIcon() {
            public int width(Font font) { return 16; }
            public void render(GuiGraphics graphics, Font font, UiRect bounds, int color) {
                // Keep the previous integer grid and tint while allowing resource-pack replacements.
                int size = Math.min(bounds.width(), bounds.height()) / 8 * 8;
                if (size < 8) return;
                var texture = net.minecraft.client.Minecraft.getInstance().getGuiSprites().getSprite(sprite);
                // Reuse the PNG alpha silhouette for a one-pixel shadow; edits and resource reloads follow automatically.
                graphics.blit(bounds.centerX() - size / 2 + 1, bounds.centerY() - size / 2 + 1, 0, size, size,
                        texture, 0.08F, 0.09F, 0.07F, (color >>> 24) / 255F * 0.7F);
                // Vertex tint avoids changing global shader color for every icon.
                graphics.blit(bounds.centerX() - size / 2, bounds.centerY() - size / 2, 0, size, size,
                        texture,
                        (color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F,
                        (color & 255) / 255F, (color >>> 24) / 255F);
            }
        };
    }

    int width(Font font);

    void render(GuiGraphics graphics, Font font, UiRect bounds, int color);

    /** Creates a lightweight icon from a font glyph while real sprite icons are introduced incrementally. */
    static EditorIcon glyph(Component glyph) {
        if (glyph == null) throw new IllegalArgumentException("glyph is required");
        return new Glyph(glyph);
    }

    /** Creates an icon from a defensive count-one copy suitable for ghost previews and buttons. */
    static EditorIcon item(ItemStack stack) {
        if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("item stack is required");
        return new Item(stack.copyWithCount(1));
    }

    /** Font glyph implementation used by the existing edit/copy/order symbols. */
    record Glyph(Component glyph) implements EditorIcon {
        public Glyph {
            if (glyph == null) throw new IllegalArgumentException("glyph is required");
        }

        @Override
        public int width(Font font) {
            return font.width(glyph);
        }

        @Override
        public void render(GuiGraphics graphics, Font font, UiRect bounds, int color) {
            graphics.drawCenteredString(font, glyph, bounds.centerX(),
                    bounds.top() + Math.max(0, (bounds.height() - font.lineHeight) / 2), color);
        }
    }

    /** Native item renderer implementation; it has no dependency on JEI or editor registries. */
    final class Item implements EditorIcon {
        private final ItemStack stack;

        private Item(ItemStack stack) {
            this.stack = stack.copyWithCount(1);
        }

        @Override
        public int width(Font font) {
            return 16;
        }

        @Override
        public void render(GuiGraphics graphics, Font font, UiRect bounds, int color) {
            graphics.renderItem(stack, bounds.centerX() - 8, bounds.centerY() - 8);
        }
    }
}
