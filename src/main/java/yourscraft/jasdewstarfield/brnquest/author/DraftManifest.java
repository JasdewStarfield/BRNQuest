package yourscraft.jasdewstarfield.brnquest.author;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;

/** Deterministic disk metadata; actor and wall-clock data belong in audit logs instead. */
public record DraftManifest(int formatVersion, ResourceLocation bookId, DraftOrigin origin,
                            String baseRevision, String draftRevision) {
    public static final int CURRENT_FORMAT = 2;

    public DraftManifest {
        if (formatVersion < 1 || formatVersion > CURRENT_FORMAT) throw new IllegalArgumentException("Unsupported draft format " + formatVersion);
        if (bookId == null) throw new IllegalArgumentException("bookId is required");
        if (origin == null) origin = DraftOrigin.UNKNOWN;
        baseRevision = baseRevision == null ? "" : baseRevision;
        if (draftRevision == null || draftRevision.isBlank()) throw new IllegalArgumentException("draftRevision is required");
    }

    String encode() {
        // Write keys explicitly so repeated saves remain byte-for-byte deterministic.
        return "{\n"
                + "  \"format_version\": " + formatVersion + ",\n"
                + "  \"book_id\": \"" + bookId + "\",\n"
                + "  \"origin\": \"" + origin + "\",\n"
                + "  \"base_revision\": \"" + baseRevision + "\",\n"
                + "  \"draft_revision\": \"" + draftRevision + "\"\n"
                + "}\n";
    }

    static DraftManifest decode(JsonObject value) {
        ResourceLocation bookId = ResourceLocation.tryParse(value.get("book_id").getAsString());
        if (bookId == null) throw new JsonParseException("Invalid draft book ID");
        int format = value.get("format_version").getAsInt();
        DraftOrigin origin = value.has("origin") ? DraftOrigin.valueOf(value.get("origin").getAsString()) : DraftOrigin.UNKNOWN;
        return new DraftManifest(format, bookId, origin,
                value.get("base_revision").getAsString(), value.get("draft_revision").getAsString());
    }
}
