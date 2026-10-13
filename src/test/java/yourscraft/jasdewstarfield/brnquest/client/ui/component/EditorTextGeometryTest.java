package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Layout and picking must agree even with fractional advances, soft wrapping and supplementary glyphs. */
class EditorTextGeometryTest {
    @Test void fractionalGlyphsShareCaretAndHitCoordinates() {
        var geometry=new EditorTextGeometry("A😀B",100,cp->1.25);
        assertEquals(2.5,geometry.distance(0,3));
        assertEquals(1,geometry.hit(0,1.3)); assertEquals(3,geometry.hit(0,2.4));
    }
    @Test void explicitNewlineAndSoftWrapHaveDifferentCaretRows() {
        var explicit=new EditorTextGeometry("ab\ncd\n",100,cp->1);
        assertEquals(0,explicit.row(2)); assertEquals(1,explicit.row(3)); assertEquals(2,explicit.row(6));
        var wrapped=new EditorTextGeometry("abcdef",3,cp->1);
        assertEquals(2,wrapped.lines().size()); assertEquals(1,wrapped.row(3));
        assertEquals(6,wrapped.hit(1,100));
    }
    @Test void wrappingPreservesEverySourceCharacter() {
        String text="alpha beta 中文 😀 tail";
        var geometry=new EditorTextGeometry(text,6,cp->1);
        assertEquals(text,geometry.lines().stream().map(row->text.substring(row.start(),row.end())).reduce("",String::concat));
        assertTrue(geometry.lines().stream().allMatch(row->TextEditBuffer.boundary(text,row.start())==row.start()
                && TextEditBuffer.boundary(text,row.end())==row.end()));
    }
    @Test void explicitWrapAffinityOnlyChangesTheCurrentBoundaryAndFallsBackWhenInvalid() {
        var geometry=new EditorTextGeometry("abcdef",3,cp->1);
        assertEquals(1,geometry.row(3)); assertEquals(0,geometry.row(3,0)); assertEquals(1,geometry.row(3,1));
        assertEquals(1,geometry.row(4,0)); assertEquals(1,geometry.row(4,99));
    }
}
