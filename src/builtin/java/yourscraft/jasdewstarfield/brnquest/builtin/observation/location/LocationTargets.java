package yourscraft.jasdewstarfield.brnquest.builtin.observation.location;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.task.*;

import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import java.util.*;

/** Registry resolution and loaded-chunk-only sampling. All caches expire with server resources or player tick. */
public final class LocationTargets {
    private LocationTargets() {}
    public static void invalidate() { GROUPS.clear(); SAMPLES.clear(); }
    private static final Map<MinecraftServer, Groups> GROUPS = new WeakHashMap<>();
    private record Groups(Object resources, Map<ResourceLocation, List<String>> values) {}
    private static final Map<ServerPlayer, Sample> SAMPLES = new WeakHashMap<>();
    private record Sample(int tick, ResourceLocation dimension, BlockPos pos, Map<String, Boolean> matches) {}
    public static Map<ResourceLocation, List<String>> groups(MinecraftServer server) {
        // Reload replaces the resource-manager instance; selectors are resolved against current registries below.
        var resources = server.getResourceManager();
        var cached = GROUPS.get(server);
        if (cached == null || cached.resources() != resources) {
            cached = new Groups(resources, DimensionGroups.read(resources)); GROUPS.put(server, cached);
        }
        return cached.values();
    }
    public static DimensionGroups.Result resolve(ServerPlayer player, String kind, String selector) {
        if (kind.equals("dimension")) {
            Set<ResourceLocation> dimensions = new HashSet<>();
            player.server.levelKeys().forEach(key -> dimensions.add(key.location()));
            return DimensionGroups.resolve(selector, groups(player.server), dimensions);
        }
        var registry = player.registryAccess().registryOrThrow(kind.equals("biome") ? Registries.BIOME : Registries.STRUCTURE);
        return resolveRegistry(registry, selector);
    }
    private static <T> DimensionGroups.Result resolveRegistry(Registry<T> registry, String selector) {
        boolean tag = selector.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? selector.substring(1) : selector);
        if (id == null) return new DimensionGroups.Result(Set.of(), "Invalid selector: " + selector);
        Set<ResourceLocation> ids = new HashSet<>();
        if (tag) registry.getTag(TagKey.create(registry.key(), id)).ifPresent(set -> set.forEach(holder -> holder.unwrapKey().ifPresent(key -> ids.add(key.location()))));
        else if (registry.containsKey(id)) ids.add(id);
        return new DimensionGroups.Result(ids, ids.isEmpty() ? "Missing or empty selector: " + selector : "");
    }
    public static boolean matches(ServerPlayer player, String kind, LocationConfig config) {
        if (player.isSpectator()) return false;
        if (kind.equals("location") && !config.contains(player.blockPosition())) return false;
        if (kind.equals("location") && config.anyDimension()) return true;
        String registry = kind.equals("location") ? "dimension" : kind;
        var sample = SAMPLES.get(player);
        if (sample == null || sample.tick() != player.server.getTickCount() || !sample.dimension().equals(player.level().dimension().location())
                || !sample.pos().equals(player.blockPosition())) {
            sample = new Sample(player.server.getTickCount(), player.level().dimension().location(), player.blockPosition(), new HashMap<>());
            SAMPLES.put(player, sample);
        }
        return sample.matches().computeIfAbsent(registry + ":" + config.selector(), ignored -> {
            var ids = resolve(player, registry, config.selector()).ids();
            if (ids.isEmpty()) return false;
            if (registry.equals("dimension")) return ids.contains(player.level().dimension().location());
            var chunk = player.serverLevel().getChunkSource().getChunkNow(player.getBlockX() >> 4, player.getBlockZ() >> 4);
            if (chunk == null) return false;
            if (registry.equals("biome")) return player.level().getBiome(player.blockPosition()).unwrapKey().map(key -> ids.contains(key.location())).orElse(false);
            var structures = player.registryAccess().registryOrThrow(Registries.STRUCTURE);
            for (var id : ids) {
                Structure structure = structures.get(id);
                if (structure == null) continue;
                for (long reference : chunk.getReferencesForStructure(structure)) {
                    var startChunk = player.serverLevel().getChunkSource().getChunkNow(ChunkPos.getX(reference), ChunkPos.getZ(reference));
                    if (startChunk == null) continue;
                    var start = startChunk.getStartForStructure(structure);
                    if (start != null && start.isValid() && start.getPieces().stream().anyMatch(piece -> piece.getBoundingBox().isInside(player.blockPosition()))) return true;
                }
            }
            return false;
        });
    }
}
