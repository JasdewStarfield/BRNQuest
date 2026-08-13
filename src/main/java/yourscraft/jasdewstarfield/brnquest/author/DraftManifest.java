package yourscraft.jasdewstarfield.brnquest.author;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;

/** Deterministic disk metadata; actor and wall-clock data belong in audit logs instead. */
public record DraftManifest(int formatVersion, ResourceLocation bookId, String baseRevision, String draftRevision) {
    public static final int CURRENT_FORMAT = 1;

    public DraftManifest {
        if (formatVersion != CURRENT_FORMAT) throw new IllegalArgumentException("Unsupported draft format " + formatVersion);
        if (bookId == null) throw new IllegalArgumentException("bookId is required");
        baseRevision = baseRevision == null ? "" : baseRevision;
        if (draftRevision == null || draftRevision.isBlank()) throw new IllegalArgumentException("draftRevision is required");
    }

    String encode() {
        // Write keys explicitly so repeated saves remain byte-for-byte deterministic.
        return "{\n"
                + "  \"format_version\": " + formatVersion + ",\n"
                + "  \"book_id\": \"" + bookId + "\",\n"
                + "  \"base_revision\": \"" + baseRevision + "\",\n"
                + "  \"draft_revision\": \"" + draftRevision + "\"\n"
                + "}\n";
    }

    static DraftManifest decode(JsonObject value) {
        ResourceLocation bookId = ResourceLocation.tryParse(value.get("book_id").getAsString());
        if (bookId == null) throw new JsonParseException("Invalid draft book ID");
        return new DraftManifest(value.get("format_version").getAsInt(), bookId,
                value.get("base_revision").getAsString(), value.get("draft_revision").getAsString());
    }
}
