package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.data.text.RichDocument;

/** Pure selection edits used by the Markdown toolbar and covered without a running client. */
final class MarkdownSourceEdits {
    enum Tool { HEADING, BOLD, ITALIC, LIST, CODE, LINK, QUEST_LINK, UNDERLINE, STRIKETHROUGH, OBFUSCATED, COLOR }
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
            case QUEST_LINK -> throw new IllegalArgumentException("Quest link requires a selected target ID");
            case HEADING -> prefixLines(text, start, end, "## ", content);
            case LIST -> prefixLines(text, start, end, "- ", content);
            case UNDERLINE -> style(text, start, end, content, "underline");
            case STRIKETHROUGH -> style(text, start, end, content, "strikethrough");
            case OBFUSCATED -> style(text, start, end, content, "obfuscated");
            case COLOR -> throw new IllegalArgumentException("Color requires a selected palette or RGB value");
        };
    }

    static Result applyColor(String source, int first, int second, String color, String placeholder) {
        String normalized = EditorMarkdownColorScreen.normalizeColor(color);
        if (normalized == null) throw new IllegalArgumentException("Unsupported Markdown color: " + color);
        String text = source == null ? "" : source;
        int start = safeBoundary(text, Math.min(first, second));
        int end = safeBoundary(text, Math.max(first, second));
        String selected = text.substring(start, end);
        return style(text, start, end, selected.isEmpty() ? placeholder : selected, "color/" + normalized);
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

    /** Inserts the stable BRNQuest target while keeping the visible label selected for immediate editing. */
    static Result insertQuestLink(String source, int first, int second, String questId, String placeholder) {
        if (questId == null || !questId.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
            throw new IllegalArgumentException("Quest link requires a namespaced target ID");
        String text = source == null ? "" : source;
        int start = safeBoundary(text, Math.min(first, second));
        int end = safeBoundary(text, Math.max(first, second));
        String selected = text.substring(start, end);
        String label = (selected.isEmpty() ? placeholder : selected).replace('\n', ' ').replace('\r', ' ');
        return inline(text, start, end, "[", label, "](brnquest:quest/" + questId + ")");
    }

    private static Result inline(String text, int start, int end, String before, String content, String after) {
        String replacement = before + content + after;
        String changed = text.substring(0, start) + replacement + text.substring(end);
        return new Result(changed, start + before.length(), start + before.length() + content.length());
    }

    private static Result style(String text, int start, int end, String content, String target) {
        return inline(text, start, end, "[", content, "](brnquest:style/" + target + ")");
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
