package yourscraft.jasdewstarfield.brnquest.compat.ftb.text;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/** Deterministically removes FTB runtime syntax by emitting only BRNQuest markdown_v1 constructs. */
public final class BrnQuestMarkdownSerializer {
    public Result serialize(FtbRichTextParser.Result parsed) {
        return serialize(parsed, ignored -> null);
    }

    public Result serialize(FtbRichTextParser.Result parsed, Function<String, String> questTargetResolver) {
        StringBuilder markdown = new StringBuilder();
        List<FtbTextDiagnostic> diagnostics = new ArrayList<>(parsed.diagnostics());
        int previousLine = -1;
        for (FtbTextNode node : parsed.nodes()) {
            if (previousLine >= 0 && node.source().line() > previousLine && !endsWithBlankLine(markdown))
                markdown.append('\n');
            append(node, markdown, diagnostics, questTargetResolver);
            previousLine = Math.max(previousLine, node.source().line());
        }
        return new Result(markdown.toString(), diagnostics);
    }

    private void append(FtbTextNode node, StringBuilder output, List<FtbTextDiagnostic> diagnostics,
                        Function<String, String> questTargetResolver) {
        if (node instanceof FtbTextNode.PageBreak pageBreak) {
            ensureBlankLine(output);
            diagnostics.add(diagnostic(FtbTextDiagnostic.Severity.INFO, "BQF-TEXT-PAGEBREAK-FLATTENED",
                    pageBreak.source(), "FTB page boundary was flattened to a paragraph boundary"));
        } else if (node instanceof FtbTextNode.Image image) {
            if (!validResourceId(image.resourceId())) {
                output.append(escape("{image:" + image.resourceId() + "}"));
                diagnostics.add(diagnostic(FtbTextDiagnostic.Severity.ERROR, "BQF-TEXT-IMAGE-ID",
                        image.source(), "FTB image does not contain a valid namespaced resource ID"));
                return;
            }
            output.append("![").append(escapeLabel(image.alt())).append("](texture:")
                    .append(image.resourceId().toLowerCase(Locale.ROOT)).append(')');
            if (image.width() != 100 || image.height() != 100 || !image.align().equalsIgnoreCase("center"))
                diagnostics.add(diagnostic(FtbTextDiagnostic.Severity.WARN, "BQF-TEXT-IMAGE-LAYOUT",
                        image.source(), "Explicit FTB image size/alignment was replaced by BRNQuest intrinsic fitting"));
        } else if (node instanceof FtbTextNode.Unknown unknown) {
            output.append(escape(unknown.sourceText()));
        } else if (node instanceof FtbTextNode.Text text) {
            appendText(text, output, diagnostics, questTargetResolver);
        }
    }

    private void appendText(FtbTextNode.Text node, StringBuilder output,
                            List<FtbTextDiagnostic> diagnostics,
                            Function<String, String> questTargetResolver) {
        String label = escape(node.value());
        if (node.style().bold() && node.style().italic()) label = "***" + label + "***";
        else if (node.style().bold()) label = "**" + label + "**";
        else if (node.style().italic()) label = "*" + label + "*";

        List<String> extended = extendedStyles(node.style(), node.source(), diagnostics);
        if (node.action().kind() == FtbTextNode.Action.Kind.OPEN_URL) {
            if (!extended.isEmpty()) diagnostics.add(diagnostic(FtbTextDiagnostic.Severity.WARN,
                    "BQF-TEXT-LINK-STYLE", node.source(),
                    "Extended color/decorations were dropped because markdown_v1 does not nest style and URL links"));
            output.append('[').append(label).append("](<").append(normalizedUrl(node.action().value())).append(">)");
            return;
        }
        if (node.action().kind() == FtbTextNode.Action.Kind.CHANGE_PAGE) {
            String target = questTargetResolver.apply(node.action().value());
            if (target != null) {
                if (!extended.isEmpty()) diagnostics.add(diagnostic(FtbTextDiagnostic.Severity.WARN,
                        "BQF-TEXT-LINK-STYLE", node.source(),
                        "Extended color/decorations were dropped because markdown_v1 does not nest style and quest links"));
                output.append('[').append(label).append("](brnquest:quest/").append(target).append(')');
                if (node.action().value().contains("/"))
                    diagnostics.add(diagnostic(FtbTextDiagnostic.Severity.INFO, "BQF-TEXT-SUBPAGE-FLATTENED",
                            node.source(), "FTB quest subpage was flattened to its single BRNQuest details view"));
                return;
            }
            diagnostics.add(diagnostic(FtbTextDiagnostic.Severity.WARN, "BQF-TEXT-CHANGE-PAGE",
                    node.source(), "FTB object/page target requires a stable BRNQuest quest mapping"));
        }
        if (!extended.isEmpty())
            label = "[" + label + "](brnquest:style/" + String.join("+", extended) + ")";
        output.append(label);
    }

    private List<String> extendedStyles(FtbTextStyle style, FtbTextSource source,
                                        List<FtbTextDiagnostic> diagnostics) {
        List<String> result = new ArrayList<>();
        if (style.rainbow()) diagnostics.add(diagnostic(FtbTextDiagnostic.Severity.WARN,
                "BQF-TEXT-RAINBOW", source, "Dynamic FTB rainbow color was removed while preserving its text"));
        else if (style.color() != null) result.add("color/" + String.format(Locale.ROOT, "%06x", style.color()));
        if (style.underlined()) result.add("underline");
        if (style.strikethrough()) result.add("strikethrough");
        if (style.obfuscated()) result.add("obfuscated");
        return result;
    }

    private static String escape(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '\\' || current == '*' || current == '_' || current == '[' || current == ']'
                    || current == '(' || current == ')' || current == '`') result.append('\\');
            result.append(current);
        }
        return result.toString();
    }

    private static String escapeLabel(String value) {
        return escape(value).replace("!", "\\!");
    }

    private static String normalizedUrl(String raw) {
        try { return new URI(raw).normalize().toASCIIString(); }
        catch (URISyntaxException ignored) { return raw; }
    }

    private static boolean validResourceId(String value) {
        return value != null && value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+");
    }

    private static void ensureBlankLine(StringBuilder output) {
        if (output.isEmpty()) return;
        if (output.charAt(output.length() - 1) != '\n') output.append('\n');
        if (output.length() < 2 || output.charAt(output.length() - 2) != '\n') output.append('\n');
    }

    private static boolean endsWithBlankLine(StringBuilder output) {
        return output.length() >= 2 && output.charAt(output.length() - 1) == '\n'
                && output.charAt(output.length() - 2) == '\n';
    }

    private static FtbTextDiagnostic diagnostic(FtbTextDiagnostic.Severity severity, String code,
                                                FtbTextSource source, String message) {
        return new FtbTextDiagnostic(severity, code, source, message);
    }

    public record Result(String markdown, List<FtbTextDiagnostic> diagnostics) {
        public Result {
            markdown = Objects.requireNonNullElse(markdown, "");
            diagnostics = List.copyOf(diagnostics);
        }
    }
}
