package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;

/** Actions retain their native PNG size, independent of the larger toolbar sprite layout. */
public final class QuestActionIcons {
    private QuestActionIcons() {}

    public static EditorIcon named(String name) {
        ResourceLocation id = ResourceLocation.parse("brnquest:editor/action/" + name);
        // All action assets now share a native 10px canvas, including the revised gift artwork.
        int size = 10;
        return new EditorIcon() {
            public int width(Font font) { return size; }
            public void render(GuiGraphics graphics, Font font, UiRect bounds, int color) {
                // Never stretch the small pixel art; clipping handles unusually narrow controls.
                var sprite = Minecraft.getInstance().getGuiSprites().getSprite(id);
                int x = bounds.centerX() - size / 2, y = bounds.centerY() - size / 2;
                LocalScissor.enable(graphics, bounds);
                try {
                    graphics.blit(x + 1, y + 1, 0, size, size, sprite, .08F, .09F, .07F, (color >>> 24) / 255F * .7F);
                    graphics.blit(x, y, 0, size, size, sprite, (color >> 16 & 255) / 255F,
                            (color >> 8 & 255) / 255F, (color & 255) / 255F, (color >>> 24) / 255F);
                } finally { graphics.disableScissor(); }
            }
        };
    }

    /** Conventional editor action names supply decoration only; dispatch remains with each owning screen. */
    public static EditorIcon action(String action) {
        String key = action.toUpperCase(java.util.Locale.ROOT);
        // Match the existing menu IDs; adding decoration never changes action dispatch.
        String name = key.equals("UP") || key.startsWith("MOVE_") && key.endsWith("_UP") ? "upward"
                : key.equals("DOWN") || key.startsWith("MOVE_") && key.endsWith("_DOWN") ? "downward"
                : key.equals("CHOICE") ? "choice" : key.equals("RANDOM") ? "random" : key.equals("ALL") ? "reward_table"
                : key.startsWith("COPY") ? "copy" : key.startsWith("DELETE") || key.equals("REMOVE") ? "trash"
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
            case "→", ">" -> "forward";
            case "←", "<" -> "back";
            // The current-position field action uses the same location pin as the detail controls.
            case "@" -> "pin";
            case "+" -> "plus";
            case "×" -> "close";
            case "−", "-" -> "minus";
            case "…", "⋯" -> "detail";
            case "{}" -> "raw";
            case "ⓘ" -> "info";
            case "▾" -> "fold";
            case "▸" -> "unfold";
            case "↑" -> "upward";
            case "↓" -> "downward";
            default -> null;
        };
        return name == null ? EditorIcon.glyph(symbol) : named(name);
    }
}
