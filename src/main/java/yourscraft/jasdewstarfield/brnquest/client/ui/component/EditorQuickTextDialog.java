package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Compact one-field modal for editing text directly from the surface where it is displayed. */
public final class EditorQuickTextDialog {
    public enum Action { NONE, CANCEL, APPLY }

    public record Layout(UiRect dialog, UiRect input, UiRect cancel, UiRect apply) {}

    private EditorQuickTextDialog() {}

    public static Layout layout(QuestScreenLayout screenLayout) {
        int dialogWidth = Math.min(420, Math.max(240, screenLayout.width() - 40));
        int left = (screenLayout.width() - dialogWidth) / 2;
        int top = Math.max(28, (screenLayout.height() - 92) / 2);
        UiRect dialog = new UiRect(left, top, left + dialogWidth, top + 92);
        UiRect input = new UiRect(dialog.left() + 12, dialog.top() + 31,
                dialog.right() - 12, dialog.top() + 51);
        UiRect cancel = new UiRect(dialog.left() + 12, dialog.bottom() - 28,
                dialog.centerX() - 4, dialog.bottom() - 8);
        UiRect apply = new UiRect(dialog.centerX() + 4, dialog.bottom() - 28,
                dialog.right() - 12, dialog.bottom() - 8);
        return new Layout(dialog, input, cancel, apply);
    }

    public static void render(GuiGraphics graphics, Font font, QuestScreenLayout screenLayout, EditorButtonInput buttons,
                              Component title, Component issue, boolean applyEnabled,
                              int mouseX, int mouseY) {
        Layout layout = layout(screenLayout);
        graphics.fill(0, 0, screenLayout.width(), screenLayout.height(), 0x99000000);
        graphics.fill(layout.dialog().left(), layout.dialog().top(),
                layout.dialog().right(), layout.dialog().bottom(), 0xFF202832);
        graphics.drawString(font, Component.literal(font.plainSubstrByWidth(
                        title.getString(), layout.dialog().width() - 24)),
                layout.dialog().left() + 12, layout.dialog().top() + 11, 0xFFFFFFFF, false);
        if (issue != null) {
            graphics.drawString(font, Component.literal(font.plainSubstrByWidth(
                            issue.getString(), layout.dialog().width() - 24)),
                    layout.dialog().left() + 12, layout.dialog().top() + 54, 0xFFFF8B8B, false);
        }
        buttons.render(graphics, font, layout.cancel(),
                EditorButton.Definition.text(Component.translatable("gui.cancel"), null),
                true, false, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        buttons.render(graphics, font, layout.apply(),
                EditorButton.Definition.text(Component.translatable("gui.done"), null),
                applyEnabled, false, EditorButton.Tone.PRIMARY, mouseX, mouseY);
    }

    public static Action actionAt(QuestScreenLayout screenLayout, double mouseX, double mouseY) {
        Layout layout = layout(screenLayout);
        if (layout.cancel().contains(mouseX, mouseY)) return Action.CANCEL;
        if (layout.apply().contains(mouseX, mouseY)) return Action.APPLY;
        return Action.NONE;
    }
}
