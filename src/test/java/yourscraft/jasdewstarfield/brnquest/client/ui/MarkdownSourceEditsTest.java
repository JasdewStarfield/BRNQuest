package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.text.RichDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkdownSourceEditsTest {
    @Test void wrapsASelectionAndKeepsItsContentsSelected() {
        var result = MarkdownSourceEdits.apply("before text after", 7, 11,
                MarkdownSourceEdits.Tool.BOLD, "bold");
        assertEquals("before **text** after", result.text());
        assertEquals("text", result.text().substring(result.selectionStart(), result.selectionEnd()));
    }

    @Test void insertsVisiblePlaceholderAtAnEmptyCursor() {
        var result = MarkdownSourceEdits.apply("a ", 2, 2,
                MarkdownSourceEdits.Tool.LINK, "link text");
        assertEquals("a [link text](https://example.com)", result.text());
        assertEquals("link text", result.text().substring(result.selectionStart(), result.selectionEnd()));
    }

    @Test void prefixesEverySelectedListLine() {
        var result = MarkdownSourceEdits.apply("one\ntwo\nthree", 4, 13,
                MarkdownSourceEdits.Tool.LIST, "item");
        assertEquals("one\n- two\n- three", result.text());
    }

    @Test void neverSplitsASurrogatePair() {
        var result = MarkdownSourceEdits.apply("A😀B", 2, 2,
                MarkdownSourceEdits.Tool.CODE, "code");
        assertEquals("A`code`😀B", result.text());
    }

    @Test void insertsContentAtTheSelectionAndSelectsAltText() {
        var result = MarkdownSourceEdits.insertContent("before after", 7, 12,
                RichDocument.ContentKind.TEXTURE, "brnquest:textures/panel.png", "texture");
        assertEquals("before ![after](texture:brnquest:textures/panel.png)", result.text());
        assertEquals("after", result.text().substring(result.selectionStart(), result.selectionEnd()));

        var middle = MarkdownSourceEdits.insertContent("one\ntwo", 4, 4,
                RichDocument.ContentKind.ITEM, "minecraft:stone", "item");
        assertEquals("one\n![item](item:minecraft:stone)two", middle.text());
    }

    @Test void wrapsMinecraftOnlyMarkdownStylesAndNormalizesRgbColors() {
        var underline = MarkdownSourceEdits.apply("text", 0, 4,
                MarkdownSourceEdits.Tool.UNDERLINE, "underlined");
        assertEquals("[text](brnquest:style/underline)", underline.text());
        assertEquals("text", underline.text().substring(underline.selectionStart(), underline.selectionEnd()));

        var color = MarkdownSourceEdits.applyColor("A😀B", 1, 3, "#FfAa00", "colored");
        assertEquals("A[😀](brnquest:style/color/ffaa00)B", color.text());
        assertEquals("😀", color.text().substring(color.selectionStart(), color.selectionEnd()));
    }

    @Test void insertsAStableQuestTargetAndKeepsTheVisibleLabelSelected() {
        var result = MarkdownSourceEdits.insertQuestLink("before next after", 7, 11,
                "example:chapter/target", "quest");

        assertEquals("before [next](brnquest:quest/example:chapter/target) after", result.text());
        assertEquals("next", result.text().substring(result.selectionStart(), result.selectionEnd()));
    }

    @Test void colorPickerAcceptsOnlyNamedPaletteOrOpaqueRgb() {
        assertEquals("dark_red", EditorMarkdownColorScreen.normalizeColor(" DARK_RED "));
        assertEquals("12abef", EditorMarkdownColorScreen.normalizeColor("#12AbEf"));
        assertEquals(null, EditorMarkdownColorScreen.normalizeColor("#abcd"));
        assertEquals(null, EditorMarkdownColorScreen.normalizeColor("transparent"));
    }
}
