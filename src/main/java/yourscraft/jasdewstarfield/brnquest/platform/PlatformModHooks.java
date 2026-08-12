package yourscraft.jasdewstarfield.brnquest.platform;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import yourscraft.jasdewstarfield.brnquest.command.BrnQuestCommands;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookReloadListener;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;
import yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine;
import yourscraft.jasdewstarfield.brnquest.task.ItemTaskMonitor;
import yourscraft.jasdewstarfield.brnquest.workspace.WorkspaceDeploymentService;

/** NeoForge event wiring kept separate from loader-neutral model and runtime code. */
public final class PlatformModHooks {
    private PlatformModHooks() {}

    public static void register(IEventBus modEventBus, ModContainer container) {
        NeoForge.EVENT_BUS.addListener(PlatformModHooks::onReloadListeners);
        NeoForge.EVENT_BUS.addListener(PlatformModHooks::onCommands);
        NeoForge.EVENT_BUS.addListener(PlatformModHooks::onLogin);
        NeoForge.EVENT_BUS.addListener(PlatformModHooks::onItemPickup);
        NeoForge.EVENT_BUS.addListener(PlatformModHooks::onItemCrafted);
        NeoForge.EVENT_BUS.addListener(PlatformModHooks::onItemDestroyed);
        NeoForge.EVENT_BUS.addListener(PlatformModHooks::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(PlatformModHooks::onServerStarted);
        if (FMLEnvironment.dist == Dist.CLIENT) PlatformClientHooks.register(modEventBus);
    }

    private static void onReloadListeners(AddReloadListenerEvent event) { event.addListener(new QuestBookReloadListener()); }
    private static void onServerStarted(ServerStartedEvent event) {
        // The initial resource load has already completed at this lifecycle point.
        // A newly copied workspace is therefore followed by one explicit reload.
        new WorkspaceDeploymentService().autoDeployAndReload(event.getServer());
    }
    private static void onCommands(RegisterCommandsEvent event) { BrnQuestCommands.register(event.getDispatcher()); }
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ProgressEngine.get().reconcile(player);
            ItemTaskMonitor.mark(player);
            // The hello lets the client request a definition only when its cached
            // revision differs, avoiding an unconditional duplicate book transfer.
            BrnQuestNetwork.syncAll(player, true);
        }
    }

    private static void onItemPickup(ItemEntityPickupEvent.Post event) { if (event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player) ItemTaskMonitor.mark(player); }
    private static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) { if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) ItemTaskMonitor.mark(player); }
    private static void onItemDestroyed(PlayerDestroyItemEvent event) { if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) ItemTaskMonitor.mark(player); }
    private static void onPlayerTick(PlayerTickEvent.Post event) { if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) ItemTaskMonitor.tick(player); }
}
