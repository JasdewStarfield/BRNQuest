package yourscraft.jasdewstarfield.brnquest.api;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/** Immutable public summary of the active task book and its content revision. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record QuestBookView(ResourceLocation id, int schemaVersion, String title, String revision,
                            List<ResourceLocation> chapterGroupIds, List<ResourceLocation> chapterIds,
                            List<ResourceLocation> questIds, Map<String, ResourceLocation> legacyIds) {
    public QuestBookView {
        chapterGroupIds = List.copyOf(chapterGroupIds);
        chapterIds = List.copyOf(chapterIds);
        questIds = List.copyOf(questIds);
        legacyIds = Map.copyOf(legacyIds);
    }
}
