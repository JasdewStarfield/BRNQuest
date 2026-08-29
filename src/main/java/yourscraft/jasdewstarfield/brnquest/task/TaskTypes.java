package yourscraft.jasdewstarfield.brnquest.task;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;

/** Stable full identifiers for BRNQuest's built-in task types. */
public final class TaskTypes {
    public static final ResourceLocation CHECKMARK = id("checkmark");
    public static final ResourceLocation CUSTOM = id("custom");
    public static final ResourceLocation ITEM = id("item");
    public static final ResourceLocation ITEM_CHOICE = id("item_choice");

    private TaskTypes() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(BRNQuest.MOD_ID, path);
    }
}
