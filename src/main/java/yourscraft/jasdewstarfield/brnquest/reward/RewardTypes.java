package yourscraft.jasdewstarfield.brnquest.reward;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;

/** Stable full identifiers for BRNQuest's built-in reward types. */
public final class RewardTypes {
    public static final ResourceLocation COMMAND = id("command");
    public static final ResourceLocation CUSTOM = id("custom");
    public static final ResourceLocation ITEM = id("item");
    public static final ResourceLocation XP = id("xp");
    public static final ResourceLocation XP_LEVELS = id("xp_levels");

    private RewardTypes() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(BRNQuest.MOD_ID, path);
    }
}
