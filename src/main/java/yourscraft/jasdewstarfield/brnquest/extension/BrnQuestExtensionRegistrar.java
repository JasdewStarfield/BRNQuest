package yourscraft.jasdewstarfield.brnquest.extension;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerProvider;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerProviderRegistry;
import yourscraft.jasdewstarfield.brnquest.reward.RewardType;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.task.TaskType;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeRegistry;

import yourscraft.jasdewstarfield.brnquest.editor.ServerFieldSources;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Staging registrar that prevents a failing plugin callback from partially changing live registries. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public final class BrnQuestExtensionRegistrar {
    private final ResourceLocation pluginId;
    private final Map<ResourceLocation, ServerFieldSources.Source> fieldSources = new LinkedHashMap<>();
    private final Map<ResourceLocation, TaskType<?>> tasks = new LinkedHashMap<>();
    private final Map<ResourceLocation, RewardType<?>> rewards = new LinkedHashMap<>();
    private final Map<ResourceLocation, ProgressOwnerProvider> owners = new LinkedHashMap<>();

    BrnQuestExtensionRegistrar(ResourceLocation pluginId) {
        this.pluginId = Objects.requireNonNull(pluginId, "pluginId");
    }

    public BrnQuestExtensionRegistrar task(ResourceLocation id, TaskType<?> type) {
        putOwned(tasks, id, type, "task type");
        return this;
    }

    public BrnQuestExtensionRegistrar reward(ResourceLocation id, RewardType<?> type) {
        putOwned(rewards, id, type, "reward type");
        return this;
    }

    /** Stages read-only author choices together with the type that declares their source ID. */
    public BrnQuestExtensionRegistrar fieldSource(ResourceLocation id, ServerFieldSources.Source source) {
        putOwned(fieldSources, id, source, "field source");
        return this;
    }

    public BrnQuestExtensionRegistrar progressOwner(ProgressOwnerProvider provider) {
        Objects.requireNonNull(provider, "provider");
        ResourceLocation id = Objects.requireNonNull(provider.id(), "provider.id()");
        putOwned(owners, id, provider, "progress owner provider");
        return this;
    }

    /** Validates every declaration before committing any of them. */
    void commit() {
        // Use one deterministic lock order so direct low-level registrations cannot race the batch preflight.
        synchronized (TaskTypeRegistry.class) {
            synchronized (RewardTypeRegistry.class) {
                synchronized (ProgressOwnerProviderRegistry.class) {
                    synchronized (ServerFieldSources.class) {
                        fieldSources.keySet().forEach(ServerFieldSources::requireAvailable);
                        tasks.keySet().forEach(id -> requireAvailable(TaskTypeRegistry.get(id), id, "task type"));
                        rewards.keySet().forEach(id -> requireAvailable(RewardTypeRegistry.get(id), id, "reward type"));
                        owners.keySet().forEach(id -> requireAvailable(ProgressOwnerProviderRegistry.get(id), id,
                                "progress owner provider"));

                        // Static synchronized registry methods are reentrant while these class locks are held.
                        tasks.forEach(TaskTypeRegistry::register);
                        rewards.forEach(RewardTypeRegistry::register);
                        owners.values().forEach(ProgressOwnerProviderRegistry::register);
                        fieldSources.forEach(ServerFieldSources::register);
                    }
                }
            }
        }
    }

    private <T> void putOwned(Map<ResourceLocation, T> declarations, ResourceLocation id, T value, String kind) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(value, kind);
        if (!pluginId.getNamespace().equals(id.getNamespace())) {
            throw new IllegalArgumentException("Plugin " + pluginId + " cannot register " + kind + " " + id
                    + " outside namespace " + pluginId.getNamespace());
        }
        if (declarations.putIfAbsent(id, value) != null) {
            throw new IllegalArgumentException("Plugin " + pluginId + " declared duplicate " + kind + " " + id);
        }
    }

    private static void requireAvailable(Object existing, ResourceLocation id, String kind) {
        if (existing != null) throw new IllegalArgumentException(kind + " is already registered: " + id);
    }
}
