package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import java.util.function.ToIntFunction;

/** Pure text fitting helpers shared by compact editor rows. */
public final class EditorTextLayout {
    private EditorTextLayout() {}

    /** Every text line needs its normal height plus two pixels above and below the complete block. */
    public static int buttonHeight(int lines, int lineHeight) {
        return Math.max(EditorPropertyFormLayout.FIELD_HEIGHT, lines * lineHeight + 4);
    }

    public static float fittedScale(int measuredWidth, int availableWidth, float minimumScale) {
        if (measuredWidth <= 0 || availableWidth >= measuredWidth) return 1.0F;
        float safeMinimum = Math.max(0.1F, Math.min(1.0F, minimumScale));
        return Math.max(safeMinimum, availableWidth / (float) measuredWidth);
    }

    /** Normal-size labels advertise omission; identifiers keep both their context and distinguishing suffix. */
    public static String ellipsize(String text, int availableWidth, ToIntFunction<String> width, boolean middle) {
        if (width.applyAsInt(text) <= availableWidth) return text;
        String marker = "…";
        if (availableWidth < width.applyAsInt(marker)) return "";
        int budget = availableWidth - width.applyAsInt(marker);
        if (!middle) return prefix(text, budget, width) + marker;
        String head = prefix(text, budget * 2 / 5, width);
        String tail = suffix(text, budget - width.applyAsInt(head), width);
        return head + marker + tail;
    }

    /** Styled captions preserve whole code points and glyph styles while reserving the omission marker's width. */
    public static net.minecraft.util.FormattedCharSequence ellipsizeSequence(
            net.minecraft.util.FormattedCharSequence text, int availableWidth,
            java.util.function.ToDoubleFunction<net.minecraft.util.FormattedCharSequence> width,
            net.minecraft.network.chat.Style markerStyle) {
        if (width.applyAsDouble(text) <= availableWidth) return text;
        var marker = net.minecraft.util.FormattedCharSequence.forward("…", markerStyle);
        double markerWidth = width.applyAsDouble(marker);
        if (availableWidth < markerWidth) return net.minecraft.util.FormattedCharSequence.EMPTY;
        double budget = availableWidth - markerWidth;
        double[] used = {0};
        var glyphs = new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
        text.accept((index, style, codepoint) -> {
            var glyph = net.minecraft.util.FormattedCharSequence.codepoint(codepoint, style);
            double next = used[0] + width.applyAsDouble(glyph);
            if (next > budget) return false;
            used[0] = next;
            glyphs.add(glyph);
            return true;
        });
        glyphs.add(marker);
        return net.minecraft.util.FormattedCharSequence.composite(glyphs);
    }

    /** Only explicit namespaced values use middle omission; ordinary action labels retain their beginning. */
    public static boolean identifier(String text) {
        return text.matches("#?[a-z0-9_.-]+:[a-z0-9_./-]+");
    }

    private static String prefix(String text, int budget, ToIntFunction<String> width) {
        int low = 0, high = text.codePointCount(0, text.length());
        while (low < high) {
            int count = (low + high + 1) / 2;
            if (width.applyAsInt(text.substring(0, text.offsetByCodePoints(0, count))) <= budget) low = count;
            else high = count - 1;
        }
        return text.substring(0, text.offsetByCodePoints(0, low));
    }

    private static String suffix(String text, int budget, ToIntFunction<String> width) {
        int low = 0, high = text.codePointCount(0, text.length());
        while (low < high) {
            int count = (low + high + 1) / 2;
            if (width.applyAsInt(text.substring(text.offsetByCodePoints(text.length(), -count))) <= budget) low = count;
            else high = count - 1;
        }
        return text.substring(text.offsetByCodePoints(text.length(), -low));
    }
}
