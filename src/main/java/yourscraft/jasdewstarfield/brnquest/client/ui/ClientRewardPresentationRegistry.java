package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypes;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Client reward presentation registry frozen with the task presentation registry. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public final class ClientRewardPresentationRegistry {
    private static final Map<ResourceLocation, ClientRewardPresentation> PRESENTATIONS = new ConcurrentHashMap<>();
    private static final ClientRewardPresentation FALLBACK = new ClientRewardPresentation() {};
    private static final Map<ResourceLocation, yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon> TYPE_ICONS = new ConcurrentHashMap<>();
    private static volatile boolean frozen;

    private ClientRewardPresentationRegistry() {}

    public static synchronized void register(ResourceLocation id, ClientRewardPresentation presentation) {
        if (frozen) throw new IllegalStateException("Client reward presentation registry is already frozen");
        if (PRESENTATIONS.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(presentation)) != null) {
            throw new IllegalArgumentException("Client reward presentation is already registered: " + id);
        }
    }

    /** Optional registration metadata uses the same lifecycle and duplicate checks as presentations. */
    public static synchronized void register(ResourceLocation id, ClientRewardPresentation presentation,
            yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon icon) {
        Objects.requireNonNull(icon);
        register(id, presentation);
        TYPE_ICONS.put(id, icon);
    }

    /** No synthetic instance/config is required to render a type choice. */
    public static yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon typeIcon(ResourceLocation id) {
        return get(id).typeIcon().orElseGet(() -> TYPE_ICONS.getOrDefault(id, ClientTypeIconFallback.icon()));
    }
    /** Distinguishes registered decoration from the generic unknown-type fallback. */
    static boolean hasTypeIcon(ResourceLocation id) {
        return get(id).typeIcon().isPresent() || TYPE_ICONS.containsKey(id);
    }
    public static ClientRewardPresentation get(ResourceLocation id) {
        return PRESENTATIONS.getOrDefault(id, FALLBACK);
    }

    public static synchronized void freeze() { frozen = true; }
    public static boolean isFrozen() { return frozen; }

    /** Computes the effective visual stack size without mutating the cached parsed ItemStack. */

}
