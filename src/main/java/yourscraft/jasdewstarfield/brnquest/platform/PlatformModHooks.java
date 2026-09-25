package yourscraft.jasdewstarfield.brnquest.platform;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import yourscraft.jasdewstarfield.brnquest.author.EditSessionService;
import yourscraft.jasdewstarfield.brnquest.command.BrnQuestCommands;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookReloadListener;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;
import yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine;
import yourscraft.jasdewstarfield.brnquest.runtime.ExtensionRegistrationLifecycle;
import yourscraft.jasdewstarfield.brnquest.task.ItemTaskMonitor;
import yourscraft.jasdewstarfield.brnquest.workspace.WorkspaceDeploymentService;

/** NeoForge event wiring kept separate from loader-neutral model and runtime code. */
public final class PlatformModHooks {
    private PlatformModHooks() {}

    public static void register(IEventBus modEventBus, ModContainer container) {
        // Built-ins share the public plugin lifecycle and freeze boundary with external types.
        RuntimeModule.registerInstalled();
        yourscraft.jasdewstarfield.brnquest.owner.OwnerRuntime.register();
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Pre event) ->
                yourscraft.jasdewstarfield.brnquest.owner.OwnerRuntime.tick(event.getServer()));
        container.registerConfig(ModConfig.Type.CLIENT, BrnQuestClientConfig.SPEC);
        container.registerConfig(ModConfig.Type.SERVER, yourscraft.jasdewstarfield.brnquest.config.BrnQuestServerConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(PlatformModHooks::onReloadListeners);
        NeoForge.EVENT_BUS.addListener(PlatformModHooks::onCommands);
        NeoForge.EVENT_BUS.addListener(PlatformModHooks::onLogin);
        NeoForge.EVENT_BUS.addListener(PlatformModHooks::onLogout);
        NeoForge.EVENT_BUS.addListener(PlatformModHooks::onItemPickup);
        NeoForge.EVENT_BUS.addListener(PlatformModHooks::onItemCrafted);
        NeoForge.EVENT_BUS.addListener(PlatformModHooks::onItemDestroyed);
        NeoForge.EVENT_BUS.addListener(PlatformModHooks::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(PlatformModHooks::onServerStarted);
        NeoForge.EVENT_BUS.addListener(PlatformModHooks::onServerStopped);
        if (FMLEnvironment.dist == Dist.CLIENT) PlatformClientHooks.register(modEventBus);
    }

    private static void onReloadListeners(AddReloadListenerEvent event) {
        // Third-party construction/setup registration must finish before any book is decoded.
        ExtensionRegistrationLifecycle.freezeCommonAndScript();
        event.addListener(new QuestBookReloadListener());
    }
    private static void onServerStarted(ServerStartedEvent event) {
        yourscraft.jasdewstarfield.brnquest.owner.OwnerRuntime.started(event.getServer());
        // The initial resource load has already completed at this lifecycle point.
        // A newly copied workspace is therefore followed by one explicit reload.
        new WorkspaceDeploymentService().autoDeployAndReload(event.getServer());
    }
    private static void onCommands(RegisterCommandsEvent event) { BrnQuestCommands.register(event.getDispatcher()); }
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            yourscraft.jasdewstarfield.brnquest.owner.OwnerRuntime.login(player);
            ProgressEngine.get().reconcile(player);
            ItemTaskMonitor.mark(player);
            // The hello lets the client request a definition only when its cached
            // revision differs, avoiding an unconditional duplicate book transfer.
            BrnQuestNetwork.syncAll(player, true);
        }
    }
    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player && player.getServer() != null) {
            ItemTaskMonitor.forget(player);
            yourscraft.jasdewstarfield.brnquest.owner.OwnerRuntime.logout(player);
            // A remote administrator must not keep a server-side write lease after disconnecting.
            EditSessionService.get().releasePlayer(player.getServer(), player.getUUID());
        }
    }
    private static void onServerStopped(ServerStoppedEvent event) {
        ItemTaskMonitor.clear();
        yourscraft.jasdewstarfield.brnquest.owner.OwnerRuntime.stopped(event.getServer());
        EditSessionService.get().clearServer(event.getServer());
    }

    private static void onItemPickup(ItemEntityPickupEvent.Post event) { if (event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player) ItemTaskMonitor.mark(player); }
    private static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            // The crafted stack is authoritative provenance; inventory scanning cannot distinguish crafted output.
            ProgressEngine.get().recordCraft(player, event.getCrafting());
            ItemTaskMonitor.mark(player);
        }
    }
    private static void onItemDestroyed(PlayerDestroyItemEvent event) { if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) ItemTaskMonitor.mark(player); }
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ItemTaskMonitor.tick(player);
            ProgressEngine.get().tick(player);
        }
    }
}
