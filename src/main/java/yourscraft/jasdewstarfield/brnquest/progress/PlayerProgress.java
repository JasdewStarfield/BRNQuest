package yourscraft.jasdewstarfield.brnquest.progress;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/** Mutable server-only state; public APIs expose immutable views instead. */
public final class PlayerProgress {
    // Per-member objectives live inside the team ledger and survive logout and server restart.
    private final Map<UUID, PlayerProgress> memberObjectives = new HashMap<>();
    public PlayerProgress objectives(UUID member) {
        return memberObjectives.computeIfAbsent(member, ignored -> new PlayerProgress());
    }
    /** Completion is tested against the authoritative current roster, including offline members. */
    public boolean allMembersCompleted(String quest, Set<UUID> members) {
        return !members.isEmpty() && members.stream().allMatch(member -> {
            var objectives = memberObjectives.get(member);
            return objectives != null && objectives.status(quest) == QuestStatus.COMPLETED;
        });
    }
    /** Immutable member state participates in administrator stale-preview detection. */
    public Map<UUID, String> objectiveState(String quest, Collection<String> tasks) {
        Map<UUID, String> result = new TreeMap<>();
        memberObjectives.forEach((member, ledger) -> {
            Map<String, Long> values = new TreeMap<>();
            tasks.forEach(task -> values.put(task, ledger.taskProgress(task)));
            result.put(member, ledger.status(quest).name() + values);
        });
        return Map.copyOf(result);
    }
    private final Map<String, QuestStatus> quests = new HashMap<>();
    private final Map<String, Long> taskProgress = new HashMap<>();
    private final Set<String> claimedRewards = new HashSet<>();
    private final Map<String, Long> completionTimes = new HashMap<>();
    private final Map<String, Integer> completionCycles = new HashMap<>();
    private final Map<String, Long> nextAvailableTimes = new HashMap<>();
    private final Set<String> orphanedQuestIds = new HashSet<>();
    // Frozen completion cohorts prevent join/leave cycling from minting old-cycle rewards.
    private final Map<String, Set<UUID>> completionMembers = new HashMap<>();
    private final Map<UUID, Set<String>> memberClaims = new HashMap<>();
    // Explicit quest resets create a fresh identity, independently of visible repeat-cycle counters.
    private final Map<String, String> claimGenerations = new HashMap<>();
    private final Set<String> rewardAttempts = new HashSet<>();
    /** Preserve attempts even if an objective is reopened before a full quest reset. */
    public void rewardAttempted(String questId) { rewardAttempts.add(questId); }
    public String claimGeneration(String questId) { return claimGenerations.getOrDefault(questId, ""); }
    private String revision = "";

