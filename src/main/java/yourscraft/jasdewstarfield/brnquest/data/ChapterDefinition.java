package yourscraft.jasdewstarfield.brnquest.data;

import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Map;

/** Immutable chapter and its ordered quest definitions. */
public record ChapterDefinition(ResourceLocation bookId, ResourceLocation id, ResourceLocation groupId,
                                String title, String icon, int order, List<QuestDefinition> quests,
                                Map<String, String> extensions, QuestCreationDefaults questDefaults, Boolean consumeItems, ResourceLocation autofocusQuestId, boolean defaultHideDependencyLines) {
    /** Older callers retain visible dependency lines by default. */
    public ChapterDefinition(ResourceLocation bookId, ResourceLocation id, ResourceLocation groupId,
            String title, String icon, int order, List<QuestDefinition> quests, Map<String, String> extensions,
            QuestCreationDefaults defaults, Boolean consumeItems, ResourceLocation autofocusQuestId) {
        this(bookId, id, groupId, title, icon, order, quests, extensions, defaults, consumeItems, autofocusQuestId, false);
    }
    /** An absent target keeps the ordinary chapter viewport behavior. */
    public ChapterDefinition(ResourceLocation bookId, ResourceLocation id, ResourceLocation groupId,
            String title, String icon, int order, List<QuestDefinition> quests, Map<String, String> extensions, QuestCreationDefaults questDefaults, Boolean consumeItems) {
        this(bookId, id, groupId, title, icon, order, quests, extensions, questDefaults, consumeItems, null);
    }
    /** Null consumption means new item objectives inherit the book policy. */
    public ChapterDefinition(ResourceLocation bookId, ResourceLocation id, ResourceLocation groupId,
            String title, String icon, int order, List<QuestDefinition> quests,
            Map<String, String> extensions, QuestCreationDefaults defaults) {
        this(bookId, id, groupId, title, icon, order, quests, extensions, defaults, null);
    }
    /** Compatibility constructor: historical chapters inherit the book creation template. */
    public ChapterDefinition(ResourceLocation bookId, ResourceLocation id, ResourceLocation groupId,
            String title, String icon, int order, List<QuestDefinition> quests, Map<String, String> extensions) {
        this(bookId, id, groupId, title, icon, order, quests, extensions, QuestCreationDefaults.EMPTY);
    }

    public ChapterDefinition(ResourceLocation bookId, ResourceLocation id, ResourceLocation groupId,
                             String title, String icon, int order, List<QuestDefinition> quests) {
        this(bookId, id, groupId, title, icon, order, quests, Map.of());
    }

    public ChapterDefinition {
        quests = List.copyOf(quests);
        extensions = Map.copyOf(extensions);
        questDefaults = questDefaults == null ? QuestCreationDefaults.EMPTY : questDefaults;
    }
}
