package yourscraft.jasdewstarfield.brnquest.progress;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/** Mutable server-only state; public APIs expose immutable views instead. */
public final class PlayerProgress {
    private final Map<String, QuestStatus> quests = new HashMap<>();
    private final Map<String, Long> taskProgress = new HashMap<>();
    private final Set<String> claimedRewards = new HashSet<>();
    private final Map<String, Long> completionTimes = new HashMap<>();
    private final Map<String, Integer> completionCycles = new HashMap<>();
    private final Map<String, Long> nextAvailableTimes = new HashMap<>();
    private final Set<String> orphanedQuestIds = new HashSet<>();
    private String revision = "";

    public QuestStatus status(String id) { return quests.getOrDefault(id, QuestStatus.LOCKED); }
    public void status(String id, QuestStatus status) { quests.put(id, status); }
    public long taskProgress(String id) { return taskProgress.getOrDefault(id, 0L); }
    public long addTaskProgress(String id, long amount) { return taskProgress.merge(id, amount, Long::sum); }
    public boolean claim(String id) { return claimedRewards.add(id); }
    public boolean isClaimed(String id) { return claimedRewards.contains(id); }
    public void completedAt(String id, long time) { completionTimes.put(id, time); }
    public long completedAt(String id) { return completionTimes.getOrDefault(id, 0L); }
    public int completionCycles(String id) { return completionCycles.getOrDefault(id, 0); }
    public long nextAvailableAt(String id) { return nextAvailableTimes.getOrDefault(id, 0L); }
    public void completedCycle(String id, long completedAt, long nextAvailableAt) {
        completionTimes.put(id, completedAt);
        completionCycles.merge(id, 1, Integer::sum);
        nextAvailableTimes.put(id, nextAvailableAt);
    }
    public String revision() { return revision; }
    public void revision(String revision) { this.revision = revision; }
    public Map<String, QuestStatus> questsView() { return Map.copyOf(quests); }
    public Map<String, Long> taskProgressView() { return Map.copyOf(taskProgress); }
    public Set<String> claimedRewardsView() { return Set.copyOf(claimedRewards); }
    public Map<String, Integer> completionCyclesView() { return Map.copyOf(completionCycles); }
    public Map<String, Long> nextAvailableTimesView() { return Map.copyOf(nextAvailableTimes); }
    public Set<String> orphanedQuestIds() { return Set.copyOf(orphanedQuestIds); }
    public void orphan(String id) { orphanedQuestIds.add(id); }
    /** Moves quest-level state across a canonical ID alias without touching stable task/reward ledgers. */
    public boolean migrateQuestId(String oldId, String newId) {
        if (oldId == null || newId == null || oldId.equals(newId)) return false;
        QuestStatus oldStatus = quests.remove(oldId);
        Long oldCompletion = completionTimes.remove(oldId);
        Integer oldCycles = completionCycles.remove(oldId);
        Long oldNext = nextAvailableTimes.remove(oldId);
        boolean changed = oldStatus != null || oldCompletion != null || oldCycles != null || oldNext != null
                || orphanedQuestIds.remove(oldId);
        if (oldStatus != null) {
            QuestStatus current = quests.get(newId);
            quests.put(newId, current == null || statusRank(oldStatus) > statusRank(current) ? oldStatus : current);
        }
        if (oldCompletion != null) {
            completionTimes.merge(newId, oldCompletion, (current, migrated) ->
                    current == 0L ? migrated : migrated == 0L ? current : Math.min(current, migrated));
        }
        if (oldCycles != null) completionCycles.merge(newId, oldCycles, Math::max);
        if (oldNext != null) nextAvailableTimes.merge(newId, oldNext, Math::max);
        return changed;
    }
    /** Moves accumulated task progress when an author explicitly renames the stable task ID. */
    public boolean migrateTaskId(String oldId, String newId) {
        if (oldId == null || newId == null || oldId.equals(newId)) return false;
        Long oldProgress = taskProgress.remove(oldId);
        if (oldProgress == null) return false;
        taskProgress.merge(newId, oldProgress, Math::max);
        return true;
    }
    /** Preserves idempotent reward claims across an explicit stable reward-ID rename. */
    public boolean migrateRewardId(String oldId, String newId) {
        if (oldId == null || newId == null || oldId.equals(newId) || !claimedRewards.remove(oldId)) return false;
        claimedRewards.add(newId);
        return true;
    }
    public void resetQuest(String questId, Collection<String> taskIds, Collection<String> rewardIds) {
        quests.remove(questId);
        completionTimes.remove(questId);
        taskIds.forEach(taskProgress::remove);
        claimedRewards.removeAll(rewardIds);
        completionCycles.remove(questId);
        nextAvailableTimes.remove(questId);
    }

