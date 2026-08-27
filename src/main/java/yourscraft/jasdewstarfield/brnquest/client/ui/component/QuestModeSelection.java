package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Resolves a detail selection when the same screen changes between runtime and draft books. */
public final class QuestModeSelection {
    private QuestModeSelection() {}

    public static Result resolve(ResourceLocation preferredId, boolean detailsOpen,
                                 Set<ResourceLocation> targetQuestIds) {
        if (preferredId != null && targetQuestIds != null && targetQuestIds.contains(preferredId)) {
            return new Result(preferredId, detailsOpen);
        }
        // A stale selection must not expose the other mode's historical detail page.
        return new Result(null, false);
    }

    public record Result(ResourceLocation selectedId, boolean detailsOpen) {}
}
