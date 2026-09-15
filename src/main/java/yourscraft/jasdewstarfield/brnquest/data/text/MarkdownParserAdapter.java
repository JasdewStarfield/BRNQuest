package yourscraft.jasdewstarfield.brnquest.data.text;

import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Converts CommonMark's AST into BRNQuest's deliberately smaller markdown_v1 tree. */
public final class MarkdownParserAdapter {
    public static final int PARSER_VERSION = 1;
    public static final int DEFAULT_NODE_BUDGET = 4096;
    public static final int DEFAULT_LINK_BUDGET = 128;

    private final Parser parser;
    private final int nodeBudget;
    private final int linkBudget;

    public MarkdownParserAdapter() {
        this(DEFAULT_NODE_BUDGET, DEFAULT_LINK_BUDGET);
    }

    public MarkdownParserAdapter(int nodeBudget, int linkBudget) {
        this.nodeBudget = Math.max(1, nodeBudget);
        this.linkBudget = Math.max(0, linkBudget);
        // Source spans are required for exact literal degradation of unsupported constructs.
        this.parser = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build();
    }

    public RichDocument parse(ResolvedDocument source) {
        String text = source.text();
        if (source.format().equals(DocumentFormat.PLAIN))
            return new RichDocument(List.of(new RichDocument.LiteralBlock(text)), List.of());
        if (!source.format().equals(DocumentFormat.MARKDOWN_V1)) {
            return literalDocument(text, "UNSUPPORTED_FORMAT", 0, text.length(),
                    "Unknown document format " + source.format().serializedName());
        }

        Node root = parser.parse(text);
        Counter counter = new Counter();
        count(root, counter);
        if (counter.nodes > nodeBudget)
            return literalDocument(text, "NODE_BUDGET_EXCEEDED", 0, text.length(), "Markdown node budget exceeded");
        if (counter.links > linkBudget)
            return literalDocument(text, "LINK_BUDGET_EXCEEDED", 0, text.length(), "Markdown link budget exceeded");

        Conversion conversion = new Conversion(text);
        List<RichDocument.Block> blocks = new ArrayList<>();
        for (Node child = root.getFirstChild(); child != null; child = child.getNext())
            blocks.add(conversion.block(child));
        return new RichDocument(blocks, conversion.diagnostics);
    }

    private static RichDocument literalDocument(String text, String code, int offset, int length, String message) {
        return new RichDocument(List.of(new RichDocument.LiteralBlock(text)),
                List.of(new RichDocument.Diagnostic(code, offset, length, message)));
    }

    private static void count(Node node, Counter counter) {
        counter.nodes++;
        if (node instanceof Link) counter.links++;
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) count(child, counter);
    }

    private static final class Counter {
        private int nodes;
        private int links;
    }

    private static final class Conversion {
        private final String source;
        private final List<RichDocument.Diagnostic> diagnostics = new ArrayList<>();

        private Conversion(String source) { this.source = source; }

        private RichDocument.Block block(Node node) {
            if (node instanceof Paragraph)
                return new RichDocument.FlowBlock(RichDocument.FlowKind.PARAGRAPH, inlines(node));
            if (node instanceof Heading heading && heading.getLevel() <= 3) {
                RichDocument.FlowKind kind = switch (heading.getLevel()) {
                    case 1 -> RichDocument.FlowKind.HEADING_1;
                    case 2 -> RichDocument.FlowKind.HEADING_2;
                    default -> RichDocument.FlowKind.HEADING_3;
                };
                return new RichDocument.FlowBlock(kind, inlines(node));
            }
            if (node instanceof BulletList bullet && supportedBulletList(bullet)) {
                List<List<RichDocument.Inline>> items = new ArrayList<>();
                for (Node item = bullet.getFirstChild(); item != null; item = item.getNext())
                    items.add(inlines(item.getFirstChild()));
                return new RichDocument.BulletListBlock(items);
            }
            String code = node instanceof OrderedList ? "ORDERED_LIST_UNSUPPORTED" : "BLOCK_UNSUPPORTED";
            return literalBlock(node, code, "Unsupported Markdown block rendered literally");
        }

        private boolean supportedBulletList(BulletList list) {
            // markdown_v1 intentionally excludes CommonMark's '+' list marker.
            if (!(list.getMarker().equals("-") || list.getMarker().equals("*"))) return false;
            for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
                if (!(item instanceof ListItem) || !(item.getFirstChild() instanceof Paragraph)
                        || item.getFirstChild() != item.getLastChild()) return false;
            }
            return true;
        }

        private List<RichDocument.Inline> inlines(Node parent) {
            List<RichDocument.Inline> result = new ArrayList<>();
            for (Node child = parent.getFirstChild(); child != null; child = child.getNext())
                result.add(inline(child));
            return result;
        }

        private RichDocument.Inline inline(Node node) {
            if (node instanceof Text text) return new RichDocument.Text(text.getLiteral());
            if (node instanceof SoftLineBreak) return new RichDocument.LineBreak(false);
            if (node instanceof HardLineBreak) return new RichDocument.LineBreak(true);
            if (node instanceof Emphasis) return new RichDocument.Emphasis(inlines(node));
            if (node instanceof StrongEmphasis) return new RichDocument.Strong(inlines(node));
            if (node instanceof Code code) return new RichDocument.Code(code.getLiteral());
            if (node instanceof Link link) {
                URI destination = safeHttpUri(link.getDestination());
                if (destination != null) return new RichDocument.Link(inlines(node), destination);
                return literalInline(node, "LINK_SCHEME_UNSUPPORTED", "Only HTTP and HTTPS links are interactive");
            }
            return literalInline(node, "INLINE_UNSUPPORTED", "Unsupported Markdown inline rendered literally");
        }

        private RichDocument.LiteralBlock literalBlock(Node node, String code, String message) {
            Slice slice = slice(node);
            diagnostics.add(new RichDocument.Diagnostic(code, slice.offset, slice.text.length(), message));
            return new RichDocument.LiteralBlock(slice.text);
        }

        private RichDocument.Text literalInline(Node node, String code, String message) {
            Slice slice = slice(node);
            diagnostics.add(new RichDocument.Diagnostic(code, slice.offset, slice.text.length(), message));
            return new RichDocument.Text(slice.text);
        }

        private Slice slice(Node node) {
            List<SourceSpan> spans = node.getSourceSpans();
            if (spans.isEmpty()) return new Slice(0, "");
            int start = spans.getFirst().getInputIndex();
            SourceSpan last = spans.getLast();
            int end = Math.min(source.length(), last.getInputIndex() + last.getLength());
            return new Slice(start, source.substring(start, Math.max(start, end)));
        }
    }

    private record Slice(int offset, String text) {}

    private static URI safeHttpUri(String raw) {
        try {
            URI uri = new URI(raw).normalize();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null || uri.getHost().isBlank())
                return null;
            return new URI(scheme, uri.getUserInfo(), uri.getHost().toLowerCase(Locale.ROOT), uri.getPort(),
                    uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException exception) {
            return null;
        }
    }
}
