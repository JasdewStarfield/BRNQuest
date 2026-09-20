package yourscraft.jasdewstarfield.brnquest.reward;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.runtime.ScriptExtensionRegistry;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Built-in and third-party rewards share one decoded execution path. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public final class RewardTypeRegistry {
    private static final Map<ResourceLocation, RewardType<?>> TYPES = new ConcurrentHashMap<>();
    private static volatile boolean frozen;

    private RewardTypeRegistry() {}

    /** Registers a type before the first server resource reload freezes the registry. */
    public static synchronized void register(ResourceLocation id, RewardType<?> type) {
        if (frozen) throw new IllegalStateException("Reward type registry is already frozen");
        if (TYPES.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(type)) != null) {
            throw new IllegalArgumentException("Reward type is already registered: " + id);
        }
    }

    public static RewardType<?> get(ResourceLocation id) {
        RewardType<?> common = TYPES.get(id);
        return common != null ? common : ScriptExtensionRegistry.reward(id);
    }

    /** Returns an immutable snapshot for authoring UIs without exposing the live registry map. */
    @ApiStatus(ApiStability.INTERNAL)
    public static Set<ResourceLocation> registeredIds() {
        Set<ResourceLocation> ids = new java.util.HashSet<>(TYPES.keySet());
        ScriptExtensionRegistry.snapshot().rewardTypeIds().stream()
                .map(ResourceLocation::parse).forEach(ids::add);
        return Set.copyOf(ids);
    }

    /** Script batches may replace their own IDs but can never shadow construction-time Java types. */
    @ApiStatus(ApiStability.INTERNAL)
    public static boolean isCommonRegistered(ResourceLocation id) { return TYPES.containsKey(id); }
    public static synchronized void freeze() { frozen = true; }
    public static boolean isFrozen() { return frozen; }

}
