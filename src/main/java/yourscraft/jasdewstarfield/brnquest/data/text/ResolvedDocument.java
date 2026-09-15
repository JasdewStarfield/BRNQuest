package yourscraft.jasdewstarfield.brnquest.data.text;

import yourscraft.jasdewstarfield.brnquest.data.BookLocalization;

import java.util.Objects;

/** Text, format, and source locale resolved as one value so fallback cannot mix their origins. */
public record ResolvedDocument(String text, DocumentFormat format, String sourceLocale) {
    public ResolvedDocument {
        text = Objects.requireNonNullElse(text, "");
        format = format == null ? DocumentFormat.PLAIN : format;
        sourceLocale = BookLocalization.normalizeLocale(sourceLocale);
    }
}
