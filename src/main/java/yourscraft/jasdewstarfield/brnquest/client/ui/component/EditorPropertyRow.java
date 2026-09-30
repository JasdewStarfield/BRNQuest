package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Composes a label/error indicator with a native field or caller-supplied control. */
public final class EditorPropertyRow {
    private EditorPropertyRow() {}

    public static void label(GuiGraphics graphics, Font font, Component label, UiRect bounds, String issue) {
        boolean invalid = issue != null && !issue.isBlank();
        Component text = invalid ? Component.literal("! ").append(label) : label;
        int available = Math.max(1, bounds.width() - 4);
        var lines = font.split(text, available);
        int count = Math.min(2, lines.size());
        int top = bounds.top() + (count == 1 ? 5 : Math.max(0, (bounds.height() - count * font.lineHeight) / 2));
        // Two normal-size lines fit the existing 18px row and preserve field/hit geometry.
        TextLayoutDebug.pushSlot(graphics, bounds, "property_label");
        try {
            for (int index = 0; index < count; index++) {
                var line = lines.get(index);
                if (index == 1 && lines.size() > 2) {
                    String rest = lines.subList(1, lines.size()).stream().map(TextLayoutDebug::plain)
                            .collect(java.util.stream.Collectors.joining(" "));
                    line = Component.literal(EditorTextLayout.ellipsize(rest, available, font::width, false))
                            .withStyle(label.getStyle()).getVisualOrderText();
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
