package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;

/** Client-only icon and fallback contract paired with a reward type by full ID. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public interface ClientRewardPresentation {
    default String itemSnbt(RewardDefinition reward) { return ""; }
    default String symbol(RewardDefinition reward) { return "?"; }
}
