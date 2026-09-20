package yourscraft.jasdewstarfield.brnquest.builtin.client;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
/** Client state is dropped on disconnect without loading client classes on a dedicated server. */
@EventBusSubscriber(modid = "brnquest", value = Dist.CLIENT)
public final class BuiltinClientLifecycle {
    private BuiltinClientLifecycle() {}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { RewardTableClientState.clear(); }
}
