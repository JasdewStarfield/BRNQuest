package yourscraft.jasdewstarfield.brnquest.data;

import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Map;

/** Immutable chapter and its ordered quest definitions. */
public record ChapterDefinition(ResourceLocation bookId, ResourceLocation id, ResourceLocation groupId,
                                String title, String icon, int order, List<QuestDefinition> quests,
                                Map<String, String> extensions) {
    public ChapterDefinition(ResourceLocation bookId, ResourceLocation id, ResourceLocation groupId,
                             String title, String icon, int order, List<QuestDefinition> quests) {
        this(bookId, id, groupId, title, icon, order, quests, Map.of());
    }

    public ChapterDefinition {
        quests = List.copyOf(quests);
        extensions = Map.copyOf(extensions);
    }
}
