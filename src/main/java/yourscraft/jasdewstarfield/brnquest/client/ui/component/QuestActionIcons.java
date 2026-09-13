package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;

/** Compact actions use native 10px PNGs, independent of the larger toolbar sprite layout. */
public final class QuestActionIcons {
    private QuestActionIcons() {}

    public static EditorIcon named(String name) {
        ResourceLocation id = ResourceLocation.parse("brnquest:editor/action/" + name);
        return new EditorIcon() {
            public int width(Font font) { return 10; }
            public void render(GuiGraphics graphics, Font font, UiRect bounds, int color) {
                // Never stretch the small pixel art; clipping handles unusually narrow controls.
                var sprite = Minecraft.getInstance().getGuiSprites().getSprite(id);
                int x = bounds.centerX() - 5, y = bounds.centerY() - 5;
                graphics.enableScissor(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
                graphics.blit(x + 1, y + 1, 0, 10, 10, sprite, .08F, .09F, .07F, (color >>> 24) / 255F * .7F);
                graphics.blit(x, y, 0, 10, 10, sprite, (color >> 16 & 255) / 255F,
                        (color >> 8 & 255) / 255F, (color & 255) / 255F, (color >>> 24) / 255F);
                graphics.disableScissor();
            }
        };
    }

    /** Conventional editor action names supply decoration only; dispatch remains with each owning screen. */
    public static EditorIcon action(String action) {
        String key = action.toUpperCase(java.util.Locale.ROOT);
        String name = key.startsWith("COPY") ? "copy" : key.startsWith("DELETE") || key.equals("REMOVE") ? "trash"
                : key.startsWith("ADD") ? "plus" : key.startsWith("SAVE") ? "save"
                : key.startsWith("RENAME") || key.equals("EDIT") || key.endsWith("PROPERTIES") ? "edit" : null;
        return name == null ? null : named(name);
    }

    /** Unrecognized extension symbols retain their original presentation. */
    public static EditorIcon symbol(Component symbol) {
        String name = switch (symbol.getString()) {
            case "✎" -> "edit";
            case "✓" -> "check";
            case "★", "☆" -> "reward_table";
            case "→" -> "forward";
            case "+" -> "plus";
            case "×" -> "close";
            default -> null;
        };
        return name == null ? EditorIcon.glyph(symbol) : named(name);
    }
}
