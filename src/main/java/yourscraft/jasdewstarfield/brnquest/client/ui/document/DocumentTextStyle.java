package yourscraft.jasdewstarfield.brnquest.client.ui.document;

import java.net.URI;

import yourscraft.jasdewstarfield.brnquest.data.text.RichDocument;

/** Renderer-neutral style state inherited while flattening rich inline nodes. */
public record DocumentTextStyle(boolean bold, boolean italic, boolean underlined, boolean strikethrough,
                                boolean obfuscated, Integer color, boolean code, int headingLevel, URI link) {
    public static final DocumentTextStyle PLAIN = new DocumentTextStyle(
            false, false, false, false, false, null, false, 0, null);

    public DocumentTextStyle withBold() { return copy(true, italic, underlined, strikethrough, obfuscated, color, code, headingLevel, link); }
    public DocumentTextStyle withItalic() { return copy(bold, true, underlined, strikethrough, obfuscated, color, code, headingLevel, link); }
    public DocumentTextStyle withCode() { return copy(bold, italic, underlined, strikethrough, obfuscated, color, true, headingLevel, link); }
    public DocumentTextStyle withHeading(int level) { return copy(bold, italic, underlined, strikethrough, obfuscated, color, code, level, link); }
    public DocumentTextStyle withLink(URI destination) { return copy(bold, italic, underlined, strikethrough, obfuscated, color, code, headingLevel, destination); }

    /** Style spans are additive so Markdown emphasis can safely wrap color and decoration extensions. */
    public DocumentTextStyle withInlineStyle(RichDocument.InlineStyle addition) {
        return copy(bold || addition.bold(), italic || addition.italic(), underlined || addition.underlined(),
                strikethrough || addition.strikethrough(), obfuscated || addition.obfuscated(),
                addition.color() == null ? color : addition.color(), code, headingLevel, link);
    }

    private static DocumentTextStyle copy(boolean bold, boolean italic, boolean underlined,
                                          boolean strikethrough, boolean obfuscated, Integer color,
                                          boolean code, int headingLevel, URI link) {
        return new DocumentTextStyle(bold, italic, underlined, strikethrough, obfuscated,
                color, code, headingLevel, link);
    }

    public float scale() {
        return switch (headingLevel) {
            case 1 -> 1.35F;
            case 2 -> 1.2F;
            case 3 -> 1.1F;
            default -> 1.0F;
        };
    }
}
