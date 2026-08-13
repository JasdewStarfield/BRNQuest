package yourscraft.jasdewstarfield.brnquest.api;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Immutable public chapter-group projection in author-defined order. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record ChapterGroupView(ResourceLocation bookId, ResourceLocation id, String title, int order,
                               List<ResourceLocation> chapterIds) {
    public ChapterGroupView {
        chapterIds = List.copyOf(chapterIds);
    }
}
