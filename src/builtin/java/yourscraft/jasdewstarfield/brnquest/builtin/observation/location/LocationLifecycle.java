package yourscraft.jasdewstarfield.brnquest.builtin.observation.location;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.task.*;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;

/** The exploration module owns its cache lifecycle through the loader's existing event API. */
@EventBusSubscriber(modid = BRNQuest.MOD_ID)
public final class LocationLifecycle {
    private LocationLifecycle() {}

    @SubscribeEvent
    public static void onReloadListeners(AddReloadListenerEvent event) {
        // Clear when resources actually reload, rather than when listeners are merely collected.
        event.addListener((ResourceManagerReloadListener) resources -> LocationTargets.invalidate());
    }
}
