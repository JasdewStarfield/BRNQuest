package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import static org.junit.jupiter.api.Assertions.*;

class NativeTextTest {
    @Test void wrappingMeasuresBoldAndFractionalGlyphWidthsAndRetainsStyles() {
        var splitter=new net.minecraft.client.StringSplitter((cp,style)->style.isBold()?1.5F:1.0F);
        var font=new net.minecraft.client.gui.Font(id->null,false) {
            @Override public net.minecraft.client.StringSplitter getSplitter() { return splitter; }
            @Override public int width(net.minecraft.util.FormattedCharSequence sequence) {
                return (int)Math.ceil(splitter.stringWidth(sequence));
            }
        };
        var lines=NativeText.split(font,NativeText.component("§6§lABCD§rEF"),3);
        assertEquals(3,lines.size());
        assertEquals(3,font.width(lines.getFirst()));
        StringBuilder content=new StringBuilder();
        lines.forEach(line->line.accept((index,style,cp)->{ content.appendCodePoint(cp); return true; }));
        assertEquals("ABCDEF",content.toString());
        lines.getFirst().accept((index,style,cp)->{ assertTrue(style.isBold()); assertEquals(0xFFAA00,style.getColor().getValue()); return true; });
        lines.getLast().accept((index,style,cp)->{ assertFalse(style.isBold()); return true; });
    }
    @Test void visibleSequenceKeepsUnknownAndIncompleteCodesLiteral() {
        StringBuilder visible=new StringBuilder();
        var styles=new ArrayList<Style>();
        NativeText.sequence(NativeText.component("§6§LTitle§r §x and §")).accept((index,style,cp)-> {
            visible.appendCodePoint(cp); styles.add(style); return true;
        });
        assertEquals("Title §x and §",visible.toString());
        assertTrue(styles.getFirst().isBold());
        assertEquals(0xFFAA00,styles.getFirst().getColor().getValue());
        assertFalse(styles.getLast().isBold());
        assertNull(styles.getLast().getColor());
    }
    @Test void titleStyleNeverLeaksIntoAnAppendedIdOrStatus() {
        var component=NativeText.component("§c§lTitle").append(Component.literal(" [test:id]"));
        var styles=new ArrayList<Style>();
        NativeText.sequence(component).accept((index,style,cp)->{ styles.add(style); return true; });
        assertTrue(styles.getFirst().isBold());
        assertFalse(styles.getLast().isBold());
        assertNull(styles.getLast().getColor());
        component.append("changed");
        assertEquals("Title",NativeText.component("§c§lTitle").getString());
    }
    @Test void slicedSequenceRetainsDecorationsAndSafeUnicodeBoundaries() {
        StringBuilder result=new StringBuilder();
        NativeText.slice(NativeText.component("§nA😀B"),1,3).accept((index,style,cp)-> {
            assertTrue(style.isUnderlined()); result.appendCodePoint(cp); return true;
        });
        assertEquals("😀",result.toString());
    }
}
