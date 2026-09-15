package yourscraft.jasdewstarfield.brnquest.compat.ftb.text;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FtbRichTextParserTest {
    private final FtbRichTextParser parser = new FtbRichTextParser();

    @Test void parsesPinnedLegacyFormattingResetAndEscapes() {
        var result = parse("plain &c&lred bold&r normal \\& literal &#12AbEfhex §nunder");

        assertEquals(List.of("plain ", "red bold", " normal & literal ", "hex ", "under"), result.nodes().stream()
                .map(FtbTextNode.Text.class::cast).map(FtbTextNode.Text::value).toList());
        var red = (FtbTextNode.Text) result.nodes().get(1);
        assertEquals(0xFF5555, red.style().color());
        assertTrue(red.style().bold());
        assertEquals(0x12ABEF, ((FtbTextNode.Text) result.nodes().get(3)).style().color());
        assertTrue(((FtbTextNode.Text) result.nodes().get(4)).style().underlined());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test void recognizesOnlyStandalonePageBreakAndSourceLine() {
        var result = parser.parse(List.of("before", "{@pagebreak}", "after {@pagebreak}"),
                "en_us.snbt", "quest.A.quest_desc", Map.of());

        assertInstanceOf(FtbTextNode.PageBreak.class, result.nodes().get(1));
        assertEquals(2, result.nodes().get(1).source().line());
        assertTrue(result.nodes().subList(2, result.nodes().size()).stream()
                .noneMatch(FtbTextNode.PageBreak.class::isInstance));
        assertEquals("{@pagebreak}", ((FtbTextNode.Unknown) result.nodes().getLast()).sourceText());
    }

    @Test void parsesImageAndSafeLegacyLinkSubstitutions() {
        var result = parse("{image:demo:textures/a.png width:175 height:90 align:right} "
                + "{open_url:https://example.com text:Safe%20site}");

        var image = assertInstanceOf(FtbTextNode.Image.class, result.nodes().getFirst());
        assertEquals("demo:textures/a.png", image.resourceId());
        assertEquals(175, image.width());
        var link = assertInstanceOf(FtbTextNode.Text.class, result.nodes().getLast());
        assertEquals("Safe site", link.value());
        assertEquals(FtbTextNode.Action.Kind.OPEN_URL, link.action().kind());
    }

    @Test void rawJsonKeepsInheritedStyleAndFlagsDangerousEvents() {
        var result = parse("{\"text\":\"root\",\"color\":\"gold\",\"bold\":true,\"extra\":["
                + "{\"text\":\" child\",\"bold\":false,\"underlined\":true,"
                + "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/op me\"}}]}");

        var root = (FtbTextNode.Text) result.nodes().getFirst();
        var child = (FtbTextNode.Text) result.nodes().getLast();
        assertEquals(0xFFAA00, root.style().color());
        assertTrue(root.style().bold());
        assertEquals(0xFFAA00, child.style().color());
        assertFalse(child.style().bold());
        assertTrue(child.style().underlined());
        assertEquals(FtbTextNode.Action.Kind.UNSAFE, child.action().kind());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("BQF-TEXT-UNSAFE-ACTION")));
    }

    @Test void rawJsonOpenUrlAndSameLocaleTranslationAreResolved() {
        var result = parser.parse(List.of("[{\"translate\":\"demo.key\",\"italic\":true},"
                        + "{\"text\":\" docs\",\"clickEvent\":{\"action\":\"open_url\","
                        + "\"value\":\"https://example.com/docs\"}}]"),
                "zh_cn.snbt", "quest.A.quest_desc", Map.of("demo.key", "本地 &c文本"));

        assertEquals("本地 ", ((FtbTextNode.Text) result.nodes().get(0)).value());
        assertTrue(((FtbTextNode.Text) result.nodes().get(0)).style().italic());
        assertEquals(0xFF5555, ((FtbTextNode.Text) result.nodes().get(1)).style().color());
        assertEquals(FtbTextNode.Action.Kind.OPEN_URL,
                ((FtbTextNode.Text) result.nodes().get(2)).action().kind());
    }

    @Test void malformedAndUnresolvedInputRemainVerbatimWithPositions() {
        var malformed = parse("prefix &x suffix");
        var unknown = parse("{missing.translation}");

        assertEquals("prefix &x suffix", ((FtbTextNode.Unknown) malformed.nodes().getLast()).sourceText());
        assertEquals("BQF-TEXT-MALFORMED", malformed.diagnostics().getFirst().code());
        assertEquals(1, malformed.diagnostics().getFirst().source().line());
        assertEquals("{missing.translation}", ((FtbTextNode.Unknown) unknown.nodes().getFirst()).sourceText());
        assertEquals(1, unknown.diagnostics().getFirst().source().column());
    }

    @Test void budgetsStopExpansionDeterministically() {
        var limited = new FtbRichTextParser(2, 1).parse(List.of("a", "b", "c"),
                "en_us.snbt", "quest.A.quest_desc", Map.of());

        assertEquals(2, limited.nodes().size());
        assertEquals(1, limited.diagnostics().stream().filter(d -> d.code().equals("BQF-TEXT-NODE-BUDGET")).count());
    }

    private FtbRichTextParser.Result parse(String value) {
        return parser.parse(List.of(value), "en_us.snbt", "quest.A.quest_desc", Map.of());
    }
}
