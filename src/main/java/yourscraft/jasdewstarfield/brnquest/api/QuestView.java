package yourscraft.jasdewstarfield.brnquest.api;

import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Immutable definition projection that never exposes mutable runtime state. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record QuestView(ResourceLocation bookId, ResourceLocation id, ResourceLocation chapterId,
                        String title, String subtitle, String description, String icon,
                        double x, double y, List<ResourceLocation> dependencies,
                        List<TaskView> tasks, List<RewardView> rewards, String legacyId,
                        QuestBehaviorView behavior) {
    public QuestView {
        dependencies = List.copyOf(dependencies);
        tasks = List.copyOf(tasks);
        rewards = List.copyOf(rewards);
    }
}
