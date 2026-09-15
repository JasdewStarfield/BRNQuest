package yourscraft.jasdewstarfield.brnquest.client.ui.document;

import java.net.URI;

/** Renderer-neutral style state inherited while flattening rich inline nodes. */
public record DocumentTextStyle(boolean bold, boolean italic, boolean code, int headingLevel, URI link) {
    public static final DocumentTextStyle PLAIN = new DocumentTextStyle(false, false, false, 0, null);

    public DocumentTextStyle withBold() { return new DocumentTextStyle(true, italic, code, headingLevel, link); }
    public DocumentTextStyle withItalic() { return new DocumentTextStyle(bold, true, code, headingLevel, link); }
    public DocumentTextStyle withCode() { return new DocumentTextStyle(bold, italic, true, headingLevel, link); }
    public DocumentTextStyle withHeading(int level) { return new DocumentTextStyle(bold, italic, code, level, link); }
    public DocumentTextStyle withLink(URI destination) { return new DocumentTextStyle(bold, italic, code, headingLevel, destination); }

    public float scale() {
        return switch (headingLevel) {
            case 1 -> 1.35F;
            case 2 -> 1.2F;
            case 3 -> 1.1F;
            default -> 1.0F;
        };
    }
}
