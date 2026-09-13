package yourscraft.jasdewstarfield.brnquest.api;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Immutable public chapter projection in author-defined quest order. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record ChapterView(ResourceLocation bookId, ResourceLocation id, ResourceLocation groupId,
                          String title, String icon, int order, List<ResourceLocation> questIds, java.util.Map<String, String> questDefaults, Boolean consumeItems, ResourceLocation autofocusQuestId, boolean defaultHideDependencyLines) {
    /** Compatibility constructor for chapter views predating dependency-line defaults. */
    public ChapterView(ResourceLocation bookId, ResourceLocation id, ResourceLocation groupId, String title, String icon,
            int order, List<ResourceLocation> questIds, java.util.Map<String, String> defaults, Boolean consumeItems, ResourceLocation autofocusQuestId) {
        this(bookId, id, groupId, title, icon, order, questIds, defaults, consumeItems, autofocusQuestId, false);
    }
    /** An absent target keeps the ordinary chapter viewport behavior. */
    public ChapterView(ResourceLocation bookId, ResourceLocation id, ResourceLocation groupId,
            String title, String icon, int order, List<ResourceLocation> questIds, java.util.Map<String, String> questDefaults, Boolean consumeItems) {
        this(bookId, id, groupId, title, icon, order, questIds, questDefaults, consumeItems, null);
    }
    /** Null consumption inherits the book default for newly created item objectives. */
    public ChapterView(ResourceLocation bookId, ResourceLocation id, ResourceLocation groupId,
            String title, String icon, int order, List<ResourceLocation> quests, java.util.Map<String, String> defaults) {
        this(bookId, id, groupId, title, icon, order, quests, defaults, null);
    }
    /** Compatibility constructor for callers predating creation templates. */
    public ChapterView(ResourceLocation bookId, ResourceLocation id, ResourceLocation groupId,
                          String title, String icon, int order, List<ResourceLocation> questIds) {
        this(bookId, id, groupId, title, icon, order, questIds, java.util.Map.of());
    }

    public ChapterView {
        questIds = List.copyOf(questIds);
        questDefaults = java.util.Map.copyOf(questDefaults);
    }
}
