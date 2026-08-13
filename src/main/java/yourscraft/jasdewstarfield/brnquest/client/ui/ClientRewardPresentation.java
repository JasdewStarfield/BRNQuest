package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;

/** Client-only icon and fallback contract paired with a reward type by full ID. */
public interface ClientRewardPresentation {
    default String itemSnbt(RewardDefinition reward) { return ""; }
    default String symbol(RewardDefinition reward) { return "?"; }
}
