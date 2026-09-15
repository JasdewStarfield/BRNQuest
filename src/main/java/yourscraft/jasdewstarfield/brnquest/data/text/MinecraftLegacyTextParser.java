package yourscraft.jasdewstarfield.brnquest.data.text;

import java.util.ArrayList;
import java.util.List;

/** Converts vanilla section-sign formatting into owned style spans before layout and measurement. */
final class MinecraftLegacyTextParser {
    private static final int[] COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
            0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
            0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private MinecraftLegacyTextParser() {}

    static RichDocument parse(String source) {
        String text = source == null ? "" : source;
        List<RichDocument.Inline> runs = new ArrayList<>();
        RichDocument.InlineStyle style = RichDocument.InlineStyle.EMPTY;
        StringBuilder pending = new StringBuilder();
        boolean recognized = false;

        for (int index = 0; index < text.length();) {
            char current = text.charAt(index);
            if (current == '\u00A7' && index + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(index + 1));
                RichDocument.InlineStyle changed = apply(code, style);
                if (changed != null) {
                    append(runs, pending, style);
                    style = changed;
                    recognized = true;
                    index += 2;
                    continue;
                }
            }
            pending.append(current);
            index++;
        }
        append(runs, pending, style);

        // Text without a valid code keeps the historical literal wrapping path exactly unchanged.
        if (!recognized) return new RichDocument(List.of(new RichDocument.LiteralBlock(text)), List.of());
        return new RichDocument(List.of(new RichDocument.FlowBlock(RichDocument.FlowKind.PARAGRAPH, runs)), List.of());
    }

    private static void append(List<RichDocument.Inline> output, StringBuilder pending,
                               RichDocument.InlineStyle style) {
        if (pending.isEmpty()) return;
        RichDocument.Text text = new RichDocument.Text(pending.toString());
        output.add(style.isEmpty() ? text : new RichDocument.StyleSpan(List.of(text), style));
        pending.setLength(0);
    }

    private static RichDocument.InlineStyle apply(char code, RichDocument.InlineStyle current) {
        int color = Character.digit(code, 16);
        if (color >= 0) {
            // Vanilla legacy colors clear all decorations before applying the new palette color.
            return new RichDocument.InlineStyle(COLORS[color], false, false, false, false, false);
        }
        return switch (code) {
            case 'k' -> new RichDocument.InlineStyle(current.color(), current.bold(), current.italic(),
                    current.underlined(), current.strikethrough(), true);
            case 'l' -> new RichDocument.InlineStyle(current.color(), true, current.italic(),
                    current.underlined(), current.strikethrough(), current.obfuscated());
            case 'm' -> new RichDocument.InlineStyle(current.color(), current.bold(), current.italic(),
                    current.underlined(), true, current.obfuscated());
            case 'n' -> new RichDocument.InlineStyle(current.color(), current.bold(), current.italic(),
                    true, current.strikethrough(), current.obfuscated());
            case 'o' -> new RichDocument.InlineStyle(current.color(), current.bold(), true,
                    current.underlined(), current.strikethrough(), current.obfuscated());
            case 'r' -> RichDocument.InlineStyle.EMPTY;
            default -> null;
        };
    }
}
