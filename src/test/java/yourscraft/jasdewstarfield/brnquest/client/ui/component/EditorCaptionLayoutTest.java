package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Captions retain native styling, advertise omissions and never split a supplementary Unicode character. */
class EditorCaptionLayoutTest {
    private static double width(FormattedCharSequence text) {
        double[] width = {0};
        text.accept((index, style, cp) -> { width[0] += cp == '…' ? 2 : style.isBold() ? 2 : 1; return true; });
        return width[0];
    }
    private static String text(FormattedCharSequence sequence) {
        var text = new StringBuilder();
        sequence.accept((index, style, cp) -> { text.appendCodePoint(cp); return true; });
        return text.toString();
    }
    @Test void longHintKeepsItsMarkerInsideTheSlot() {
        var source = FormattedCharSequence.forward("Click inventory items or drag them", Style.EMPTY);
        var visible = EditorTextLayout.ellipsizeSequence(source, 10, EditorCaptionLayoutTest::width, Style.EMPTY);
        assertEquals("Click in…", text(visible));
        assertEquals(10, width(visible));
        assertEquals("…",text(EditorTextLayout.ellipsizeSequence(source,2,EditorCaptionLayoutTest::width,Style.EMPTY)));
        assertEquals("",text(EditorTextLayout.ellipsizeSequence(source,1,EditorCaptionLayoutTest::width,Style.EMPTY)));
    }
    @Test void styledPrefixAndUnicodeGlyphsRemainIntact() {
        var bold = Style.EMPTY.withBold(true).withColor(0x80D49B);
        var source = FormattedCharSequence.composite(FormattedCharSequence.forward("甲😀",bold),
                FormattedCharSequence.forward("abcd",Style.EMPTY));
        var visible = EditorTextLayout.ellipsizeSequence(source,6,EditorCaptionLayoutTest::width,Style.EMPTY);
        assertEquals("甲😀…",text(visible));
        visible.accept((index, style, cp) -> { if(cp != '…') assertEquals(bold,style); return true; });
        assertEquals(6,width(visible));
    }
    @Test void fittingCaptionKeepsTheOriginalStyledSequence() {
        var source = Component.literal("done").withStyle(style->style.withItalic(true)).getVisualOrderText();
        assertSame(source,EditorTextLayout.ellipsizeSequence(source,10,EditorCaptionLayoutTest::width,Style.EMPTY));
    }
}
