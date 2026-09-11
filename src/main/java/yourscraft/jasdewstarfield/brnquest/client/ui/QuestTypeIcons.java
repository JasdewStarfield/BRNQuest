package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import java.util.Set;

/** Editable GUI sprites for type choices; these remain decorative, never ingredients. */
final class QuestTypeIcons {
    private QuestTypeIcons() {}
    private static final Set<String> BUILTINS = Set.of("checkmark", "item", "item_choice", "xp", "xp_levels", "command", "location", "observe", "kill", "advancement", "biome", "structure", "dimension", "custom", "reward_table", "loot_table");

    static ResourceLocation sprite(ResourceLocation type) {
        // Foreign types with matching paths must not inherit built-in semantics.
        String name = type.getNamespace().equals("brnquest") && BUILTINS.contains(type.getPath())
                ? type.getPath() : "custom";
        return ResourceLocation.fromNamespaceAndPath("brnquest", "editor/type/" + name);
    }

    static EditorIcon forType(ResourceLocation type) {
        ResourceLocation sprite = sprite(type);
        return new EditorIcon() {
            public int width(Font font) { return 16; }
            public void render(GuiGraphics graphics, Font font, UiRect bounds, int color) {
                // Keep the previous integer grid and tint while allowing resource-pack replacements.
                int size = Math.min(bounds.width(), bounds.height()) / 8 * 8;
                if (size < 8) return;
                var texture = Minecraft.getInstance().getGuiSprites().getSprite(sprite);
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
}
