package yourscraft.jasdewstarfield.brnquest.data.text;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/** Immutable, loader-neutral document tree shared by details and editor preview. */
public record RichDocument(List<Block> blocks, List<Diagnostic> diagnostics) {
    public RichDocument {
        blocks = List.copyOf(blocks);
        diagnostics = List.copyOf(diagnostics);
    }

    /** A block owns vertical spacing; inline styles never depend on a Screen instance. */
    public sealed interface Block permits FlowBlock, BulletListBlock, LiteralBlock, ContentBlock {}

    public enum FlowKind { PARAGRAPH, HEADING_1, HEADING_2, HEADING_3 }

    public record FlowBlock(FlowKind kind, List<Inline> inlines) implements Block {
        public FlowBlock {
            kind = Objects.requireNonNull(kind, "kind");
            inlines = List.copyOf(inlines);
        }
    }

    public record BulletListBlock(List<List<Inline>> items) implements Block {
        public BulletListBlock {
            items = items.stream().map(List::copyOf).toList();
        }
    }

    /** Literal blocks are the lossless fallback for plain text and unsupported Markdown. */
    public record LiteralBlock(String text) implements Block {
        public LiteralBlock { text = Objects.requireNonNullElse(text, ""); }
    }

    public enum ContentKind { TEXTURE, ITEM }

    /** Shared client-resolved payload for block and inline content nodes. */
    public sealed interface Content permits ContentBlock, ContentInline {
        ContentKind kind();
        String id();
        String alt();
    }

    /** A content node stores only a validated identifier; resource resolution remains client-side. */
    public record ContentBlock(ContentKind kind, String id, String alt) implements Block, Content {
        public ContentBlock {
            kind = Objects.requireNonNull(kind, "kind");
            id = Objects.requireNonNull(id, "id");
            alt = Objects.requireNonNullElse(alt, "");
        }
    }

    public sealed interface Inline permits Text, Emphasis, Strong, StyleSpan, Code, Link, LineBreak, ContentInline {}

    /** Inline content is measured as one indivisible glyph-like atom during wrapping. */
    public record ContentInline(ContentKind kind, String id, String alt) implements Inline, Content {
        public ContentInline {
            kind = Objects.requireNonNull(kind, "kind");
            id = Objects.requireNonNull(id, "id");
            alt = Objects.requireNonNullElse(alt, "");
        }
    }

    public record Text(String value) implements Inline {
        public Text { value = Objects.requireNonNullElse(value, ""); }
    }

    public record Emphasis(List<Inline> children) implements Inline {
        public Emphasis { children = List.copyOf(children); }
    }

    public record Strong(List<Inline> children) implements Inline {
        public Strong { children = List.copyOf(children); }
    }

    /** Renderer-neutral Minecraft text decorations shared by legacy text and Markdown extensions. */
    public record InlineStyle(Integer color, boolean bold, boolean italic, boolean underlined,
                              boolean strikethrough, boolean obfuscated) {
        public static final InlineStyle EMPTY = new InlineStyle(null, false, false, false, false, false);

        public InlineStyle {
            if (color != null) color &= 0xFFFFFF;
        }

        public boolean isEmpty() {
            return color == null && !bold && !italic && !underlined && !strikethrough && !obfuscated;
        }
    }

    /** Applies additive text decorations without exposing Minecraft client classes to the document tree. */
    public record StyleSpan(List<Inline> children, InlineStyle style) implements Inline {
        public StyleSpan {
            children = List.copyOf(children);
            style = Objects.requireNonNullElse(style, InlineStyle.EMPTY);
        }
    }

    public record Code(String value) implements Inline {
        public Code { value = Objects.requireNonNullElse(value, ""); }
    }

    /** Only normalized HTTP(S) destinations can reach the client interaction layer. */
    public record Link(List<Inline> label, URI destination) implements Inline {
        public Link {
            label = List.copyOf(label);
            destination = Objects.requireNonNull(destination, "destination");
        }
    }

    public record LineBreak(boolean hard) implements Inline {}

    public record Diagnostic(String code, int offset, int length, String message) {
        public Diagnostic {
            code = Objects.requireNonNullElse(code, "UNKNOWN");
            offset = Math.max(0, offset);
            length = Math.max(0, length);
            message = Objects.requireNonNullElse(message, "");
        }
    }
}
