package yourscraft.jasdewstarfield.brnquest.runtime;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.reward.RewardType;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.task.TaskType;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Atomic runtime registry for script-defined types.
 *
 * <p>Java extensions remain permanently frozen before task-book decoding. Scripts instead
 * build a candidate batch which becomes visible in one replacement, so a failed script does
 * not leave half of its declarations in the active registry.</p>
 */
@ApiStatus(ApiStability.INTERNAL)
public final class ScriptExtensionRegistry {
    private static Map<ResourceLocation, TaskType<?>> activeTasks = Map.of();
    private static Map<ResourceLocation, RewardType<?>> activeRewards = Map.of();
    private static LinkedHashMap<ResourceLocation, TaskType<?>> candidateTasks;
    private static LinkedHashMap<ResourceLocation, RewardType<?>> candidateRewards;
    private static ReloadState reloadState = ReloadState.IDLE;
    private static String failureMessage = "";
    private static final ThreadLocal<Boolean> CANDIDATE_LOOKUP = ThreadLocal.withInitial(() -> false);

    private ScriptExtensionRegistry() {}

    /** Opens one candidate window immediately before server scripts are evaluated. */
    public static synchronized void beginRegistration() {
        // A resource reload can abort before BRNQuest's listener runs. A later attempt always
        // replaces that stale candidate while the last committed types remain untouched.
        candidateTasks = new LinkedHashMap<>();
        candidateRewards = new LinkedHashMap<>();
        reloadState = ReloadState.OPEN;
        failureMessage = "";
    }

    public static synchronized void stageTask(ResourceLocation id, TaskType<?> type) {
        requireOpen();
        requireScriptOwned(id, "task type");
        if (TaskTypeRegistry.isCommonRegistered(id)) {
            throw new IllegalArgumentException("Script task type conflicts with a Java type: " + id);
        }
        if (candidateTasks.putIfAbsent(id, Objects.requireNonNull(type, "task type")) != null) {
            throw new IllegalArgumentException("Script task type is already declared: " + id);
        }
    }

    public static synchronized void stageReward(ResourceLocation id, RewardType<?> type) {
        requireOpen();
        requireScriptOwned(id, "reward type");
        if (RewardTypeRegistry.isCommonRegistered(id)) {
            throw new IllegalArgumentException("Script reward type conflicts with a Java type: " + id);
        }
        if (candidateRewards.putIfAbsent(id, Objects.requireNonNull(type, "reward type")) != null) {
            throw new IllegalArgumentException("Script reward type is already declared: " + id);
        }
    }

    /** Seals a clean script batch until the matching task book has also validated. */
    public static synchronized void sealRegistration() {
        requireOpen();
        candidateTasks = new LinkedHashMap<>(candidateTasks);
        candidateRewards = new LinkedHashMap<>(candidateRewards);
        reloadState = ReloadState.READY;
    }

    /** Records a failed script attempt so the matching task-book reload is also rejected. */
    public static synchronized void failRegistration(String message) {
        candidateTasks = null;
        candidateRewards = null;
        reloadState = ReloadState.FAILED;
        failureMessage = message == null || message.isBlank() ? "Server script evaluation failed" : message;
    }

    /** Discards a pending batch and preserves the last known-good active types. */
    public static synchronized void rollbackRegistration() {
        closeCandidate();
    }

    /** Makes sealed types visible only while the current thread validates a candidate task book. */
    public static <T> T withCandidateLookup(Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        boolean enabled;
        synchronized (ScriptExtensionRegistry.class) {
            enabled = reloadState == ReloadState.READY;
        }
        Boolean previous = CANDIDATE_LOOKUP.get();
        CANDIDATE_LOOKUP.set(enabled);
        try {
            return action.get();
        } finally {
            CANDIDATE_LOOKUP.set(previous);
        }
    }

    /** Publishes the sealed batch immediately before its validated task-book snapshot. */
    static synchronized void commitSealedRegistration() {
        if (reloadState == ReloadState.IDLE) return;
        if (reloadState != ReloadState.READY || candidateTasks == null || candidateRewards == null) {
            throw new IllegalStateException("Script extension candidate is not ready");
        }
        activeTasks = Map.copyOf(candidateTasks);
        activeRewards = Map.copyOf(candidateRewards);
        closeCandidate();
    }

    static synchronized String reloadFailure() {
        return reloadState == ReloadState.FAILED ? failureMessage : "";
    }

    public static synchronized TaskType<?> task(ResourceLocation id) {
        if (CANDIDATE_LOOKUP.get() && reloadState == ReloadState.READY) return candidateTasks.get(id);
        return activeTasks.get(id);
    }

    public static synchronized RewardType<?> reward(ResourceLocation id) {
        if (CANDIDATE_LOOKUP.get() && reloadState == ReloadState.READY) return candidateRewards.get(id);
        return activeRewards.get(id);
    }

    public static synchronized Snapshot snapshot() {
        return new Snapshot(activeTasks.keySet().stream().map(ResourceLocation::toString).sorted().toList(),
                activeRewards.keySet().stream().map(ResourceLocation::toString).sorted().toList(),
                reloadState == ReloadState.OPEN);
    }

    private static void requireOpen() {
        if (reloadState != ReloadState.OPEN || candidateTasks == null || candidateRewards == null) {
            throw new IllegalStateException("Script extension registration is closed");
        }
    }

    private static void requireScriptOwned(ResourceLocation id, String kind) {
        Objects.requireNonNull(id, "id");
        // BRNQuest's namespace is reserved for built-ins; scripts should use their pack/mod namespace.
        if ("minecraft".equals(id.getNamespace()) || "brnquest".equals(id.getNamespace())) {
            throw new IllegalArgumentException("Script " + kind + " must not use reserved namespace: " + id);
        }
    }

    private static void closeCandidate() {
        candidateTasks = null;
        candidateRewards = null;
        reloadState = ReloadState.IDLE;
        failureMessage = "";
    }

    private enum ReloadState { IDLE, OPEN, READY, FAILED }

    /** Immutable diagnostic projection; no live registry collection crosses the boundary. */
    public record Snapshot(java.util.List<String> taskTypeIds, java.util.List<String> rewardTypeIds,
                           boolean registrationOpen) {}
}
