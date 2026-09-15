package yourscraft.jasdewstarfield.brnquest.data.text;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Locks BRNQuest's controlled syntax instead of inheriting CommonMark behavior accidentally. */
class MarkdownParserAdapterTest {
    private final MarkdownParserAdapter parser = new MarkdownParserAdapter();

    @Test void parsesTheMarkdownV1WhitelistIntoOwnedNodes() {
        RichDocument document = parse("# 标题\n\n正文 **粗体**、*斜体*、`code` 和 [链接](HTTPS://Example.Test/a/../b)。\n\n- 一\n- 二");

        assertEquals(3, document.blocks().size());
        assertEquals(RichDocument.FlowKind.HEADING_1,
                assertInstanceOf(RichDocument.FlowBlock.class, document.blocks().getFirst()).kind());
        RichDocument.FlowBlock paragraph = assertInstanceOf(RichDocument.FlowBlock.class, document.blocks().get(1));
        assertTrue(paragraph.inlines().stream().anyMatch(RichDocument.Strong.class::isInstance));
        assertTrue(paragraph.inlines().stream().anyMatch(RichDocument.Emphasis.class::isInstance));
        assertTrue(paragraph.inlines().stream().anyMatch(RichDocument.Code.class::isInstance));
        RichDocument.Link link = paragraph.inlines().stream().filter(RichDocument.Link.class::isInstance)
                .map(RichDocument.Link.class::cast).findFirst().orElseThrow();
        assertEquals(URI.create("https://example.test/b"), link.destination());
        assertEquals(2, assertInstanceOf(RichDocument.BulletListBlock.class, document.blocks().getLast()).items().size());
        assertTrue(document.diagnostics().isEmpty());
    }

    @Test void unsupportedBlocksAndSchemesKeepTheirExactSource() {
        RichDocument ordered = parse("1. ordered");
        assertEquals("1. ordered", assertInstanceOf(RichDocument.LiteralBlock.class,
                ordered.blocks().getFirst()).text());
        assertEquals("ORDERED_LIST_UNSUPPORTED", ordered.diagnostics().getFirst().code());

        RichDocument unsafe = parse("[local](file:test)");
        RichDocument.FlowBlock paragraph = assertInstanceOf(RichDocument.FlowBlock.class, unsafe.blocks().getFirst());
        assertEquals("[local](file:test)", assertInstanceOf(RichDocument.Text.class,
                paragraph.inlines().getFirst()).value());
        assertEquals("LINK_SCHEME_UNSUPPORTED", unsafe.diagnostics().getFirst().code());

        for (String source : List.of("#### level four", "> quote", "```\ncode\n```", "+ excluded marker",
                "- parent\n  - nested")) {
            RichDocument unsupported = parse(source);
            assertEquals(source, assertInstanceOf(RichDocument.LiteralBlock.class,
                    unsupported.blocks().getFirst()).text());
            assertFalse(unsupported.diagnostics().isEmpty());
        }
    }

    @Test void plainUnknownMalformedAndEscapedInputHaveStableFallbacks() {
        String special = "literal **stars** [link](https://example.test)";
        RichDocument plain = parser.parse(new ResolvedDocument(special, DocumentFormat.PLAIN, "en_us"));
        assertEquals(special, assertInstanceOf(RichDocument.LiteralBlock.class, plain.blocks().getFirst()).text());

        RichDocument unknown = parser.parse(new ResolvedDocument(special, DocumentFormat.parse("markdown_v2"), "en_us"));
        assertEquals(special, assertInstanceOf(RichDocument.LiteralBlock.class, unknown.blocks().getFirst()).text());
        assertEquals("UNSUPPORTED_FORMAT", unknown.diagnostics().getFirst().code());

        RichDocument malformed = parse("unclosed ** emphasis [link](file:test)");
        RichDocument.FlowBlock paragraph = assertInstanceOf(RichDocument.FlowBlock.class, malformed.blocks().getFirst());
        assertTrue(visibleText(paragraph.inlines()).contains("**"));
        assertEquals("*escaped*", visibleText(assertInstanceOf(RichDocument.FlowBlock.class,
                parse("\\*escaped\\*").blocks().getFirst()).inlines()));
    }

