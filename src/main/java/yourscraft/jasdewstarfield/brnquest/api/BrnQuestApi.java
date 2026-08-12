package yourscraft.jasdewstarfield.brnquest.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.data.QuestIds;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;
import yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;

import java.util.Optional;

/** Small public server API; all returned views are immutable. */
public final class BrnQuestApi {
    private BrnQuestApi() {}

    public static boolean completeQuest(ServerPlayer player, String questId) { return completeQuestResult(player, questId).success(); }
    public static OperationResult completeQuestResult(ServerPlayer player, String questId) { return ProgressEngine.get().forceComplete(player, resolve(questId)); }
    public static boolean isQuestCompleted(ServerPlayer player, String questId) { return getProgress(player, questId).map(v -> v.status().ordinal() >= yourscraft.jasdewstarfield.brnquest.progress.QuestStatus.COMPLETED.ordinal()).orElse(false); }
    public static boolean addTaskProgress(ServerPlayer player, String taskId, long amount) { return ProgressEngine.get().addTaskProgress(player, resolve(taskId), amount).success(); }
    public static boolean claimReward(ServerPlayer player, String rewardId) { return ProgressEngine.get().claim(player, resolve(rewardId)).success(); }

    public static boolean openQuestScreen(ServerPlayer player, String questId) {
        if (player == null || player.getServer() == null) return false;
        BrnQuestNetwork.syncAll(player, false);
        BrnQuestNetwork.openScreen(player, questId == null ? "" : questId);
        return true;
    }

    public static Optional<QuestView> getQuest(String questId) {
        ResourceLocation id = resolve(questId);
        return QuestBookManager.get().active().map(s -> s.quests().get(id)).map(q -> new QuestView(q.bookId(), q.id(), q.chapterId(), q.title(), q.dependencies()));
    }

    public static Optional<ProgressView> getProgress(ServerPlayer player, String questId) {
        ResourceLocation id = resolve(questId);
        if (QuestBookManager.get().active().map(s -> s.quests().containsKey(id)).orElse(false)) {
            var progress = ProgressEngine.get().progress(player);
            ResourceLocation bookId = QuestBookManager.get().active().orElseThrow().book().id();
            return Optional.of(new ProgressView(bookId, id, progress.status(id.toString()), progress.taskProgressView(), progress.claimedRewardsView(), progress.revision()));
        }
        return Optional.empty();
    }

    private static ResourceLocation resolve(String value) {
        var snapshot = QuestBookManager.get().active().orElse(null);
        if (snapshot != null && snapshot.book().legacyIds().containsKey(value)) return snapshot.book().legacyIds().get(value);
        if (QuestIds.isLegacy(value)) return QuestIds.normalize(snapshot == null ? "brnquest" : snapshot.book().id().getNamespace(), value);
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed == null) throw new IllegalArgumentException("Invalid ID " + value);
        return parsed;
    }
}
