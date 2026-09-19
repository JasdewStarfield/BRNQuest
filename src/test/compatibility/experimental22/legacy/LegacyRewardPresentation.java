package legacy;

import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import yourscraft.jasdewstarfield.brnquest.client.ui.ClientRewardPresentation;

/** Compiled against the frozen experimental.22 SPI, which has no contentSummary method. */
public final class LegacyRewardPresentation implements ClientRewardPresentation {
    public Component typeName(RewardView reward) { return Component.literal("Legacy reward"); }
}
