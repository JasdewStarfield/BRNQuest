package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Cover space-free identifiers, hard line breaks and styled glyphs supplied by direct tooltip callers. */
class TooltipTextLayoutTest {
    private static String plain(FormattedCharSequence line) {
        var text = new StringBuilder();
        line.accept((index, style, codePoint) -> { text.appendCodePoint(codePoint); return true; });
        return text.toString();
    }
    private static List<FormattedCharSequence> wrap(String text, int limit) {
        return TooltipTextLayout.wrap(FormattedCharSequence.forward(text, Style.EMPTY), limit, glyph -> 1);
    }
    @Test void spaceFreeIdsWrapWithoutDroppingCharacters() {
        String id = "minecraft:recipes/food/cooked_beef";
        var lines = wrap(id, 8);
        assertTrue(lines.size() > 1);
        assertEquals(id, lines.stream().map(TooltipTextLayoutTest::plain).collect(java.util.stream.Collectors.joining()));
        assertTrue(lines.stream().allMatch(line -> plain(line).length() <= 8));
    }
    @Test void englishWordsUseWhitespaceBoundaries() {
        assertEquals(List.of("Right-click", "to clear", "the item"), wrap("Right-click to clear the item", 11)
                .stream().map(TooltipTextLayoutTest::plain).toList());
    }
    @Test void explicitAndTrailingBlankLinesRemainPresent() {
        assertEquals(List.of("one", "", "last", ""), wrap("one\n\nlast\n", 20)
                .stream().map(TooltipTextLayoutTest::plain).toList());
        assertEquals(List.of(""), wrap("", 20).stream().map(TooltipTextLayoutTest::plain).toList());
    }
    @Test void colorsAndSupplementaryGlyphsSurviveWrapping() {
        Style red = Style.EMPTY.withColor(0xFF0000), blue = Style.EMPTY.withColor(0x0000FF).withBold(true);
        FormattedCharSequence styled = sink -> sink.accept(0, red, 'A')
                && sink.accept(1, blue, 0x1F600) && sink.accept(3, red, 'B');
        var lines = TooltipTextLayout.wrap(styled, 2, glyph -> glyph.codePoint() == 0x1F600 ? 2 : 1);
        assertEquals(List.of("A", "😀", "B"), lines.stream().map(TooltipTextLayoutTest::plain).toList());
        lines.get(1).accept((index, style, cp) -> { assertEquals(blue, style); return true; });
    }
    @Test void fractionalFontAdvancesAreNotRoundedPerGlyph() {
        var lines = TooltipTextLayout.wrap(FormattedCharSequence.forward("abcdef", Style.EMPTY), 3, glyph -> 0.5);
        assertEquals(List.of("abcdef"), lines.stream().map(TooltipTextLayoutTest::plain).toList());
    }
    @Test void maximumWidthLeavesScreenPaddingEvenAtHighGuiScale() {
        assertEquals(320, TooltipTextLayout.maximumWidth(640));
        assertEquals(176, TooltipTextLayout.maximumWidth(200));
        assertEquals(1, TooltipTextLayout.maximumWidth(20));
    }
}
