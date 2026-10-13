package yourscraft.jasdewstarfield.brnquest.compat.imblocker;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Observe finished screen input/render state, including hidden fields and child screen transitions. */
@EventBusSubscriber(modid="brnquest",value=Dist.CLIENT)
public final class ImBlockerInputEvents {
    private ImBlockerInputEvents() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) { ImBlockerInputBridge.synchronize(); }
    @SubscribeEvent public static void render(ScreenEvent.Render.Post event) { ImBlockerInputBridge.synchronize(); }
    @SubscribeEvent public static void opening(ScreenEvent.Opening event) {
        if(!event.isCanceled() && event.getCurrentScreen()!=event.getNewScreen()) ImBlockerInputBridge.release();
    }
}