    public QuestStatus status(String id) { return quests.getOrDefault(id, QuestStatus.LOCKED); }
    public void status(String id, QuestStatus status) { quests.put(id, status); }
    public long taskProgress(String id) { return taskProgress.getOrDefault(id, 0L); }
    /** Started dependencies can observe contribution by any member without combining their counters. */
    public boolean taskStarted(String id) {
        return taskProgress(id) > 0 || memberObjectives.values().stream().anyMatch(ledger -> ledger.taskProgress(id) > 0);
    }
    public long addTaskProgress(String id, long amount) { return taskProgress.merge(id, amount, Long::sum); }
    public boolean claim(String id) { return claimedRewards.add(id); }
    public boolean isClaimed(String id) { return claimedRewards.contains(id); }
    public void completionMembers(String quest, Set<UUID> members) {
        completionMembers.put(quest, Set.copyOf(members));
    }
    public Set<UUID> completionMembers(String quest) { return completionMembers.getOrDefault(quest, Set.of()); }
    public boolean memberClaimed(UUID player, String reward) {
        return memberClaims.getOrDefault(player, Set.of()).contains(reward);
    }
    public boolean claimMember(UUID player, String reward) {
        return memberClaims.computeIfAbsent(player, ignored -> new HashSet<>()).add(reward);
    }
    /** Immutable per-member receipts are included in administrator concurrency checks. */
    public Map<UUID, Set<String>> memberClaimsFor(Collection<String> rewards) {
        Map<UUID, Set<String>> result = new HashMap<>();
        memberClaims.forEach((player, claims) -> {
            Set<String> selected = new HashSet<>(claims);
            selected.retainAll(rewards);
            if (!selected.isEmpty()) result.put(player, Set.copyOf(selected));
        });
        return Map.copyOf(result);
    }
    /** A viewer sees shared one-shot receipts plus only their own ordinary reward receipts. */
    public Set<String> claimsFor(UUID player) {
        Set<String> result = new HashSet<>(claimedRewards);
        result.addAll(memberClaims.getOrDefault(player, Set.of()));
        return Set.copyOf(result);
    }
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
        boolean memberChanged = false;
        for (var ledger : memberObjectives.values()) memberChanged |= ledger.migrateQuestId(oldId, newId);
        boolean oldAttempt = rewardAttempts.remove(oldId);
        if (oldAttempt) rewardAttempts.add(newId);
        String oldGeneration = claimGenerations.remove(oldId);
        QuestStatus oldStatus = quests.remove(oldId);
        Long oldCompletion = completionTimes.remove(oldId);
        Integer oldCycles = completionCycles.remove(oldId);
        Long oldNext = nextAvailableTimes.remove(oldId);
        boolean changed = memberChanged || oldAttempt || oldGeneration != null || oldStatus != null || oldCompletion != null || oldCycles != null || oldNext != null
                || orphanedQuestIds.remove(oldId);
        if (oldGeneration != null) claimGenerations.putIfAbsent(newId, oldGeneration);
        if (oldStatus != null) {
            QuestStatus current = quests.get(newId);
            quests.put(newId, current == null || statusRank(oldStatus) > statusRank(current) ? oldStatus : current);
        }
        Set<UUID> oldMembers = completionMembers.remove(oldId);
        if (oldMembers != null) completionMembers.putIfAbsent(newId, oldMembers);
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
        boolean memberChanged = false;
        for (var ledger : memberObjectives.values()) memberChanged |= ledger.migrateTaskId(oldId, newId);
        Long oldProgress = taskProgress.remove(oldId);
        if (oldProgress == null) return memberChanged;
        taskProgress.merge(newId, oldProgress, Math::max);
        return true;
    }
    /** Preserves idempotent reward claims across an explicit stable reward-ID rename. */
    public boolean migrateRewardId(String oldId, String newId) {
        if (oldId == null || newId == null || oldId.equals(newId)) return false;
        boolean changed = claimedRewards.remove(oldId);
        if (changed) claimedRewards.add(newId);
        for (Set<String> claims : memberClaims.values()) if (claims.remove(oldId)) {
            claims.add(newId);
            changed = true;
        }
        return changed;
    }
    public void resetQuest(String questId, Collection<String> taskIds, Collection<String> rewardIds) {
        // An already empty quest is a no-op; a failed or pending attempt still needs a new identity.
        boolean hadAttempt = rewardAttempts.remove(questId);
        if (hadAttempt || status(questId) == QuestStatus.COMPLETED || status(questId) == QuestStatus.REWARD_CLAIMED
                || completionCycles(questId) > 0 || taskIds.stream().anyMatch(id -> taskProgress(id) != 0)
                || rewardIds.stream().anyMatch(claimedRewards::contains)
                || memberClaims.values().stream().anyMatch(claims -> rewardIds.stream().anyMatch(claims::contains))) {
            claimGenerations.put(questId, UUID.randomUUID().toString());
        }
        quests.remove(questId);
        completionTimes.remove(questId);
        memberObjectives.values().forEach(ledger -> ledger.resetQuest(questId, taskIds, rewardIds));
        taskIds.forEach(taskProgress::remove);
        claimedRewards.removeAll(rewardIds);
        memberClaims.values().forEach(claims -> claims.removeAll(rewardIds));
        completionMembers.remove(questId);
        completionCycles.remove(questId);
        nextAvailableTimes.remove(questId);
    }

    /** Starts a new repeat cycle only after the prior cycle's reward policy permits it. */
    public void beginNextCycle(String questId, Collection<String> taskIds, Collection<String> rewardIds,
                               QuestStatus availableStatus) {
        memberObjectives.values().forEach(ledger -> ledger.resetQuest(questId, taskIds, rewardIds));
        taskIds.forEach(taskProgress::remove);
        claimedRewards.removeAll(rewardIds);
        memberClaims.values().forEach(claims -> claims.removeAll(rewardIds));
        completionMembers.remove(questId);
        completionTimes.remove(questId);
        nextAvailableTimes.remove(questId);
        quests.put(questId, availableStatus);
    }

    /** A single-objective reset must never make previously delivered rewards claimable again. */
    void resetTask(String questId, String taskId, QuestStatus reopenedStatus) {
        memberObjectives.values().forEach(ledger -> ledger.resetTask(questId, taskId, reopenedStatus));
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
        CompoundTag objectives = new CompoundTag();
        memberObjectives.forEach((member, ledger) -> objectives.put(member.toString(), ledger.save()));
        tag.put("member_objectives", objectives);
        CompoundTag generations = new CompoundTag();
        claimGenerations.forEach(generations::putString);
        tag.put("claim_generations", generations);
        tag.put("reward_attempts", stringList(rewardAttempts));
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
        CompoundTag cohorts = new CompoundTag();
        completionMembers.forEach((quest, members) -> cohorts.put(quest,
                stringList(members.stream().map(UUID::toString).toList())));
        tag.put("completion_members", cohorts);
        CompoundTag receipts = new CompoundTag();
        memberClaims.forEach((player, claims) -> receipts.put(player.toString(), stringList(claims)));
        tag.put("member_claims", receipts);
        tag.put("orphaned_quests", stringList(orphanedQuestIds));
        return tag;
    }

    public static PlayerProgress load(CompoundTag tag) {
        PlayerProgress result = new PlayerProgress();
        result.revision = tag.getString("revision");
        CompoundTag objectives = tag.getCompound("member_objectives");
        for (String member : objectives.getAllKeys()) {
            try { result.memberObjectives.put(UUID.fromString(member), load(objectives.getCompound(member))); }
            catch (IllegalArgumentException ignored) { /* Retain every valid member when a key is malformed. */ }
        }
        readStrings(tag.getList("reward_attempts", Tag.TAG_STRING), result.rewardAttempts);
        CompoundTag generations = tag.getCompound("claim_generations");
        generations.getAllKeys().forEach(key -> result.claimGenerations.put(key, generations.getString(key)));
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
        CompoundTag cohorts = tag.getCompound("completion_members");
        for (String quest : cohorts.getAllKeys()) {
            Set<String> ids = new HashSet<>();
            readStrings(cohorts.getList(quest, Tag.TAG_STRING), ids);
            Set<UUID> members = new HashSet<>();
            for (String id : ids) try { members.add(UUID.fromString(id)); } catch (IllegalArgumentException ignored) { }
            result.completionMembers.put(quest, Set.copyOf(members));
        }
        CompoundTag receipts = tag.getCompound("member_claims");
        for (String player : receipts.getAllKeys()) {
            try {
                Set<String> claims = new HashSet<>();
                readStrings(receipts.getList(player, Tag.TAG_STRING), claims);
                result.memberClaims.put(UUID.fromString(player), claims);
            } catch (IllegalArgumentException ignored) { }
        }
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
