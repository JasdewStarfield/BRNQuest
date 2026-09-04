package yourscraft.jasdewstarfield.brnquest.extension;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

/**
 * Construction-time extension owned by another mod.
 *
 * <p>The plugin class belongs in the consumer mod, so BRNQuest never has to
 * resolve the consumer's classes when that optional mod is absent.</p>
 */
@ApiStatus(ApiStability.EXPERIMENTAL)
public interface BrnQuestPlugin {
    /** Stable plugin identity; declarations must use the same namespace. */
    ResourceLocation id();

    /** Collects common task, reward, and owner-provider declarations atomically. */
    void register(BrnQuestExtensionRegistrar registrar);
}
