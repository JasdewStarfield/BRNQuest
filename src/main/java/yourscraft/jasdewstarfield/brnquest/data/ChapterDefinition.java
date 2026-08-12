package yourscraft.jasdewstarfield.brnquest.data;

import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Immutable chapter and its ordered quest definitions. */
public record ChapterDefinition(ResourceLocation bookId, ResourceLocation id, ResourceLocation groupId,
                                String title, String icon, int order, List<QuestDefinition> quests) {
    public ChapterDefinition { quests = List.copyOf(quests); }
}
