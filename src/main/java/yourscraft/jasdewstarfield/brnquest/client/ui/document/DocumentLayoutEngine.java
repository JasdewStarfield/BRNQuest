package yourscraft.jasdewstarfield.brnquest.client.ui.document;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.MixedTextLayout;
import yourscraft.jasdewstarfield.brnquest.data.text.RichDocument;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure mixed-style layout engine; Minecraft font access is isolated behind {@link Metrics}. */
public final class DocumentLayoutEngine {
    static final int MAX_CONTENT_HEIGHT = 160;
    static final int INLINE_CONTENT_HEIGHT = 16;
    static final int INLINE_TEXTURE_WIDTH = 32;

    public interface Metrics {
        int width(String text, DocumentTextStyle style);
        int lineHeight(DocumentTextStyle style);
        /** Missing resources keep deterministic geometry so restoring a pack cannot collapse the document. */
        default ContentSize contentSize(RichDocument.Content content) {
            return new ContentSize(24, 24, false);
        }
    }

    public record ContentSize(int width, int height, boolean present) {}

    public DocumentLayout layout(RichDocument document, int maximumWidth, Metrics metrics) {
        int width = Math.max(1, maximumWidth);
        List<DocumentLayout.Line> lines = new ArrayList<>();
        List<DocumentLayout.ContentHit> contents = new ArrayList<>();
        int y = 0;
        for (int blockIndex = 0; blockIndex < document.blocks().size(); blockIndex++) {
            RichDocument.Block block = document.blocks().get(blockIndex);
            if (block instanceof RichDocument.FlowBlock flow) {
                int heading = switch (flow.kind()) {
                    case HEADING_1 -> 1;
                    case HEADING_2 -> 2;
                    case HEADING_3 -> 3;
                    default -> 0;
                };
                y = layoutAtoms(flatten(flow.inlines(), DocumentTextStyle.PLAIN.withHeading(heading)), 0,
                        y, width, metrics, lines, contents);
            } else if (block instanceof RichDocument.LiteralBlock literal) {
                y = layoutLiteral(literal.text(), y, width, metrics, lines);
            } else if (block instanceof RichDocument.BulletListBlock list) {
                for (List<RichDocument.Inline> item : list.items()) {
                    List<Atom> itemAtoms = new ArrayList<>(atoms("• ", DocumentTextStyle.PLAIN));
                    itemAtoms.addAll(flatten(item, DocumentTextStyle.PLAIN));
                    y = layoutAtoms(itemAtoms, 10, y, width, metrics, lines, contents);
                    y += 2;
                }
                y = Math.max(0, y - 2);
            } else if (block instanceof RichDocument.ContentBlock content) {
                ContentSize natural = metrics.contentSize(content);
                int naturalWidth = Math.max(1, natural.width());
                int naturalHeight = Math.max(1, natural.height());
                double scale = Math.min(1.0, Math.min((double) width / naturalWidth,
                        (double) MAX_CONTENT_HEIGHT / naturalHeight));
                int renderedWidth = Math.max(1, (int) Math.round(naturalWidth * scale));
                int renderedHeight = Math.max(1, (int) Math.round(naturalHeight * scale));
                var bounds = new DocumentLayout.Bounds(0, y, renderedWidth, y + renderedHeight);
                contents.add(new DocumentLayout.ContentHit(content, bounds, natural.present()));
                y += renderedHeight;
            }
            if (blockIndex + 1 < document.blocks().size()) y += block instanceof RichDocument.FlowBlock flow
                    && flow.kind() != RichDocument.FlowKind.PARAGRAPH ? 5 : 4;
        }
        return new DocumentLayout(lines, collectLinks(lines), contents, y, width);
    }

    private static int layoutLiteral(String text, int startY, int width, Metrics metrics,
                                     List<DocumentLayout.Line> output) {
        int y = startY;
        // Keep historical plain-text geometry byte-for-byte compatible with the existing wrapper.
        for (String line : MixedTextLayout.wrap(text, width,
                candidate -> metrics.width(candidate, DocumentTextStyle.PLAIN)))
            y = emit(atoms(line, DocumentTextStyle.PLAIN), 0, y, width, metrics, output);
        return y;
    }

