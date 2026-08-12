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
    private final Set<String> orphanedQuestIds = new HashSet<>();
    private String revision = "";

    public QuestStatus status(String id) { return quests.getOrDefault(id, QuestStatus.LOCKED); }
    public void status(String id, QuestStatus status) { quests.put(id, status); }
    public long taskProgress(String id) { return taskProgress.getOrDefault(id, 0L); }
    public long addTaskProgress(String id, long amount) { return taskProgress.merge(id, amount, Long::sum); }
    public boolean claim(String id) { return claimedRewards.add(id); }
    public boolean isClaimed(String id) { return claimedRewards.contains(id); }
    public void completedAt(String id, long time) { completionTimes.put(id, time); }
    public String revision() { return revision; }
    public void revision(String revision) { this.revision = revision; }
    public Map<String, QuestStatus> questsView() { return Map.copyOf(quests); }
    public Map<String, Long> taskProgressView() { return Map.copyOf(taskProgress); }
    public Set<String> claimedRewardsView() { return Set.copyOf(claimedRewards); }
    public Set<String> orphanedQuestIds() { return Set.copyOf(orphanedQuestIds); }
    public void orphan(String id) { orphanedQuestIds.add(id); }
    public void resetQuest(String questId, Collection<String> taskIds, Collection<String> rewardIds) {
        quests.remove(questId);
        completionTimes.remove(questId);
        taskIds.forEach(taskProgress::remove);
        claimedRewards.removeAll(rewardIds);
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
