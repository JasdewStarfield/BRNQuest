package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Two-line entry presentation usable by any list: no task/reward types, registry access or actions inside. */
public final class EditorEntryRow {
    public record Content(EditorIcon icon, Component title, Component summary, int summaryColor) {}
    public record Layout(UiRect bounds, UiRect icon, UiRect title, UiRect summary, UiRect actions) {}

    private EditorEntryRow() {}

    /** The action area is reserved once so labels and buttons cannot independently claim the same pixels. */
    public static Layout layout(UiRect bounds, int actionCount, int buttonWidth, int gap) {
        int actionWidth = Math.max(0, actionCount * (buttonWidth + gap) - gap);
        int actionLeft = Math.max(bounds.left(), bounds.right() - actionWidth);
        int textRight = Math.max(bounds.left(), actionLeft - 4);
        int textLeft = Math.min(textRight, bounds.left() + 26);
        // Very narrow consumers retain actions without drawing an item underneath those controls.
        UiRect icon = textRight - bounds.left() >= 20
                ? new UiRect(bounds.left() + 4, bounds.top() + 10, bounds.left() + 20, bounds.top() + 26)
                : new UiRect(bounds.left(), bounds.top(), bounds.left(), bounds.top());
        return new Layout(bounds,
                icon,
                new UiRect(textLeft, bounds.top() + 4, textRight, bounds.top() + 14),
                new UiRect(textLeft, bounds.top() + 18, textRight, bounds.top() + 28),
                new UiRect(actionLeft, bounds.top() + 9, bounds.right(), bounds.top() + 27));
    }

    public static void render(GuiGraphics graphics, Font font, Layout layout, Content content) {
        UiRect bounds = layout.bounds();
        graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), 0xA02A323E);
        if (content.icon() != null && layout.icon().width() > 0) {
            content.icon().render(graphics, font, layout.icon(), 0xFFFFFFFF);
        }
        UiRect title = layout.title();
        if (title.width() > 0) {
            float scale = EditorTextLayout.fittedScale(font.width(content.title()), title.width(), 0.75F);
            int available = Math.max(1, (int) Math.floor(title.width() / scale));
            Component visible = font.width(content.title()) <= available ? content.title()
                    : Component.literal(font.plainSubstrByWidth(content.title().getString(), available));
            graphics.pose().pushPose();
            graphics.pose().translate(title.left(), title.top(), 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, visible, 0, 0, 0xFFFFFFFF, false);
            graphics.pose().popPose();
        }
        UiRect summary = layout.summary();
        if (summary.width() > 0) graphics.drawString(font,
                Component.literal(font.plainSubstrByWidth(content.summary().getString(), summary.width())),
                summary.left(), summary.top(), content.summaryColor(), false);
    }
}
