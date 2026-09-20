package yourscraft.jasdewstarfield.brnquest.builtin.observation.advancement;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.task.*;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import yourscraft.jasdewstarfield.brnquest.task.TargetGroups;
import java.util.*;

/** Read-only resolution is shared by task checks, reward preflight and paged author choices. */
public final class AdvancementTargets {
    private AdvancementTargets() {}
    private record Catalog(ResourceManager resources, Object advancements, Map<ResourceLocation,List<String>> groups,
                           Set<ResourceLocation> ids) {}
    private static final Map<MinecraftServer, Catalog> CATALOGS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Map<AdvancementConfig,Boolean>> CHECKS = new WeakHashMap<>();
    public static void invalidate() { CATALOGS.clear(); CHECKS.clear(); }
    public static void invalidate(ServerPlayer player) { CHECKS.remove(player); }
    private static Catalog catalog(MinecraftServer server) {
        var result = CATALOGS.get(server);
        if (result == null || result.resources() != server.getResourceManager() || result.advancements() != server.getAdvancements()) {
            var ids = new HashSet<ResourceLocation>();
            server.getAdvancements().getAllAdvancements().forEach(holder -> ids.add(holder.id()));
            result = new Catalog(server.getResourceManager(), server.getAdvancements(),
                    TargetGroups.read(server.getResourceManager(), "advancement"), Set.copyOf(ids));
            CATALOGS.put(server,result);
            CHECKS.clear();
        }
        return result;
    }
    public static SortedSet<String> choices(MinecraftServer server) {
        var catalog = catalog(server);
        var choices = new TreeSet<String>();
        catalog.ids().forEach(id -> choices.add(id.toString()));
        catalog.groups().keySet().forEach(id -> choices.add("#"+id));
        return choices;
    }
    public static List<AdvancementHolder> resolve(MinecraftServer server, AdvancementConfig config) {
        var catalog = catalog(server);
        var result = TargetGroups.resolve(config.selector(), catalog.groups(), catalog.ids());
        if (!result.error().isEmpty()) throw new IllegalArgumentException(result.error());
        var holders = result.ids().stream().sorted().map(server.getAdvancements()::get).toList();
        // Validate the entire selection before the first reward side effect.
        for (var holder : holders) {
            if (holder == null) throw new IllegalArgumentException("Advancement disappeared during resolution");
            if (!config.criterion().isEmpty() && !holder.value().criteria().containsKey(config.criterion()))
                throw new IllegalArgumentException("Unknown criterion: " + config.criterion());
        }
        return holders;
    }
    public static boolean matches(ServerPlayer player, AdvancementConfig config) {
        catalog(player.server);
        return CHECKS.computeIfAbsent(player, ignored -> new HashMap<>()).computeIfAbsent(config, ignored -> {
            try {
                var holders = resolve(player.server, config);
                return config.all() ? holders.stream().allMatch(holder -> done(player,holder,config.criterion()))
                        : holders.stream().anyMatch(holder -> done(player,holder,config.criterion()));
            } catch (IllegalArgumentException error) { return false; }
        });
    }
    private static boolean done(ServerPlayer player, AdvancementHolder holder, String criterion) {
        var progress = player.getAdvancements().getOrStartProgress(holder);
        return criterion.isEmpty() ? progress.isDone() : progress.getCriterion(criterion).isDone();
    }
}
