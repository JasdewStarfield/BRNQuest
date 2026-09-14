package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Reusable item/texture selector row. The owner retains values and commits; callbacks retain its input and sound routing. */
public final class EditorTextureSelector {
    @FunctionalInterface public interface ActionRenderer {
        void render(GuiGraphics graphics, UiRect bounds, EditorButton.Definition definition, boolean enabled, EditorButton.Tone tone);
    }
    private EditorTextureSelector() {}

    /** Returns the rendered geometry so mode switching and item selection use the same hit regions. */
    public static QuestIconEditorRow.Layout render(GuiGraphics graphics, Font font, int left, int top, int width,
            EditorTextField input, boolean textureMode, boolean enabled,
            EditorPropertyPanel.ButtonRenderer buttons, ActionRenderer actions) {
        int labelWidth = 48;
        var row = QuestIconEditorRow.layout(left, top, width, labelWidth);
        String label = font.plainSubstrByWidth(Component.translatable("screen.brnquest.editor.quest.icon").getString(),
                row.label().width() - 4);
        graphics.drawString(font, Component.literal(label), row.label().left(), row.label().top() + 5,
                GraystonePalette.SECONDARY, false);
        buttons.render(graphics, row.mode(), Component.translatable(textureMode ? "screen.brnquest.editor.quest.icon_mode.texture" : "screen.brnquest.editor.quest.icon_mode.item"),
                enabled, EditorButton.Tone.NEUTRAL);
        input.show(row.input(), enabled);
        ResourceLocation iconId = ResourceLocation.tryParse(input.getValue().strip());
        if (!textureMode
                && iconId != null && BuiltInRegistries.ITEM.containsKey(iconId)) {
            Component select = Component.translatable("screen.brnquest.editor.quest.icon.select_item");
            ItemStack stack = BuiltInRegistries.ITEM.get(iconId).getDefaultInstance();
            EditorButton.Definition definition = EditorButton.Definition.iconOnly(
                    select, select, EditorIcon.item(stack));
            actions.render(graphics, row.picker(), definition,
                    enabled, EditorButton.Tone.NEUTRAL);
            // Item artwork describes the picker action; it must not advertise recipe shortcuts.
        } else if (!textureMode) {
            Component select = Component.translatable("screen.brnquest.editor.quest.icon.select_item");
                    actions.render(graphics, row.picker(), EditorButton.Definition.iconOnly(
                            select, select, QuestActionIcons.symbol(Component.literal(
                                    input.getValue().isBlank() ? "+" : "?"))),
                    enabled, EditorButton.Tone.NEUTRAL);
        } else {
            // The preview remains a real picker action even when a pack is absent or the ID is empty.
            Component select = Component.translatable("screen.brnquest.editor.quest.icon.select_texture");
            EditorIcon preview = iconId == null ? QuestActionIcons.named("plus") : new EditorIcon() {
                public int width(Font ignored) { return 16; }
                public void render(GuiGraphics g, Font ignored, UiRect bounds, int color) {
                    int size = Math.min(16, Math.min(bounds.width(), bounds.height()));
                    yourscraft.jasdewstarfield.brnquest.client.ui.LoadedTextures.draw(g, iconId.toString(),
                            bounds.centerX() - size / 2, bounds.centerY() - size / 2, size, size, (color >>> 24) / 255F);
                }
            };
            // The image itself is the button, with the same accessible label for empty/missing textures.
            actions.render(graphics, row.picker(), EditorButton.Definition.iconOnly(select, select, preview),
                    enabled, EditorButton.Tone.NEUTRAL);
        }
        return row;
    }
}
