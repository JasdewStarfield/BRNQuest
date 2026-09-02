package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.function.Predicate;

/** Resolves a detail selection when the same screen changes between runtime and draft books. */
public final class QuestModeSelection {
    private QuestModeSelection() {}

    public static Result resolve(ResourceLocation preferredId, boolean detailsOpen,
                                 Set<ResourceLocation> targetQuestIds,
                                 Predicate<ResourceLocation> selectable) {
        if (preferredId != null && targetQuestIds != null && targetQuestIds.contains(preferredId)
                && selectable != null && selectable.test(preferredId)) {
            return new Result(preferredId, detailsOpen);
        }
        // Deleted draft quests and runtime-hidden quests must not leave an empty detail drawer behind.
        return new Result(null, false);
    }

    public record Result(ResourceLocation selectedId, boolean detailsOpen) {}
}
