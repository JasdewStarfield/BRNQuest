package yourscraft.jasdewstarfield.brnquest.task;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.runtime.ScriptExtensionRegistry;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Extensible task registry with a construction-time registration window. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public final class TaskTypeRegistry {
    private static final Map<ResourceLocation, TaskType<?>> TYPES = new ConcurrentHashMap<>();
    private static volatile boolean frozen;

    private TaskTypeRegistry() {}

    /**
     * Registers a type during mod construction or common setup.
     * The registry is frozen before the first server resource reload.
     */
    public static synchronized void register(ResourceLocation id, TaskType<?> type) {
        if (frozen) throw new IllegalStateException("Task type registry is already frozen");
        if (TYPES.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(type)) != null) {
            throw new IllegalArgumentException("Task type is already registered: " + id);
        }
    }

    public static TaskType<?> get(ResourceLocation id) {
        TaskType<?> common = TYPES.get(id);
        return common != null ? common : ScriptExtensionRegistry.task(id);
    }

    /** Returns an immutable snapshot for authoring UIs without exposing the live registry map. */
    @ApiStatus(ApiStability.INTERNAL)
    public static Set<ResourceLocation> registeredIds() {
        Set<ResourceLocation> ids = new java.util.HashSet<>(TYPES.keySet());
        ScriptExtensionRegistry.snapshot().taskTypeIds().stream()
                .map(ResourceLocation::parse).forEach(ids::add);
        return Set.copyOf(ids);
    }

    /** Script batches may replace their own IDs but can never shadow construction-time Java types. */
    @ApiStatus(ApiStability.INTERNAL)
    public static boolean isCommonRegistered(ResourceLocation id) { return TYPES.containsKey(id); }

    /** Closes the public registration window before task books are decoded. */
    public static synchronized void freeze() { frozen = true; }

    public static boolean isFrozen() { return frozen; }

}
