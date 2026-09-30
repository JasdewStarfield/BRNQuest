package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import java.util.function.ToIntFunction;

/** Pure text fitting helpers shared by compact editor rows. */
public final class EditorTextLayout {
    private EditorTextLayout() {}

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
