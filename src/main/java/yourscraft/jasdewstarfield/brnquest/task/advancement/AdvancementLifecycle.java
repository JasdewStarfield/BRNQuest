package yourscraft.jasdewstarfield.brnquest.task.advancement;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;

/** Events invalidate reads only: the existing server polling loop owns progress writes and synchronization. */
@EventBusSubscriber(modid = BRNQuest.MOD_ID)
public final class AdvancementLifecycle {
    private AdvancementLifecycle() {}
    @SubscribeEvent public static void changed(AdvancementEvent.AdvancementProgressEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) AdvancementTargets.invalidate(player);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) AdvancementTargets.invalidate(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) AdvancementTargets.invalidate(player);
    }
    @SubscribeEvent public static void reload(AddReloadListenerEvent event) {
        event.addListener((ResourceManagerReloadListener) resources -> AdvancementTargets.invalidate());
    }
}
