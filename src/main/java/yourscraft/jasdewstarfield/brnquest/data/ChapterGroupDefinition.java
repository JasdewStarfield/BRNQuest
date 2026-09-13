package yourscraft.jasdewstarfield.brnquest.data;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;

/** Immutable navigation metadata; groups organize chapters and do not supply task defaults. */
public record ChapterGroupDefinition(ResourceLocation bookId, ResourceLocation id, String title, int order,
                                     String icon, String description, Map<String, String> extensions) {
    /** Keeps the original construction contract for existing callers and old books. */
    public ChapterGroupDefinition(ResourceLocation bookId, ResourceLocation id, String title, int order) {
        this(bookId, id, title, order, "", "", Map.of());
    }

    public ChapterGroupDefinition {
        icon = icon == null ? "" : icon;
        description = description == null ? "" : description;
        extensions = Map.copyOf(extensions);
    }
}
