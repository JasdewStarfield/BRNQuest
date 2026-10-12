package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.text.MinecraftStyledText;
import static org.junit.jupiter.api.Assertions.*;

/** Selection commands must preserve both authored content and the style after the selection. */
class NativeSourceEditsTest {
    @Test void restoresTheColorAndDecorationsAtTheEndOfASelection() {
        String original="§6§oBefore selected after";
        int start=original.indexOf("selected"), end=start+8;
        var result=NativeSourceEdits.apply(original,start,end,'l');
        assertEquals(MinecraftStyledText.parse(original).visibleText(), MinecraftStyledText.parse(result.text()).visibleText());
        assertEquals(NativeSourceEdits.styleAt(original,original.length()), NativeSourceEdits.styleAt(result.text(),result.text().length()));
        assertTrue(MinecraftStyledText.parse(result.text()).runs().stream().anyMatch(r -> r.text().equals("selected") && r.style().bold()));
        assertEquals("selected",result.text().substring(result.selectionStart(),result.selectionEnd()));
    }
    @Test void restoresStylesEstablishedInsideTheSelection() {
        String original="§eone §ctwo three";
        var result=NativeSourceEdits.apply(original,2,original.indexOf(" three"),'n');
        assertEquals(NativeSourceEdits.styleAt(original,original.length()),NativeSourceEdits.styleAt(result.text(),result.text().length()));
    }
    @Test void insertsAtAnEmptySelectionAndRetainsUnknownTokens() {
        var result=NativeSourceEdits.apply("A§xB",1,1,'l');
        assertEquals("A§l§xB",result.text());
        assertEquals(3,result.selectionStart());
        assertEquals(3,result.selectionEnd());
        assertEquals("A§xB",MinecraftStyledText.parse(result.text()).visibleText());
    }
    @Test void neverSplitsSurrogatesOrKnownFormatCodes() {
        var unicode=NativeSourceEdits.apply("A😀B",2,3,'o');
        assertEquals("😀",unicode.text().substring(unicode.selectionStart(),unicode.selectionEnd()));
        var token=NativeSourceEdits.apply("§6AB",1,1,'l');
        assertEquals("§l§6AB",token.text());
    }
}
