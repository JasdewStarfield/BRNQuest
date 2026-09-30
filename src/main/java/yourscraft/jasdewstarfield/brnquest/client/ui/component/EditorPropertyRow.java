package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Composes a label/error indicator with a native field or caller-supplied control. */
public final class EditorPropertyRow {
    private EditorPropertyRow() {}

    /** Include the validation marker in wrapping because it occupies the same label slot. */
    public static int height(Font font, Component label, int width, String issue) {
        Component text = issue != null && !issue.isBlank() ? Component.literal("! ").append(label) : label;
        return Math.max(EditorPropertyFormLayout.FIELD_HEIGHT,
                font.split(text, Math.max(1, width - 4)).size() * font.lineHeight);
    }

    /** Keep a full action name by moving its button below the label before considering text wrapping. */
    public static EditorPropertyFormLayout.Row action(Font font, Component label, Component action,
                                                       int left, int top, int width, int labelWidth) {
        if (font.width(action) + 8 <= width - labelWidth)
            return EditorPropertyFormLayout.row(left, top, width, labelWidth, height(font, label, labelWidth, null));
        int labelHeight = Math.max(font.lineHeight, font.split(label, Math.max(1, width - 4)).size() * font.lineHeight);
        int buttonHeight = Math.max(18, font.split(action, Math.max(1, width - 8)).size() * font.lineHeight + 4);
        return EditorPropertyFormLayout.stacked(left, top, width, labelHeight, buttonHeight);
    }

    public static void label(GuiGraphics graphics, Font font, Component label, UiRect bounds, String issue) {
        boolean invalid = issue != null && !issue.isBlank();
        Component text = invalid ? Component.literal("! ").append(label) : label;
        int available = Math.max(1, bounds.width() - 4);
        var lines = font.split(text, available);
        int count = Math.min(Math.max(1, bounds.height() / font.lineHeight), lines.size());
        int top = bounds.top() + (count == 1 ? Math.min(5, Math.max(0, bounds.height() - font.lineHeight))
                : Math.max(0, (bounds.height() - count * font.lineHeight) / 2));
        // Measured property rows grow to show every label line at the normal font size.
        TextLayoutDebug.pushSlot(graphics, bounds, "property_label");
        try {
            for (int index = 0; index < count; index++) {
                var line = lines.get(index);
                if (index == count - 1 && lines.size() > count) {
                    String rest = lines.subList(index, lines.size()).stream().map(TextLayoutDebug::plain)
                            .collect(java.util.stream.Collectors.joining(" "));
                    Component full = Component.literal(rest).withStyle(label.getStyle());
                    Component visible = Component.literal(EditorTextLayout.ellipsize(rest, available, font::width, false))
                            .withStyle(label.getStyle());
                    // Legacy fixed rows must still report omission instead of measuring the shortened text as fitting.
                    TextLayoutDebug.fitted(graphics, font, full, visible, bounds, bounds.left(),
                            top + index * font.lineHeight, 1, "property_label");
                    TextLayoutDebug.mute();
                    try { graphics.drawString(font, visible, bounds.left(), top + index * font.lineHeight,
                            invalid ? 0xFFFF7070 : GraystonePalette.SECONDARY, false); }
                    finally { TextLayoutDebug.unmute(); }
                    continue;
                }
                graphics.drawString(font, line, bounds.left(), top + index * font.lineHeight,
                        invalid ? 0xFFFF7070 : GraystonePalette.SECONDARY, false);
            }
        } finally { TextLayoutDebug.popSlot(); }
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
