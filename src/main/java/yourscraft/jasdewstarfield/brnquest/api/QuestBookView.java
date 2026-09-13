package yourscraft.jasdewstarfield.brnquest.api;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/** Immutable public summary of the active task book and its content revision. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record QuestBookView(ResourceLocation id, int schemaVersion, String title, String revision,
                            List<ResourceLocation> chapterGroupIds, List<ResourceLocation> chapterIds,
                            List<ResourceLocation> questIds, Map<String, ResourceLocation> legacyIds, java.util.Map<String, String> questDefaults, Map<String, String> settings) {
    /** Compatibility overload for the first creation-template API. */
    public QuestBookView(ResourceLocation id, int schemaVersion, String title, String revision,
            List<ResourceLocation> groups, List<ResourceLocation> chapters, List<ResourceLocation> quests,
            Map<String, ResourceLocation> legacyIds, Map<String, String> defaults) {
        this(id, schemaVersion, title, revision, groups, chapters, quests, legacyIds, defaults, Map.of());
    }
    /** Compatibility constructor for callers predating creation templates. */
    public QuestBookView(ResourceLocation id, int schemaVersion, String title, String revision,
                            List<ResourceLocation> chapterGroupIds, List<ResourceLocation> chapterIds,
                            List<ResourceLocation> questIds, Map<String, ResourceLocation> legacyIds) {
        this(id, schemaVersion, title, revision, chapterGroupIds, chapterIds, questIds, legacyIds, java.util.Map.of());
    }

    public QuestBookView {
        chapterGroupIds = List.copyOf(chapterGroupIds);
        chapterIds = List.copyOf(chapterIds);
        questIds = List.copyOf(questIds);
        questDefaults = java.util.Map.copyOf(questDefaults);
        legacyIds = Map.copyOf(legacyIds);
        settings = Map.copyOf(settings);
    }
}
