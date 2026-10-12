package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.List;
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

    public record Result(ResourceLocation selectedId, boolean detailsOpen) {
        /** A historical selection behind a closed drawer must not redirect chapter navigation. */
        public boolean followSelection() { return detailsOpen && selectedId != null; }
    }

    /** Preserve the browsed chapter across snapshots with different ordering, without moving the camera. */
    public static int chapterIndex(ResourceLocation preferredChapter, List<ResourceLocation> chapters, int fallback) {
        int preferred = preferredChapter == null ? -1 : chapters.indexOf(preferredChapter);
        return preferred >= 0 ? preferred : Math.max(0, Math.min(fallback, chapters.size() - 1));
    }
}
