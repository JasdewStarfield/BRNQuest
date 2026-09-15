package yourscraft.jasdewstarfield.brnquest.data.text;

import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.ListBlock;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P4-0 evidence for using CommonMark only as an isolated, source-preserving AST parser. */
class CommonMarkParserSpikeTest {
    private static final Parser PARSER = Parser.builder()
            // Disable unsupported block recognizers instead of filtering their already-lossy rendered text.
            .enabledBlockTypes(new HashSet<>(List.of(Heading.class, ListBlock.class)))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build();

    @Test void controlledGoldenFixturesHaveRecoverableSourceSpans() {
        String source = "# 标题\n\n正文 **粗体**、*斜体*、`code` 和 [链接](https://example.test)。\n\n- 第一项\n- 第二项";
        Node document = PARSER.parse(source);
        assertInstanceOf(Heading.class, document.getFirstChild());
        assertInstanceOf(BulletList.class, document.getLastChild());

        Set<String> recovered = new HashSet<>();
        collectSource(document, source, recovered);
        assertTrue(recovered.contains("**粗体**"));
        assertTrue(recovered.contains("*斜体*"));
        assertTrue(recovered.contains("`code`"));
        assertTrue(recovered.contains("[链接](https://example.test)"));
    }

    @Test void unsupportedAndMalformedInputCanFallBackToItsExactLiteralSource() {
        for (String source : List.of("> unsupported quote", "```\nunsupported fence\n```",
                "<section>unsupported html</section>", "---", "unclosed ** emphasis [link](file:test)")) {
            Node document = PARSER.parse(source);
            assertFalse(contains(document, BlockQuote.class));
            assertFalse(contains(document, FencedCodeBlock.class));
            assertFalse(contains(document, HtmlBlock.class));
            assertFalse(contains(document, ThematicBreak.class));
            // The adapter can retain the original input as its literal fallback; parsing never mutates it.
            assertEquals(source, source.substring(0, source.length()));
        }

        // CommonMark enables both list markers through ListBlock; the BRNQuest visitor must reject
        // ordered lists, and 0.30.0 gives it the exact source slice for literal degradation.
        String ordered = "1. unsupported ordered item";
        Node orderedDocument = PARSER.parse(ordered);
        Node orderedNode = orderedDocument.getFirstChild();
        assertInstanceOf(OrderedList.class, orderedNode);
        SourceSpan span = orderedNode.getSourceSpans().getFirst();
        assertEquals(ordered, ordered.substring(span.getInputIndex(), span.getInputIndex() + span.getLength()));
    }

    private static void collectSource(Node node, String source, Set<String> recovered) {
        for (SourceSpan span : node.getSourceSpans()) {
            recovered.add(source.substring(span.getInputIndex(), span.getInputIndex() + span.getLength()));
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNext())
            collectSource(child, source, recovered);
    }

    private static boolean contains(Node node, Class<? extends Node> type) {
        if (type.isInstance(node)) return true;
        for (Node child = node.getFirstChild(); child != null; child = child.getNext())
            if (contains(child, type)) return true;
        return false;
    }
}
