package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Covers language boundaries that vanilla space-only wrapping handles poorly. */
class MixedTextLayoutTest {
    @Test void keepsChineseLatinSpacingWithoutCreatingASeparateBreak() {
        assertEquals("任务\u00A0BRNQuest 1\u00A0目标",
                MixedTextLayout.normalizeInlineSpacing("任务 BRNQuest 1 目标"));
    }

    @Test void closingAndOpeningPunctuationStayWithTheirText() {
        List<String> closing = wrap("甲乙，丙丁", 2);
        assertTrue(closing.stream().noneMatch(line -> line.startsWith("，")));

        List<String> opening = wrap("甲（乙丙", 2);
        assertTrue(opening.stream().noneMatch(line -> line.endsWith("（")));
    }

    @Test void technicalIdentifiersPreferSeparatorsAndStillForceLongSegments() {
        List<String> identifier = wrap("brnquest:very_long_identifier", 10);
        assertEquals("brnquest:", identifier.getFirst());
        assertTrue(identifier.stream().allMatch(line -> codePoints(line) <= 10));

        assertEquals(List.of("ABCD", "EFGH", "IJK"), wrap("ABCDEFGHIJK", 4));
    }

    @Test void explicitNewlinesAndNonBreakingSpacesRemainIntentional() {
        assertEquals(List.of("甲", "", "乙"), wrap("甲\n\n乙", 4));
        assertEquals(List.of(""), wrap("   ", 4));
        assertEquals(List.of("任", "务\u00A0A"), wrap("任务 A", 2));
    }

    private static List<String> wrap(String text, int width) {
        return MixedTextLayout.wrap(text, width, MixedTextLayoutTest::codePoints);
    }

    private static int codePoints(String text) {
        return text.codePointCount(0, text.length());
    }
}
