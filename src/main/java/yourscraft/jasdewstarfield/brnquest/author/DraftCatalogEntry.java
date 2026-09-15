package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;

/** One server-local draft that an authorized editor can open. */
public record DraftCatalogEntry(ResourceLocation bookId, String title, String draftRevision, DraftOrigin origin,
                                String importSource) {
    public DraftCatalogEntry(ResourceLocation bookId, String title, String draftRevision, DraftOrigin origin) {
        this(bookId, title, draftRevision, origin, "");
    }

    public DraftCatalogEntry {
        if (bookId == null) throw new IllegalArgumentException("bookId is required");
        title = title == null ? "" : title;
        if (draftRevision == null || draftRevision.isBlank()) {
            throw new IllegalArgumentException("draftRevision is required");
        }
        if (origin == null) origin = DraftOrigin.UNKNOWN;
        importSource = importSource == null ? "" : importSource;
    }
}
