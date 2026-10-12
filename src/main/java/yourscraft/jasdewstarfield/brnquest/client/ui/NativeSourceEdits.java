package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.data.text.MinecraftLegacyTextParser;
import yourscraft.jasdewstarfield.brnquest.data.text.MinecraftTextColor;
import yourscraft.jasdewstarfield.brnquest.data.text.RichDocument;

/** Native selection formatting restores the exact downstream style instead of resetting it to white. */
final class NativeSourceEdits {
    private NativeSourceEdits() {}

    static MarkdownSourceEdits.Result apply(String source, int cursor, int anchor, char code) {
        int start = boundary(source, Math.min(cursor, anchor));
        int end = boundary(source, Math.max(cursor, anchor));
        String prefix = "§" + code;
        if (start == end) return new MarkdownSourceEdits.Result(source.substring(0, start) + prefix
                + source.substring(end), start + 2, start + 2);
        String restore = encode(styleAt(source, end));
        return new MarkdownSourceEdits.Result(source.substring(0, start) + prefix + source.substring(start, end)
                + restore + source.substring(end), start + 2, end + 2);
    }

    static RichDocument.InlineStyle styleAt(String source, int end) {
        var style = RichDocument.InlineStyle.EMPTY;
        for (int index = 0; index + 1 < end; index++) {
            if (source.charAt(index) != '§') continue;
            var next = MinecraftLegacyTextParser.apply(source.charAt(index + 1), style);
            if (next != null) { style = next; index++; }
        }
        return style;
    }

    private static String encode(RichDocument.InlineStyle style) {
        StringBuilder codes = new StringBuilder("§r");
        for (MinecraftTextColor color : MinecraftTextColor.values())
            if (Integer.valueOf(color.rgb()).equals(style.color())) codes.append('§').append(color.legacyCode());
        if (style.obfuscated()) codes.append("§k");
        if (style.bold()) codes.append("§l");
        if (style.strikethrough()) codes.append("§m");
        if (style.underlined()) codes.append("§n");
        if (style.italic()) codes.append("§o");
        return codes.toString();
    }

    private static int boundary(String text, int requested) {
        int offset = Math.clamp(requested, 0, text.length());
        if (offset > 0 && offset < text.length() && Character.isLowSurrogate(text.charAt(offset))
                && Character.isHighSurrogate(text.charAt(offset - 1))) offset--;
        // A style operation cannot cut a recognized two-character formatting token in half.
        if (offset > 0 && offset < text.length() && text.charAt(offset - 1) == '§'
                && MinecraftLegacyTextParser.apply(text.charAt(offset), RichDocument.InlineStyle.EMPTY) != null) offset--;
        return offset;
    }
}
