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
        } else if (textureMode && iconId != null) {
            graphics.blit(iconId, row.picker().left() + 2, row.picker().top() + 1,
                    0.0F, 0.0F, 16, 16, 16, 16);
        } else {
            graphics.drawCenteredString(font, input.getValue().isBlank() ? "−" : "?",
                    row.picker().centerX(), row.picker().top() + 5, GraystonePalette.SECONDARY);
        }
        return row;
    }
}
