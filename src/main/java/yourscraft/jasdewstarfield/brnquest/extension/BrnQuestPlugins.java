package yourscraft.jasdewstarfield.brnquest.extension;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Public construction-time entry point for optional companion-mod plugins. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public final class BrnQuestPlugins {
    private static final Set<ResourceLocation> REGISTERED = new LinkedHashSet<>();
    private static boolean frozen;

    private BrnQuestPlugins() {}

    /**
     * Registers one plugin during mod construction/common setup.
     * The callback is staged and commits only after all declarations pass preflight validation.
     */
    public static synchronized void register(BrnQuestPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        if (frozen) throw new IllegalStateException("BRNQuest plugin registration is already frozen");
        ResourceLocation id = Objects.requireNonNull(plugin.id(), "plugin.id()");
        if (REGISTERED.contains(id)) throw new IllegalArgumentException("BRNQuest plugin is already registered: " + id);

        BrnQuestExtensionRegistrar registrar = new BrnQuestExtensionRegistrar(id);
        // No live registry is touched if plugin code or declaration validation fails here.
        plugin.register(registrar);
        registrar.commit();
        REGISTERED.add(id);
    }

    /** Immutable diagnostic view of successfully committed plugins. */
    public static synchronized Set<ResourceLocation> registeredPluginIds() {
        return Set.copyOf(REGISTERED);
    }

    public static synchronized boolean isFrozen() {
        return frozen;
    }

    /** Closes the plugin entry point at the same boundary as the underlying common registries. */
    @ApiStatus(ApiStability.INTERNAL)
    public static synchronized void freeze() {
        frozen = true;
    }
}
