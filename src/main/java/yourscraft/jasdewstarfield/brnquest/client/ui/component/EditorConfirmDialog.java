package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Reusable two-action modal used for destructive and discard confirmations. */
public final class EditorConfirmDialog {
    public enum Action { NONE, CANCEL, CONFIRM }

    public record Layout(UiRect dialog, UiRect cancel, UiRect confirm) {}

    private EditorConfirmDialog() {}

    public static Layout layout(QuestScreenLayout screenLayout) {
        // Preserve the established confirmation geometry while sharing it between dialog types.
        int dialogWidth = Math.min(300, Math.max(220, screenLayout.width() - 40));
        int left = (screenLayout.width() - dialogWidth) / 2;
        int top = (screenLayout.height() - 72) / 2;
        UiRect dialog = new UiRect(left, top, left + dialogWidth, top + 72);
        UiRect cancel = new UiRect(dialog.left() + 10, dialog.bottom() - 26,
                dialog.centerX() - 4, dialog.bottom() - 8);
        UiRect confirm = new UiRect(dialog.centerX() + 4, dialog.bottom() - 26,
                dialog.right() - 10, dialog.bottom() - 8);
        return new Layout(dialog, cancel, confirm);
    }

    public static void render(GuiGraphics graphics, Font font, QuestScreenLayout screenLayout,
                              Component title, Component detail, int detailColor,
                              Component cancelLabel, Component confirmLabel) {
        Layout layout = layout(screenLayout);
        UiRect dialog = layout.dialog();
        graphics.fill(0, 0, screenLayout.width(), screenLayout.height(), 0x88000000);
        graphics.fill(dialog.left(), dialog.top(), dialog.right(), dialog.bottom(), 0xFF202832);
        graphics.drawCenteredString(font, title, dialog.centerX(), dialog.top() + (detail == null ? 12 : 9),
                0xFFFFFFFF);
        if (detail != null) {
            String visible = font.plainSubstrByWidth(detail.getString(), dialog.width() - 20);
            graphics.drawCenteredString(font, Component.literal(visible), dialog.centerX(), dialog.top() + 23,
                    detailColor);
        }
        EditorButton.render(graphics, font, layout.cancel(), cancelLabel, 0xFF385A72, 0xFFFFFFFF, 4);
        EditorButton.render(graphics, font, layout.confirm(), confirmLabel, 0xFF723E46, 0xFFFFFFFF, 4);
    }

    public static Action actionAt(QuestScreenLayout screenLayout, double mouseX, double mouseY) {
        Layout layout = layout(screenLayout);
        if (layout.cancel().contains(mouseX, mouseY)) return Action.CANCEL;
        if (layout.confirm().contains(mouseX, mouseY)) return Action.CONFIRM;
        return Action.NONE;
    }
}
