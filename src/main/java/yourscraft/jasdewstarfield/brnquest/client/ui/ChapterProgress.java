package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;

import java.util.function.Function;
import java.util.function.Predicate;

/** Read-only chapter summary: hidden quests never contribute to either count. */
record ChapterProgress(int completed, int total) {
    static ChapterProgress count(ChapterDefinition chapter, Predicate<ResourceLocation> visible,
                                 Function<ResourceLocation, QuestStatus> status) {
        int completed = 0, total = 0;
        for (var quest : chapter.quests()) {
            if (!visible.test(quest.id())) continue;
            total++;
            var current = status.apply(quest.id());
            // Reward collection does not change completion; repeating quests use their current cycle.
            if (current == QuestStatus.COMPLETED || current == QuestStatus.REWARD_CLAIMED) completed++;
        }
        return new ChapterProgress(completed, total);
    }

    int percent() { return total == 0 ? 0 : (int) ((long) completed * 100 / total); }
}