    private static int layoutAtoms(List<Atom> source, int continuationIndent, int startY, int width,
                                   Metrics metrics, List<DocumentLayout.Line> output,
                                   List<DocumentLayout.ContentHit> contents) {
        List<Atom> pending = new ArrayList<>();
        int y = startY;
        int indent = 0;
        for (Atom atom : source) {
            if (atom.text.equals("\n")) {
                y = emit(pending, indent, y, width, metrics, output, contents);
                pending.clear();
                indent = Math.min(continuationIndent, Math.max(0, width - 1));
                continue;
            }
            pending.add(atom);
            while (indent + measure(pending, metrics, width) > width && pending.size() > 1) {
                int breakAt = bestBreak(pending);
                if (breakAt <= 0) break;
                List<Atom> line = new ArrayList<>(pending.subList(0, breakAt));
                trimEnd(line);
                y = emit(line, indent, y, width, metrics, output, contents);
                pending = new ArrayList<>(pending.subList(breakAt, pending.size()));
                trimStart(pending);
                // Indentation is geometry, not inserted text, so even a one-pixel viewport always advances.
                indent = Math.min(continuationIndent, Math.max(0, width - 1));
            }
        }
        return emit(pending, indent, y, width, metrics, output, contents);
    }

    private static int emit(List<Atom> atoms, int indent, int y, int width, Metrics metrics,
                            List<DocumentLayout.Line> output) {
        return emit(atoms, indent, y, width, metrics, output, new ArrayList<>());
    }

    private static int emit(List<Atom> atoms, int indent, int y, int width, Metrics metrics,
                            List<DocumentLayout.Line> output, List<DocumentLayout.ContentHit> contents) {
        int height = atoms.stream().mapToInt(atom -> atom.content == null
                ? metrics.lineHeight(atom.style) : inlineContentSize(atom.content, metrics, width).height()).max().orElse(9);
        int baseline = y + height;
        List<DocumentLayout.Run> runs = new ArrayList<>();
        int x = indent;
        for (int index = 0; index < atoms.size();) {
            Atom first = atoms.get(index);
            if (first.content != null) {
                ContentSize natural = metrics.contentSize(first.content);
                ContentSize size = inlineContentSize(first.content, metrics, width);
                var bounds = new DocumentLayout.Bounds(x, baseline - size.height(), x + size.width(), baseline);
                contents.add(new DocumentLayout.ContentHit(first.content, bounds, natural.present()));
                x += size.width();
                index++;
                continue;
            }
            StringBuilder text = new StringBuilder(first.text);
            int next = index + 1;
            while (next < atoms.size() && atoms.get(next).content == null
                    && atoms.get(next).style.equals(first.style)) text.append(atoms.get(next++).text);
            int runWidth = metrics.width(text.toString(), first.style);
            int textHeight = metrics.lineHeight(first.style);
            var bounds = new DocumentLayout.Bounds(x, baseline - textHeight,
                    Math.min(width, x + Math.max(1, runWidth)), baseline);
            runs.add(new DocumentLayout.Run(text.toString(), first.style, bounds, baseline));
            x += runWidth;
            index = next;
        }
        output.add(new DocumentLayout.Line(baseline, new DocumentLayout.Bounds(0, y, Math.min(width, Math.max(1, x)), y + height), runs));
        return y + height;
    }

    private static int bestBreak(List<Atom> atoms) {
        String text = text(atoms);
        int[] offsets = offsets(atoms);
        int preferred = -1;
        int legal = -1;
        for (int i = 1; i < atoms.size(); i++) {
            int offset = offsets[i];
            if (atoms.get(i - 1).content != null || atoms.get(i).content != null) legal = i;
            if (MixedTextLayout.legalBoundary(text, offset)) legal = i;
            if (MixedTextLayout.preferredBoundary(text, offset)) preferred = i;
        }
        return preferred > 0 ? preferred : legal;
    }

    private static int measure(List<Atom> atoms, Metrics metrics, int lineWidth) {
        int used = 0;
        for (Atom atom : atoms) used += atom.content == null ? metrics.width(atom.text, atom.style)
                : inlineContentSize(atom.content, metrics, lineWidth).width();
        return used;
    }