    @Test void plainTextParsesVanillaSectionCodesWithoutLosingUnknownText() {
        RichDocument document = parser.parse(new ResolvedDocument(
                "普通 §l粗体 §C红色§n下划线 §r重置 §x未知 §", DocumentFormat.PLAIN, "zh_cn"));
        RichDocument.FlowBlock paragraph = assertInstanceOf(RichDocument.FlowBlock.class,
                document.blocks().getFirst());

        assertEquals("普通 粗体 红色下划线 重置 §x未知 §", visibleText(paragraph.inlines()));
        List<RichDocument.StyleSpan> styles = paragraph.inlines().stream()
                .filter(RichDocument.StyleSpan.class::isInstance).map(RichDocument.StyleSpan.class::cast).toList();
        assertTrue(styles.stream().anyMatch(span -> span.style().bold()));
        assertTrue(styles.stream().anyMatch(span -> Integer.valueOf(0xFF5555).equals(span.style().color())
                && span.style().underlined() && !span.style().bold()));
        assertTrue(document.diagnostics().isEmpty());
    }

    @Test void budgetsDegradeTheWholeDocumentBeforeProducingPartialOutput() {
        RichDocument nodes = new MarkdownParserAdapter(3, 10).parse(
                new ResolvedDocument("**one** and *two*", DocumentFormat.MARKDOWN_V1, "en_us"));
        assertInstanceOf(RichDocument.LiteralBlock.class, nodes.blocks().getFirst());
        assertEquals("NODE_BUDGET_EXCEEDED", nodes.diagnostics().getFirst().code());

        RichDocument links = new MarkdownParserAdapter(100, 0).parse(
                new ResolvedDocument("[one](https://example.test)", DocumentFormat.MARKDOWN_V1, "en_us"));
        assertEquals("LINK_BUDGET_EXCEEDED", links.diagnostics().getFirst().code());

        RichDocument contents = new MarkdownParserAdapter(100, 10, 0).parse(
                new ResolvedDocument("![stone](item:minecraft:stone)", DocumentFormat.MARKDOWN_V1, "en_us"));
        assertInstanceOf(RichDocument.LiteralBlock.class, contents.blocks().getFirst());
        assertEquals("CONTENT_BUDGET_EXCEEDED", contents.diagnostics().getFirst().code());
    }

    @Test void acceptsOnlyExplicitNamespacedTextureAndItemContent() {
        RichDocument texture = parse("![panel](texture:brnquest:textures/gui/panel.png)");
        RichDocument.ContentBlock textureNode = assertInstanceOf(RichDocument.ContentBlock.class,
                texture.blocks().getFirst());
        assertEquals(RichDocument.ContentKind.TEXTURE, textureNode.kind());
        assertEquals("brnquest:textures/gui/panel.png", textureNode.id());
        assertEquals("panel", textureNode.alt());

        RichDocument item = parse("![stone](item:minecraft:stone)");
        assertEquals(RichDocument.ContentKind.ITEM, assertInstanceOf(RichDocument.ContentBlock.class,
                item.blocks().getFirst()).kind());

        RichDocument.FlowBlock inline = assertInstanceOf(RichDocument.FlowBlock.class,
                parse("before ![item](item:minecraft:stone) after").blocks().getFirst());
        RichDocument.ContentInline inlineItem = inline.inlines().stream()
                .filter(RichDocument.ContentInline.class::isInstance)
                .map(RichDocument.ContentInline.class::cast).findFirst().orElseThrow();
        assertEquals(RichDocument.ContentKind.ITEM, inlineItem.kind());
        assertEquals("minecraft:stone", inlineItem.id());

        for (String source : List.of("![remote](https://example.test/a.png)",
                "![implicit](item:stone)", "before ![implicit](texture:panel.png) after")) {
            RichDocument rejected = parse(source);
            assertFalse(rejected.diagnostics().isEmpty());
            assertTrue(rejected.blocks().getFirst() instanceof RichDocument.LiteralBlock
                    || rejected.blocks().getFirst() instanceof RichDocument.FlowBlock);
        }
    }

    private RichDocument parse(String text) {
        return parser.parse(new ResolvedDocument(text, DocumentFormat.MARKDOWN_V1, "zh_cn"));
    }

    private static String visibleText(List<RichDocument.Inline> inlines) {
        StringBuilder result = new StringBuilder();
        for (RichDocument.Inline inline : inlines) {
            if (inline instanceof RichDocument.Text text) result.append(text.value());
            else if (inline instanceof RichDocument.Code code) result.append(code.value());
            else if (inline instanceof RichDocument.Emphasis emphasis) result.append(visibleText(emphasis.children()));
            else if (inline instanceof RichDocument.Strong strong) result.append(visibleText(strong.children()));
            else if (inline instanceof RichDocument.StyleSpan span) result.append(visibleText(span.children()));
            else if (inline instanceof RichDocument.Link link) result.append(visibleText(link.label()));
            else if (inline instanceof RichDocument.ContentInline content) result.append(content.alt());
            else if (inline instanceof RichDocument.LineBreak) result.append('\n');
        }
        return result.toString();
    }
}
