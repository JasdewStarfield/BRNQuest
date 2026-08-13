package yourscraft.jasdewstarfield.brnquest.api;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Immutable public chapter projection in author-defined quest order. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record ChapterView(ResourceLocation bookId, ResourceLocation id, ResourceLocation groupId,
                          String title, String icon, int order, List<ResourceLocation> questIds) {
    public ChapterView {
        questIds = List.copyOf(questIds);
    }
}
