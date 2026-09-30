package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Wrap already ordered tooltip glyphs without losing colors, custom fonts, or bidirectional ordering. */
public final class TooltipTextLayout {
    public record Glyph(Style style, int codePoint) {}
    private TooltipTextLayout() {}

    public static int maximumWidth(int screenWidth) { return Math.max(1, Math.min(320, screenWidth - 24)); }

    /** Prefer spaces and identifier separators; long uninterrupted IDs still break at glyph boundaries. */
    public static List<FormattedCharSequence> wrap(FormattedCharSequence text, int maximumWidth,
                                                   ToDoubleFunction<Glyph> advance) {
        var glyphs = new ArrayList<Glyph>();
        text.accept((index, style, codePoint) -> { glyphs.add(new Glyph(style, codePoint)); return true; });
        var result = new ArrayList<FormattedCharSequence>();
        int paragraph = 0;
        for (int index = 0; index <= glyphs.size(); index++) {
            if (index == glyphs.size() || glyphs.get(index).codePoint() == '\n') {
                wrapParagraph(glyphs, paragraph, index, Math.max(1, maximumWidth), advance, result);
                paragraph = index + 1;
            }
        }
        return List.copyOf(result);
    }

    private static void wrapParagraph(List<Glyph> glyphs, int start, int end, int limit,
                                       ToDoubleFunction<Glyph> advance, List<FormattedCharSequence> output) {
        if (start == end) { output.add(sequence(List.of())); return; }
        while (start < end) {
            int cursor = start, boundary = -1;
            double width = 0;
            while (cursor < end && (cursor == start || width + advance.applyAsDouble(glyphs.get(cursor)) <= limit)) {
                Glyph glyph = glyphs.get(cursor++);
                width += advance.applyAsDouble(glyph);
                if (glyph.codePoint() == ' ' || ":/._-".indexOf(glyph.codePoint()) >= 0) boundary = cursor;
            }
            // A following space already ends the fitting word; do not fall back to a separator inside it.
            boolean wordFits = cursor < end && glyphs.get(cursor).codePoint() == ' ';
            int next = cursor < end && !wordFits && boundary > start ? boundary : cursor;
            int visibleEnd = next;
            while (visibleEnd > start && glyphs.get(visibleEnd - 1).codePoint() == ' ') visibleEnd--;
            output.add(sequence(List.copyOf(glyphs.subList(start, visibleEnd))));
            start = next;
            while (start < end && glyphs.get(start).codePoint() == ' ') start++;
        }
    }

    private static FormattedCharSequence sequence(List<Glyph> glyphs) {
        return sink -> {
            int offset = 0;
            for (Glyph glyph : glyphs) {
                if (!sink.accept(offset, glyph.style(), glyph.codePoint())) return false;
                offset += Character.charCount(glyph.codePoint());
            }
            return true;
        };
    }
}
