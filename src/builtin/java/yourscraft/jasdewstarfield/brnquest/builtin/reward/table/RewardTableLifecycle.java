package yourscraft.jasdewstarfield.brnquest.builtin.reward.table;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
/** Built-in continuation and cache lifetime is independent of the core's player event wiring. */
@EventBusSubscriber(modid = "brnquest")
public final class RewardTableLifecycle {
    private RewardTableLifecycle() {}
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST) public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) RewardTableService.tick(player);
    }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST) public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) RewardTableService.logout(player);
    }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST) public static void stopped(ServerStoppedEvent event) { RewardTableService.clear(); }
}
