package yourscraft.jasdewstarfield.brnquest.compat.ftb.text;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.text.DocumentFormat;
import yourscraft.jasdewstarfield.brnquest.data.text.MarkdownParserAdapter;
import yourscraft.jasdewstarfield.brnquest.data.text.ResolvedDocument;
import yourscraft.jasdewstarfield.brnquest.data.text.RichDocument;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BrnQuestMarkdownSerializerTest {
    private final FtbRichTextParser parser = new FtbRichTextParser();
    private final BrnQuestMarkdownSerializer serializer = new BrnQuestMarkdownSerializer();

    @Test void escapesLiteralMarkdownAndPreservesListEntryBoundaries() {
        var converted = convert(List.of("literal * _ [x](y)", "", "next"));

        assertEquals("literal \\* \\_ \\[x\\]\\(y\\)\n\nnext", converted.markdown());
        assertTrue(converted.diagnostics().isEmpty());
    }

    @Test void combinesLegacyStylesIntoOneNonInteractiveTarget() {
        var converted = convert(List.of("&c&l&o&n&m&kDanger&r done"));

        assertEquals("[***Danger***](brnquest:style/color/ff5555+underline+strikethrough+obfuscated) done",
                converted.markdown());
        RichDocument document = new MarkdownParserAdapter().parse(new ResolvedDocument(
                converted.markdown(), DocumentFormat.MARKDOWN_V1, "en_us"));
        var paragraph = assertInstanceOf(RichDocument.FlowBlock.class, document.blocks().getFirst());
        var style = assertInstanceOf(RichDocument.StyleSpan.class, paragraph.inlines().getFirst()).style();
        assertEquals(0xFF5555, style.color());
        assertTrue(style.underlined() && style.strikethrough() && style.obfuscated());
        assertTrue(document.diagnostics().isEmpty());
    }

    @Test void mapsSafeLinksAndImagesButReportsLayoutLoss() {
        var converted = convert(List.of("{open_url:https://example.com/a text:Read%20me}",
                "{image:demo:textures/a.png width:175 height:90 align:right}"));

        assertEquals("[Read me](<https://example.com/a>)\n![](texture:demo:textures/a.png)", converted.markdown());
        assertTrue(converted.diagnostics().stream().anyMatch(d -> d.code().equals("BQF-TEXT-IMAGE-LAYOUT")));
    }

    @Test void flattensPageBreakWithoutDroppingAdjacentText() {
        var converted = convert(List.of("before", "{@pagebreak}", "after"));

        assertEquals("before\n\nafter", converted.markdown());
        assertTrue(converted.diagnostics().stream()
                .anyMatch(d -> d.code().equals("BQF-TEXT-PAGEBREAK-FLATTENED")));
    }

    @Test void rainbowAndChangePageKeepLabelsAndProducePositionedDiagnostics() {
        var parsed = parser.parse(List.of("&zRainbow", "{\"text\":\"jump\",\"clickEvent\":{"
                        + "\"action\":\"change_page\",\"value\":\"ABCDEF/2\"}}"),
                "en_us.snbt", "quest.A.quest_desc", Map.of());
        var converted = serializer.serialize(parsed);

        assertEquals("Rainbow\njump", converted.markdown());
        assertEquals(List.of("BQF-TEXT-RAINBOW", "BQF-TEXT-CHANGE-PAGE"), converted.diagnostics().stream()
                .map(FtbTextDiagnostic::code).toList());
        assertEquals(2, converted.diagnostics().getLast().source().line());
    }

    @Test void knownFtbQuestTargetBecomesStableInBookLinkAndFlattensOnlyItsSubpage() {
        var parsed = parser.parse(List.of("{\"text\":\"jump\",\"clickEvent\":{"
                        + "\"action\":\"change_page\",\"value\":\"000000000000ABCD/2\"}}"),
                "en_us.snbt", "quest.A.quest_desc", Map.of());
        var converted = serializer.serialize(parsed, raw -> raw.startsWith("000000000000ABCD")
                ? "converted:000000000000abcd" : null);

        assertEquals("[jump](brnquest:quest/converted:000000000000abcd)", converted.markdown());
        assertTrue(converted.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("BQF-TEXT-SUBPAGE-FLATTENED")));
        assertFalse(converted.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("BQF-TEXT-CHANGE-PAGE")));
    }

    @Test void unsafeActionsNeverBecomeMarkdownLinks() {
        var converted = convert(List.of("{\"text\":\"do not run\",\"clickEvent\":{"
                + "\"action\":\"run_command\",\"value\":\"/op me\"}}"));

        assertEquals("do not run", converted.markdown());
        assertTrue(converted.diagnostics().stream().anyMatch(d -> d.code().equals("BQF-TEXT-UNSAFE-ACTION")));
        assertFalse(converted.markdown().contains("/op me"));
    }

    private BrnQuestMarkdownSerializer.Result convert(List<String> lines) {
        return serializer.serialize(parser.parse(lines, "en_us.snbt", "quest.A.quest_desc", Map.of()));
    }
}
