package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Literal source geometry: section signs are ordinary visible characters in the editor. */
public final class RawTextLayout {
    public record Line(int start, int end) {}
    private RawTextLayout() {}
    public static int width(Font font, String text) { return font.width(FormattedCharSequence.forward(text, Style.EMPTY)); }
    public static String filter(String source, boolean multiline) {
        StringBuilder result = new StringBuilder();
        source.codePoints().filter(code -> code >= 32 && code != 127
                || multiline && code == '\n').forEach(result::appendCodePoint);
        return result.toString();
    }
    public static List<Line> lines(String source, int width, ToIntFunction<String> measure) {
        List<Line> lines = new ArrayList<>();
        int start = 0, offset = 0, preferred = -1;
        while (offset < source.length()) {
            if (source.charAt(offset) == '\n') {
                lines.add(new Line(start, offset)); start = ++offset; preferred = -1; continue;
            }
            int next = source.offsetByCodePoints(offset, 1);
            if (offset > start && measure.applyAsInt(source.substring(start, next)) > Math.max(1, width)) {
                int end = preferred > start ? preferred : offset;
                lines.add(new Line(start, end)); start = offset = end; preferred = -1; continue;
            }
            offset = next;
            if (MixedTextLayout.preferredBoundary(source, offset)) preferred = offset;
        }
        lines.add(new Line(start, source.length()));
        return List.copyOf(lines);
    }
}
