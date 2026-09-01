package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Wraps mixed Chinese/Latin UI text with punctuation-aware and identifier-aware boundaries.
 */
public final class MixedTextLayout {
    private static final int NON_BREAKING_SPACE = 0x00A0;
    private static final String OPENING_PUNCTUATION = "（《【「『〔〈([{“‘";
    private static final String CLOSING_PUNCTUATION = "，。！？；：、）》】」』〕〉)]}”’…,.!?;:%％";
    private static final String TECHNICAL_SEPARATORS = ":/\\._-#@=+";

    private MixedTextLayout() {}

    /** Returns render-ready lines while retaining the root style of the supplied component. */
    public static List<FormattedCharSequence> split(Font font, Component text, int maximumWidth) {
        List<String> wrapped = wrap(text == null ? "" : text.getString(), maximumWidth, font::width);
        return wrapped.stream()
                .map(line -> Component.literal(line).withStyle(text == null ? net.minecraft.network.chat.Style.EMPTY : text.getStyle())
                        .getVisualOrderText())
                .toList();
    }

    /**
     * BRNTalk keeps a single space at a Chinese/Latin boundary visually present but unbreakable.
     */
    static String normalizeInlineSpacing(String source) {
        if (source == null || source.isEmpty()) return "";
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder result = new StringBuilder(normalized.length());
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            int nextOffset = offset + Character.charCount(codePoint);
            if (codePoint == ' ' && offset > 0 && nextOffset < normalized.length()) {
                int before = normalized.codePointBefore(offset);
                int after = normalized.codePointAt(nextOffset);
                if (isHan(before) && isLatinOrDigit(after) || isLatinOrDigit(before) && isHan(after)) {
                    result.appendCodePoint(NON_BREAKING_SPACE);
                    offset = nextOffset;
                    continue;
                }
            }
            result.appendCodePoint(codePoint);
            offset = nextOffset;
        }
        return result.toString();
    }

    /** Pure wrapping core kept independent from Screen state so geometry can be regression tested. */
    static List<String> wrap(String source, int maximumWidth, ToIntFunction<String> width) {
        String normalized = normalizeInlineSpacing(source);
        int safeWidth = Math.max(1, maximumWidth);
        List<String> result = new ArrayList<>();
        String[] paragraphs = normalized.split("\n", -1);
        for (String paragraph : paragraphs) wrapParagraph(paragraph, safeWidth, width, result);
        return List.copyOf(result);
    }

    private static void wrapParagraph(String text, int maximumWidth, ToIntFunction<String> width,
                                      List<String> output) {
        if (text.isEmpty()) {
            output.add("");
            return;
        }
        int lineStart = skipBreakableSpaces(text, 0);
        if (lineStart >= text.length()) {
            output.add("");
            return;
        }
        while (lineStart < text.length()) {
            int cursor = lineStart;
            int lastPreferredBreak = -1;
            boolean emitted = false;
            while (cursor < text.length()) {
                int next = text.offsetByCodePoints(cursor, 1);
                String candidate = text.substring(lineStart, next);
                if (width.applyAsInt(candidate) <= maximumWidth || cursor == lineStart) {
                    cursor = next;
                    if (preferredBoundary(text, cursor)) lastPreferredBreak = cursor;
                    continue;
                }

                int breakAt = lastPreferredBreak > lineStart ? lastPreferredBreak
                        : legalBoundary(text, cursor) ? cursor : -1;
                if (breakAt < 0) {
                    // Keep paired punctuation together even when it hangs a few pixels past the limit.
                    cursor = next;
                    if (preferredBoundary(text, cursor)) lastPreferredBreak = cursor;
                    continue;
                }
                appendLine(text, lineStart, breakAt, output);
                lineStart = skipBreakableSpaces(text, breakAt);
                emitted = true;
                break;
            }
            if (!emitted) {
                appendLine(text, lineStart, text.length(), output);
                return;
            }
        }
    }

    private static void appendLine(String text, int start, int end, List<String> output) {
        int trimmedEnd = end;
        while (trimmedEnd > start) {
            int codePoint = text.codePointBefore(trimmedEnd);
            if (!breakableSpace(codePoint)) break;
            trimmedEnd -= Character.charCount(codePoint);
        }
        if (trimmedEnd > start) output.add(text.substring(start, trimmedEnd));
    }

    private static int skipBreakableSpaces(String text, int offset) {
        int cursor = offset;
        while (cursor < text.length()) {
            int codePoint = text.codePointAt(cursor);
            if (!breakableSpace(codePoint)) break;
            cursor += Character.charCount(codePoint);
        }
        return cursor;
    }

    private static boolean preferredBoundary(String text, int offset) {
        if (!legalBoundary(text, offset) || offset <= 0 || offset >= text.length()) return false;
        int before = text.codePointBefore(offset);
        int after = text.codePointAt(offset);
        return breakableSpace(before) || contains(TECHNICAL_SEPARATORS, before)
                || isHan(before) || isHan(after)
                || contains(CLOSING_PUNCTUATION, before) || contains(OPENING_PUNCTUATION, after);
    }

    private static boolean legalBoundary(String text, int offset) {
        if (offset <= 0 || offset >= text.length()) return true;
        int before = text.codePointBefore(offset);
        int after = text.codePointAt(offset);
        if (before == NON_BREAKING_SPACE || after == NON_BREAKING_SPACE) return false;
        return !contains(OPENING_PUNCTUATION, before) && !contains(CLOSING_PUNCTUATION, after);
    }

    private static boolean breakableSpace(int codePoint) {
        return codePoint != NON_BREAKING_SPACE && Character.isWhitespace(codePoint);
    }

    private static boolean isHan(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
    }

    private static boolean isLatinOrDigit(int codePoint) {
        return Character.isDigit(codePoint)
                || Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN;
    }

    private static boolean contains(String characters, int codePoint) {
        return characters.indexOf(codePoint) >= 0;
    }
}