    /** Inline content behaves like one glyph and cannot make a line wider than its viewport. */
    private static ContentSize inlineContentSize(RichDocument.Content content, Metrics metrics, int lineWidth) {
        ContentSize natural = metrics.contentSize(content);
        int naturalWidth = Math.max(1, natural.width());
        int naturalHeight = Math.max(1, natural.height());
        int kindWidth = content.kind() == RichDocument.ContentKind.ITEM
                ? INLINE_CONTENT_HEIGHT : INLINE_TEXTURE_WIDTH;
        int maximumWidth = Math.max(1, Math.min(lineWidth, kindWidth));
        double scale = Math.min((double) maximumWidth / naturalWidth,
                (double) INLINE_CONTENT_HEIGHT / naturalHeight);
        return new ContentSize(Math.max(1, (int) Math.round(naturalWidth * scale)),
                Math.max(1, (int) Math.round(naturalHeight * scale)), natural.present());
    }

    private static void trimStart(List<Atom> atoms) {
        while (!atoms.isEmpty() && breakable(atoms.getFirst())) atoms.removeFirst();
    }

    private static void trimEnd(List<Atom> atoms) {
        while (!atoms.isEmpty() && breakable(atoms.getLast())) atoms.removeLast();
    }

    private static boolean breakable(Atom atom) {
        return atom.text.codePointCount(0, atom.text.length()) == 1
                && MixedTextLayout.breakableSpace(atom.text.codePointAt(0));
    }

    private static List<Atom> flatten(List<RichDocument.Inline> inlines, DocumentTextStyle style) {
        List<Atom> result = new ArrayList<>();
        for (RichDocument.Inline inline : inlines) {
            if (inline instanceof RichDocument.Text text) result.addAll(atoms(text.value(), style));
            else if (inline instanceof RichDocument.Code code) result.addAll(atoms(code.value(), style.withCode()));
            else if (inline instanceof RichDocument.Emphasis emphasis) result.addAll(flatten(emphasis.children(), style.withItalic()));
            else if (inline instanceof RichDocument.Strong strong) result.addAll(flatten(strong.children(), style.withBold()));
            else if (inline instanceof RichDocument.StyleSpan span)
                result.addAll(flatten(span.children(), style.withInlineStyle(span.style())));
            else if (inline instanceof RichDocument.Link link) result.addAll(flatten(link.label(), style.withLink(link.destination())));
            else if (inline instanceof RichDocument.LineBreak) result.add(new Atom("\n", style));
            else if (inline instanceof RichDocument.ContentInline content)
                result.add(new Atom("\uFFFC", style, content));
        }
        normalizeBoundarySpacing(result);
        return result;
    }

    private static List<Atom> atoms(String text, DocumentTextStyle style) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
        List<Atom> result = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(normalized);
        for (int start = iterator.first(), end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next())
            result.add(new Atom(normalized.substring(start, end), style));
        normalizeBoundarySpacing(result);
        return result;
    }

    private static void normalizeBoundarySpacing(List<Atom> atoms) {
        for (int index = 1; index + 1 < atoms.size(); index++) {
            Atom atom = atoms.get(index);
            if (!atom.text.equals(" ")) continue;
            int before = atoms.get(index - 1).text.codePointBefore(atoms.get(index - 1).text.length());
            int after = atoms.get(index + 1).text.codePointAt(0);
            String probe = new String(Character.toChars(before)) + " " + new String(Character.toChars(after));
            if (MixedTextLayout.normalizeInlineSpacing(probe).codePointAt(Character.charCount(before)) == 0x00A0)
                atoms.set(index, new Atom("\u00A0", atom.style));
        }
    }

    private static List<DocumentLayout.LinkHit> collectLinks(List<DocumentLayout.Line> lines) {
        List<DocumentLayout.LinkHit> links = new ArrayList<>();
        for (DocumentLayout.Line line : lines)
            for (DocumentLayout.Run run : line.runs())
                if (run.style().link() != null) links.add(new DocumentLayout.LinkHit(run.style().link(), run.bounds()));
        return links;
    }

    private static String text(List<Atom> atoms) {
        StringBuilder result = new StringBuilder();
        atoms.forEach(atom -> result.append(atom.text));
        return result.toString();
    }

    private static int[] offsets(List<Atom> atoms) {
        int[] offsets = new int[atoms.size()];
        int offset = 0;
        for (int i = 0; i < atoms.size(); i++) {
            offsets[i] = offset;
            offset += atoms.get(i).text.length();
        }
        return offsets;
    }

    private record Atom(String text, DocumentTextStyle style, RichDocument.ContentInline content) {
        private Atom(String text, DocumentTextStyle style) { this(text, style, null); }
    }
}
