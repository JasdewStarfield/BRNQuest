package yourscraft.jasdewstarfield.brnquest.data.text;

import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
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
    public static final int PARSER_VERSION = 5;
    public static final int DEFAULT_NODE_BUDGET = 4096;
    public static final int DEFAULT_LINK_BUDGET = 128;
    public static final int DEFAULT_CONTENT_BUDGET = 64;

    private final Parser parser;
    private final int nodeBudget;
    private final int linkBudget;
    private final int contentBudget;

    public MarkdownParserAdapter() {
        this(DEFAULT_NODE_BUDGET, DEFAULT_LINK_BUDGET, DEFAULT_CONTENT_BUDGET);
    }

    public MarkdownParserAdapter(int nodeBudget, int linkBudget) {
        this(nodeBudget, linkBudget, DEFAULT_CONTENT_BUDGET);
    }

    public MarkdownParserAdapter(int nodeBudget, int linkBudget, int contentBudget) {
        this.nodeBudget = Math.max(1, nodeBudget);
        this.linkBudget = Math.max(0, linkBudget);
        this.contentBudget = Math.max(0, contentBudget);
        // Source spans are required for exact literal degradation of unsupported constructs.
        this.parser = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build();
    }

    public RichDocument parse(ResolvedDocument source) {
        String text = source.text();
        if (source.format().equals(DocumentFormat.PLAIN)) return MinecraftLegacyTextParser.parse(text);
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
        if (counter.contents > contentBudget)
            return literalDocument(text, "CONTENT_BUDGET_EXCEEDED", 0, text.length(),
                    "Markdown content node budget exceeded");

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
        if (node instanceof Link link && !isStyleTarget(link.getDestination())) counter.links++;
        if (node instanceof Image) counter.contents++;
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) count(child, counter);
    }

    private static final class Counter {
        private int nodes;
        private int links;
        private int contents;
    }

    private static final class Conversion {
        private final String source;
        private final List<RichDocument.Diagnostic> diagnostics = new ArrayList<>();

        private Conversion(String source) { this.source = source; }

        private RichDocument.Block block(Node node) {
            if (node instanceof Paragraph && node.getFirstChild() instanceof Image image
                    && node.getFirstChild() == node.getLastChild()) {
                ContentTarget target = contentTarget(image.getDestination());
                if (target != null) return new RichDocument.ContentBlock(target.kind(), target.id(), altText(image));
                return literalBlock(node, "CONTENT_TARGET_UNSUPPORTED",
                        "Only texture:namespace:path and item:namespace:id content targets are supported");
            }
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
            if (node instanceof Image image) {
                ContentTarget target = contentTarget(image.getDestination());
                if (target != null)
                    return new RichDocument.ContentInline(target.kind(), target.id(), altText(image));
                return literalInline(node, "CONTENT_TARGET_UNSUPPORTED",
                        "Only texture:namespace:path and item:namespace:id content targets are supported");
            }
            if (node instanceof Link link) {
                RichDocument.InlineStyle style = markdownStyle(link.getDestination());
                if (style != null) return new RichDocument.StyleSpan(inlines(node), style);
                if (isStyleTarget(link.getDestination())) return literalInline(node, "STYLE_TARGET_UNSUPPORTED",
                        "Unsupported BRNQuest Markdown style target");
                String questId = questTarget(link.getDestination());
                if (questId != null)
                    return new RichDocument.Link(inlines(node), new RichDocument.QuestLink(questId));
                if (isQuestTarget(link.getDestination())) return literalInline(node, "QUEST_LINK_TARGET_INVALID",
                        "BRNQuest quest links require a namespaced quest ID");
                URI destination = safeHttpUri(link.getDestination());
                if (destination != null)
                    return new RichDocument.Link(inlines(node), new RichDocument.ExternalLink(destination));
                return literalInline(node, "LINK_SCHEME_UNSUPPORTED", "Only HTTP and HTTPS links are interactive");
            }
            return literalInline(node, "INLINE_UNSUPPORTED", "Unsupported Markdown inline rendered literally");
        }

        private static String altText(Node image) {
            StringBuilder result = new StringBuilder();
            appendAltText(image, result);
            return result.toString();
        }

        private static void appendAltText(Node parent, StringBuilder output) {
            for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof Text text) output.append(text.getLiteral());
                else if (child instanceof Code code) output.append(code.getLiteral());
                else appendAltText(child, output);
            }
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
    private record ContentTarget(RichDocument.ContentKind kind, String id) {}

    private static final String STYLE_PREFIX = "brnquest:style/";
    private static final String QUEST_PREFIX = "brnquest:quest/";
    private static boolean isStyleTarget(String destination) {
        return destination != null && destination.toLowerCase(Locale.ROOT).startsWith(STYLE_PREFIX);
    }

    private static boolean isQuestTarget(String destination) {
        return destination != null && destination.toLowerCase(Locale.ROOT).startsWith(QUEST_PREFIX);
    }

    /** Quest targets are intentionally full IDs so imports and reordered chapters cannot retarget a link. */
    private static String questTarget(String destination) {
        if (!isQuestTarget(destination)) return null;
        String id = destination.substring(QUEST_PREFIX.length());
        return id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") ? id : null;
    }

    /** Style links reuse CommonMark's nested label parsing but never enter the clickable-link layer. */
    private static RichDocument.InlineStyle markdownStyle(String destination) {
        if (!isStyleTarget(destination)) return null;
        String target = destination.substring(STYLE_PREFIX.length()).toLowerCase(Locale.ROOT);
        Integer color = null;
        boolean underlined = false;
        boolean strikethrough = false;
        boolean obfuscated = false;
        for (String part : target.split("\\+")) {
            if (part.equals("underline")) underlined = true;
            else if (part.equals("strikethrough")) strikethrough = true;
            else if (part.equals("obfuscated")) obfuscated = true;
            else if (part.startsWith("color/")) {
                if (color != null) return null;
                String value = part.substring("color/".length());
                MinecraftTextColor named = MinecraftTextColor.byName(value);
                color = named == null ? null : named.rgb();
                if (color == null && value.matches("[0-9a-f]{6}")) color = Integer.parseInt(value, 16);
                if (color == null) return null;
            } else return null;
        }
        RichDocument.InlineStyle style = new RichDocument.InlineStyle(color, false, false,
                underlined, strikethrough, obfuscated);
        return style.isEmpty() ? null : style;
    }

    private static ContentTarget contentTarget(String destination) {
        if (destination == null) return null;
        RichDocument.ContentKind kind;
        String id;
        if (destination.startsWith("texture:")) {
            kind = RichDocument.ContentKind.TEXTURE;
            id = destination.substring("texture:".length());
        } else if (destination.startsWith("item:")) {
            kind = RichDocument.ContentKind.ITEM;
            id = destination.substring("item:".length());
        } else return null;
        // Requiring an explicit namespace keeps resource interpretation deterministic on every client.
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) return null;
        return new ContentTarget(kind, id);
    }

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
