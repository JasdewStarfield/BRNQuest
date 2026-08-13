package yourscraft.jasdewstarfield.brnquest.owner;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Construction-time registry of available owner providers. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public final class ProgressOwnerProviderRegistry {
    private static final Map<ResourceLocation, ProgressOwnerProvider> PROVIDERS = new ConcurrentHashMap<>();
    private static volatile boolean frozen;

    static {
        register(PersonalProgressOwnerProvider.INSTANCE);
    }

    private ProgressOwnerProviderRegistry() {}

    /** Registers an available provider; stage 3 still activates only {@code brnquest:personal}. */
    public static synchronized void register(ProgressOwnerProvider provider) {
        Objects.requireNonNull(provider, "provider");
        if (frozen) throw new IllegalStateException("Progress owner provider registry is already frozen");
        ResourceLocation id = Objects.requireNonNull(provider.id(), "provider.id()");
        if (PROVIDERS.putIfAbsent(id, provider) != null) {
            throw new IllegalArgumentException("Progress owner provider is already registered: " + id);
        }
    }

    public static ProgressOwnerProvider get(ResourceLocation id) {
        return PROVIDERS.get(id);
    }

    /** Closes provider registration before the first task-book reload. */
    public static synchronized void freeze() {
        frozen = true;
    }

    public static boolean isFrozen() {
        return frozen;
    }
}
