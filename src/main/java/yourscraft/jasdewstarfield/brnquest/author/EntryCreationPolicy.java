package yourscraft.jasdewstarfield.brnquest.author;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry;

/** Shared preview/server creation rules; copying and updating never pass through these defaults. */
public final class EntryCreationPolicy {
    private EntryCreationPolicy() {}
    public static Map<String, String> taskConfig(QuestBookDefinition book, ResourceLocation questId,
                                                ResourceLocation typeId, Map<String, String> explicit) {
        var chapter = book.chapters().stream().filter(c -> c.quests().stream().anyMatch(q -> q.id().equals(questId)))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Quest not found"));
        var type = TaskTypeRegistry.get(typeId);
        return type == null ? explicit : Map.copyOf(type.creationConfig(explicit, book.settings().taskDefaults(chapter)));
    }
    public static String rewardPolicy(QuestBookDefinition book, ResourceLocation typeId, Map<String, String> config) {
        var type = RewardTypeRegistry.get(typeId);
        // Interactive types always start with manual claiming; authors cannot accidentally auto-select a choice.
        return type != null && type.requiresManualClaim(config) ? "manual" : book.settings().rewardClaimPolicy();
    }
}
