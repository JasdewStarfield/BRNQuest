package yourscraft.jasdewstarfield.brnquest.data.text;

import com.mojang.serialization.Codec;

import java.util.Locale;
import java.util.Objects;

/**
 * Versioned storage format for author-controlled document text.
 * Unknown values remain round-trippable so newer books can safely fall back to literal text.
 */
public record DocumentFormat(String serializedName) {
    public static final DocumentFormat PLAIN = new DocumentFormat("plain");
    public static final DocumentFormat MARKDOWN_V1 = new DocumentFormat("markdown_v1");
    public static final Codec<DocumentFormat> CODEC = Codec.STRING.xmap(DocumentFormat::parse, DocumentFormat::serializedName);

    public DocumentFormat {
        serializedName = Objects.requireNonNull(serializedName, "serializedName").strip().toLowerCase(Locale.ROOT);
        if (serializedName.isEmpty()) throw new IllegalArgumentException("Document format cannot be empty");
    }

    /** Disk and synchronization reads preserve unknown names instead of guessing newer semantics. */
    public static DocumentFormat parse(String value) {
        return value == null || value.isBlank() ? PLAIN : new DocumentFormat(value);
    }

    /** Author mutations may select only formats whose semantics this version can validate. */
    public boolean editable() {
        return equals(PLAIN) || equals(MARKDOWN_V1);
    }
}
