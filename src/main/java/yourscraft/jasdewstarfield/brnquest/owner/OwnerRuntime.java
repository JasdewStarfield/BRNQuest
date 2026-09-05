package yourscraft.jasdewstarfield.brnquest.owner;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.event.BrnQuestEvents;
import yourscraft.jasdewstarfield.brnquest.event.ProgressOwnerChangedEvent;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;
import yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine;
import yourscraft.jasdewstarfield.brnquest.progress.QuestProgressData;
import java.util.*;

/** Server-owned reconciliation state; snapshots are hints, never authorization. */
public final class OwnerRuntime {
    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();
    private static final ResourceLocation OPAC = ResourceLocation.fromNamespaceAndPath("brnquest", "openpac");
    private OwnerRuntime() {}
    public static void register() {
        var mod = ModList.get().getModContainerById("openpartiesandclaims");
        if (mod.isEmpty()) return;
        try {
            // Runtime compatibility is established by loading the guarded adapter, not a brittle version whitelist.
            var provider = (ProgressOwnerProvider) Class.forName(
                    "yourscraft.jasdewstarfield.brnquest.compat.opac.OpenPacProgressOwnerProvider")
                    .getConstructor().newInstance();
            ProgressOwnerProviderRegistry.register(provider);
            BRNQuest.LOGGER.info("[BRNQuest/OPAC] Adapter enabled for OPAC {}",
                    mod.get().getModInfo().getVersion());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            BRNQuest.LOGGER.error("[BRNQuest/OPAC] Adapter unavailable; using personal owners", exception);
        }
    }
    public static void started(MinecraftServer server) {
        if (ProgressOwnerProviderRegistry.get(OPAC) == null) return;
        int hooks = Integer.getInteger("brnquest.opac.hookCount", 0);
        if (hooks < 5) BRNQuest.LOGGER.warn("[BRNQuest/OPAC] Hook health degraded ({} sites); login/write queries and 100-tick polling remain active", hooks);
        else BRNQuest.LOGGER.info("[BRNQuest/OPAC] Hook health OK ({} sites); authoritative polling active", hooks);
    }
    public static void queryFailed(MinecraftServer server, Throwable exception) {
        State state = STATES.computeIfAbsent(server, ignored -> new State());
        if (!state.warned) BRNQuest.LOGGER.warn("[BRNQuest/OPAC] Query failed; using personal history until recovery", exception);
        state.warned = true;
    }
    public static void login(ServerPlayer player) { refresh(player, false); }
    public static void tick(MinecraftServer server) {
        State state = STATES.computeIfAbsent(server, ignored -> new State());
        long generation = OwnerInvalidation.generation();
        if (state.generation == generation && server.getTickCount() % 100 != 0) return;
        state.generation = generation;
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) refresh(player, true);
        var provider = ProgressOwnerProviderRegistry.get(OPAC);
        if (provider == null) return;
        var data = QuestProgressData.get(server);
        try {
            // Include offline/orphan ledgers, so disbanding while nobody is online still archives them.
            for (var id : data.ownerIds()) if (id.providerId().equals(OPAC)) {
                var lifecycle = provider.lifecycle(server, id);
                data.observe(id, lifecycle == ProgressOwnerLifecycle.ACTIVE ? provider.members(server, id) : Set.of(), lifecycle);
            }
            state.warned = false;
        } catch (RuntimeException | LinkageError exception) {
            if (!state.warned) BRNQuest.LOGGER.warn("[BRNQuest/OPAC] Query failed; personal fallback, archived data retained", exception);
            state.warned = true;
        }
    }
    private static void refresh(ServerPlayer player, boolean synchronize) {
        var server = player.getServer();
        State state = STATES.computeIfAbsent(server, ignored -> new State());
        var view = ProgressOwnerService.resolve(player).orElseThrow();
        var previous = state.players.put(player.getUUID(), view);
        QuestProgressData.get(server).observe(view.id(), view.members(), view.lifecycle());
        if (!view.equals(previous)) {
            ProgressEngine.get().reconcile(player);
            yourscraft.jasdewstarfield.brnquest.task.ItemTaskMonitor.mark(player);
            if (previous != null && !previous.id().equals(view.id())) BrnQuestEvents.post(
                    new ProgressOwnerChangedEvent(player.getUUID(), previous.id(), view.id()));
            if (synchronize) BrnQuestNetwork.syncProgress(player, false);
        }
    }
    public static void logout(ServerPlayer player) {
        State state = STATES.get(player.getServer());
        if (state != null) state.players.remove(player.getUUID());
    }
    public static void stopped(MinecraftServer server) { STATES.remove(server); }
    private static final class State {
        private final Map<UUID, ProgressOwnerView> players = new HashMap<>();
        private long generation = -1;
        private boolean warned;
    }
}
