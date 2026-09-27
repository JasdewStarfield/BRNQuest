package yourscraft.jasdewstarfield.brnquest.compat.ftb.text;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FtbRichTextParserTest {
    private final FtbRichTextParser parser = new FtbRichTextParser();

    @Test void propertyBoundariesPreserveResourceIdsAndLastValues() {
        // Synthetic data exercises spacing, duplicate keys, and colons inside values together.
        var result = parse("{  image:demo:textures/old.png image:demo:textures/new.png "
                + "width:12 width:45 height:23 text:Old text:New%20caption align:right  }");
        var image = assertInstanceOf(FtbTextNode.Image.class, result.nodes().getFirst());
        assertEquals(1, result.nodes().size());
        assertEquals("demo:textures/new.png", image.resourceId());
        assertEquals(45, image.width());
        assertEquals(23, image.height());
        assertEquals("New caption", image.alt());
        assertEquals("right", image.align());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test void bareAndEmptyPropertiesOverwritePreviousValues() {
        // Both spellings denote an empty value, including when they end the substitution.
        for (String empty : List.of("text", "text:")) {
            var result = parse("{image:demo:textures/a.png width:45 width height:23 height: text:Old " + empty + "}");
            var image = assertInstanceOf(FtbTextNode.Image.class, result.nodes().getFirst());
            assertEquals("", image.alt());
            assertEquals(100, image.width());
            assertEquals(100, image.height());
            assertTrue(result.diagnostics().isEmpty());
        }
    }

    @Test void valuesDecodeOnlySpaceEscapesAndKeepOtherWhitespace() {
        // Tabs/newlines belong to a value; only an ASCII space separates properties.
        var result = parse("{image:demo:textures/a.png text:%20A%20%20B+%2F%2520%2\tC\nD:%20}");
        var image = assertInstanceOf(FtbTextNode.Image.class, result.nodes().getFirst());
        assertEquals(" A  B+%2F%2520%2\tC\nD: ", image.alt());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test void propertyNamesRemainLiteralAndEmptyNamesAreHarmless() {
        // Encoded key text must not become a recognized property or consume its neighbor.
        var result = parse("{image:demo:textures/a.png :ignored text%20:ignored text:caption}");
        var image = assertInstanceOf(FtbTextNode.Image.class, result.nodes().getFirst());
        assertEquals("caption", image.alt());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test void linkPropertiesPreserveUrlPunctuation() {
        var result = parse("{open_url:https://example.com:8443/a:b?q=a+b%2Fc text:Read%20more}");
        var link = assertInstanceOf(FtbTextNode.Text.class, result.nodes().getFirst());
        assertEquals("Read more", link.value());
        assertEquals("https://example.com:8443/a:b?q=a+b%2Fc", link.action().value());
        assertEquals(FtbTextNode.Action.Kind.OPEN_URL, link.action().kind());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test void acceptsExplicitNoActionImagesWithoutLosingImageProperties() {
        // Both the bare marker and FTB's serialized marker describe an ordinary, non-clickable image.
        for (String action : List.of("none", "none:")) {
            var result = parse("{image:picture:textures/horrrsteam/hudong1.png width:100 height:160 "
                    + "align:center fit:true click_action:" + action + "}");
            var image = assertInstanceOf(FtbTextNode.Image.class, result.nodes().getFirst());
            assertEquals("picture:textures/horrrsteam/hudong1.png", image.resourceId());
            assertEquals(100, image.width());
            assertEquals(160, image.height());
            assertTrue(result.diagnostics().isEmpty(), action);
        }
    }

    @Test void continuesToReportRealAndUnknownImageActions() {
        // Do not let a prefix match for "none" silence malformed or unsupported actions.
        for (String action : List.of("run_command:/say%20hello", "open_url:https://example.com",
                "unknown:value", "none:unexpected")) {
            var result = parse("{image:demo:textures/a.png click_action:" + action + "}");
            assertInstanceOf(FtbTextNode.Image.class, result.nodes().getFirst());
            assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("BQF-TEXT-IMAGE-ACTION")
                    && d.severity() == FtbTextDiagnostic.Severity.ERROR), action);
        }
    }

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
