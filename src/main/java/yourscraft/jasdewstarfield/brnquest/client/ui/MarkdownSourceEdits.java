package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.data.text.RichDocument;

/** Pure selection edits used by the Markdown toolbar and covered without a running client. */
final class MarkdownSourceEdits {
    enum Tool { HEADING, BOLD, ITALIC, LIST, CODE, LINK }
    record Result(String text, int selectionStart, int selectionEnd) {}

    private MarkdownSourceEdits() {}

    static Result apply(String source, int first, int second, Tool tool, String placeholder) {
        String text = source == null ? "" : source;
        int start = safeBoundary(text, Math.min(first, second));
        int end = safeBoundary(text, Math.max(first, second));
        String selected = text.substring(start, end);
        String content = selected.isEmpty() ? placeholder : selected;
        return switch (tool) {
            case BOLD -> inline(text, start, end, "**", content, "**");
            case ITALIC -> inline(text, start, end, "*", content, "*");
            case CODE -> inline(text, start, end, "`", content, "`");
            case LINK -> inline(text, start, end, "[", content, "](https://example.com)");
            case HEADING -> prefixLines(text, start, end, "## ", content);
            case LIST -> prefixLines(text, start, end, "- ", content);
        };
    }

    static Result insertContent(String source, int first, int second, RichDocument.ContentKind kind,
                                String id, String placeholder) {
        String text = source == null ? "" : source;
        int start = safeBoundary(text, Math.min(first, second));
        int end = safeBoundary(text, Math.max(first, second));
        String selected = text.substring(start, end);
        String alt = selected.isEmpty() ? placeholder : selected.replace('\n', ' ').replace('\r', ' ');
        String scheme = kind == RichDocument.ContentKind.TEXTURE ? "texture:" : "item:";
        String content = "![" + alt + "](" + scheme + id + ")";
        String changed = text.substring(0, start) + content + text.substring(end);
        int selectionStart = start + 2;
        return new Result(changed, selectionStart, selectionStart + alt.length());
    }

    private static Result inline(String text, int start, int end, String before, String content, String after) {
        String replacement = before + content + after;
        String changed = text.substring(0, start) + replacement + text.substring(end);
        return new Result(changed, start + before.length(), start + before.length() + content.length());
    }

    private static Result prefixLines(String text, int start, int end, String prefix, String placeholder) {
        int lineStart = text.lastIndexOf('\n', Math.max(0, start - 1)) + 1;
        String content = start == end ? placeholder : text.substring(lineStart, end);
        String replacement = prefix + content.replace("\n", "\n" + prefix);
        String changed = text.substring(0, lineStart) + replacement + text.substring(end);
        int selectionStart = lineStart + prefix.length();
        return new Result(changed, selectionStart, selectionStart + replacement.length() - prefix.length());
    }

    /** Avoid toolbar operations splitting a UTF-16 surrogate pair at a cursor boundary. */
    private static int safeBoundary(String text, int raw) {
        int index = Math.max(0, Math.min(raw, text.length()));
        if (index > 0 && index < text.length() && Character.isHighSurrogate(text.charAt(index - 1))
                && Character.isLowSurrogate(text.charAt(index))) return index - 1;
        return index;
    }
}
