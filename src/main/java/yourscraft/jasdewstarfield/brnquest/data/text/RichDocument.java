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
    public sealed interface Block permits FlowBlock, BulletListBlock, LiteralBlock {}

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

    public sealed interface Inline permits Text, Emphasis, Strong, Code, Link, LineBreak {}

    public record Text(String value) implements Inline {
        public Text { value = Objects.requireNonNullElse(value, ""); }
    }

    public record Emphasis(List<Inline> children) implements Inline {
        public Emphasis { children = List.copyOf(children); }
    }

    public record Strong(List<Inline> children) implements Inline {
        public Strong { children = List.copyOf(children); }
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
