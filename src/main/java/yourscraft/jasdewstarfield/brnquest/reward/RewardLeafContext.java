package yourscraft.jasdewstarfield.brnquest.reward;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Objects;

/** Root identity and leaf occurrence remain separate; child IDs never enter the normal claim ledger. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record RewardLeafContext(RewardClaimContext root, String path, String occurrenceId,
                                ResourceLocation typeId, Map<String, String> config) {
    public RewardLeafContext {
        Objects.requireNonNull(root); Objects.requireNonNull(path); Objects.requireNonNull(occurrenceId);
        Objects.requireNonNull(typeId); config = Map.copyOf(config);
    }
}
