package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypes;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Client reward presentation registry frozen with the task presentation registry. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public final class ClientRewardPresentationRegistry {
    private static final Map<ResourceLocation, ClientRewardPresentation> PRESENTATIONS = new ConcurrentHashMap<>();
    private static final ClientRewardPresentation FALLBACK = new ClientRewardPresentation() {};
    private static volatile boolean frozen;

    static {
        register(RewardTypes.ITEM, new ClientRewardPresentation() {
            public String itemSnbt(RewardDefinition reward) { return reward.config().getOrDefault("item", ""); }
        });
        register(RewardTypes.CUSTOM, FALLBACK);
    }

    private ClientRewardPresentationRegistry() {}

    public static synchronized void register(ResourceLocation id, ClientRewardPresentation presentation) {
        if (frozen) throw new IllegalStateException("Client reward presentation registry is already frozen");
        if (PRESENTATIONS.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(presentation)) != null) {
            throw new IllegalArgumentException("Client reward presentation is already registered: " + id);
        }
    }

    public static ClientRewardPresentation get(ResourceLocation id) {
        return PRESENTATIONS.getOrDefault(id, FALLBACK);
    }

    public static synchronized void freeze() { frozen = true; }
    public static boolean isFrozen() { return frozen; }
}
