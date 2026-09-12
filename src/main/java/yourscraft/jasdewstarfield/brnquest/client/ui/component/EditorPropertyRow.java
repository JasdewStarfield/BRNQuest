package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Composes a label/error indicator with a native field or caller-supplied control. */
public final class EditorPropertyRow {
    private EditorPropertyRow() {}

    public static void label(GuiGraphics graphics, Font font, Component label, UiRect bounds, String issue) {
        boolean invalid = issue != null && !issue.isBlank();
        String text = (invalid ? "! " : "") + label.getString();
        graphics.drawString(font, Component.literal(font.plainSubstrByWidth(text, Math.max(0, bounds.width() - 4))),
                bounds.left(), bounds.top() + 5, invalid ? 0xFFFF7070 : GraystonePalette.SECONDARY, false);
    }

    public static void text(GuiGraphics graphics, Font font, EditorPropertyFormLayout.Row row,
                            Component label, String issue, EditorTextField input, boolean enabled) {
        label(graphics, font, label, row.label(), issue);
        input.show(row.field(), enabled);
    }

    public static void readOnly(GuiGraphics graphics, Font font, EditorPropertyFormLayout.Row row,
                                Component label, Component value) {
        label(graphics, font, label, row.label(), null);
        graphics.drawString(font, Component.literal(font.plainSubstrByWidth(value.getString(), row.field().width())),
                row.field().left(), row.field().top() + 5, 0xFFFFFFFF, false);
    }
}
