package yourscraft.jasdewstarfield.brnquest.owner;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

/** Stable IDs of owner providers shipped by BRNQuest. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public final class ProgressOwnerProviders {
    public static final ResourceLocation PERSONAL =
            ResourceLocation.fromNamespaceAndPath(BRNQuest.MOD_ID, "personal");

    private ProgressOwnerProviders() {}
}
