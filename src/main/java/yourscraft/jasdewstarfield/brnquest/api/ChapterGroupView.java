package yourscraft.jasdewstarfield.brnquest.api;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/** Immutable public chapter-group projection in author-defined order. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record ChapterGroupView(ResourceLocation bookId, ResourceLocation id, String title, int order,
                               List<ResourceLocation> chapterIds, String icon, String description, Map<String, String> extensions) {
    /** Binary-compatible constructor for addons compiled against the original group projection. */
    public ChapterGroupView(ResourceLocation bookId, ResourceLocation id, String title, int order,
                            List<ResourceLocation> chapterIds) {
        this(bookId, id, title, order, chapterIds, "", "", Map.of());
    }
    public ChapterGroupView {
        chapterIds = List.copyOf(chapterIds);
        extensions = Map.copyOf(extensions);
    }
}