    /** Starts a new repeat cycle only after the prior cycle's reward policy permits it. */
    public void beginNextCycle(String questId, Collection<String> taskIds, Collection<String> rewardIds,
                               QuestStatus availableStatus) {
        taskIds.forEach(taskProgress::remove);
        claimedRewards.removeAll(rewardIds);
        completionTimes.remove(questId);
        nextAvailableTimes.remove(questId);
        quests.put(questId, availableStatus);
    }

    /** A single-objective reset must never make previously delivered rewards claimable again. */
    void resetTask(String questId, String taskId, QuestStatus reopenedStatus) {
        taskProgress.remove(taskId);
        completionTimes.remove(questId);
        // An administrator reopening an objective invalidates dependency completion for this quest.
        completionCycles.remove(questId);
        nextAvailableTimes.remove(questId);
        quests.put(questId, reopenedStatus);
    }

    private static int statusRank(QuestStatus status) {
        return switch (status) {
            case REWARD_CLAIMED -> 5;
            case COMPLETED -> 4;
            case ACTIVE -> 3;
            case AVAILABLE -> 2;
            case LOCKED -> 1;
            case UNAVAILABLE -> 0;
        };
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("revision", revision);
        CompoundTag questTag = new CompoundTag();
        quests.forEach((id, status) -> questTag.putString(id, status.name()));
        tag.put("quests", questTag);
        CompoundTag tasks = new CompoundTag();
        taskProgress.forEach(tasks::putLong);
        tag.put("task_progress", tasks);
        CompoundTag times = new CompoundTag();
        completionTimes.forEach(times::putLong);
        tag.put("completion_times", times);
        CompoundTag cycles = new CompoundTag();
        completionCycles.forEach(cycles::putInt);
        tag.put("completion_cycles", cycles);
        CompoundTag nextTimes = new CompoundTag();
        nextAvailableTimes.forEach(nextTimes::putLong);
        tag.put("next_available_times", nextTimes);
        tag.put("claimed_rewards", stringList(claimedRewards));
        tag.put("orphaned_quests", stringList(orphanedQuestIds));
        return tag;
    }

    public static PlayerProgress load(CompoundTag tag) {
        PlayerProgress result = new PlayerProgress();
        result.revision = tag.getString("revision");
        CompoundTag quests = tag.getCompound("quests");
        for (String key : quests.getAllKeys()) {
            try { result.quests.put(key, QuestStatus.valueOf(quests.getString(key))); }
            catch (IllegalArgumentException ignored) { result.quests.put(key, QuestStatus.UNAVAILABLE); }
        }
        CompoundTag tasks = tag.getCompound("task_progress");
        tasks.getAllKeys().forEach(key -> result.taskProgress.put(key, tasks.getLong(key)));
        CompoundTag times = tag.getCompound("completion_times");
        times.getAllKeys().forEach(key -> result.completionTimes.put(key, times.getLong(key)));
        CompoundTag cycles = tag.getCompound("completion_cycles");
        cycles.getAllKeys().forEach(key -> result.completionCycles.put(key, cycles.getInt(key)));
        CompoundTag nextTimes = tag.getCompound("next_available_times");
        nextTimes.getAllKeys().forEach(key -> result.nextAvailableTimes.put(key, nextTimes.getLong(key)));
        readStrings(tag.getList("claimed_rewards", Tag.TAG_STRING), result.claimedRewards);
        readStrings(tag.getList("orphaned_quests", Tag.TAG_STRING), result.orphanedQuestIds);
        return result;
    }

    private static ListTag stringList(Collection<String> values) {
        ListTag list = new ListTag();
        values.stream().sorted().map(StringTag::valueOf).forEach(list::add);
        return list;
    }

    private static void readStrings(ListTag list, Set<String> target) { for (int i = 0; i < list.size(); i++) target.add(list.getString(i)); }
}
