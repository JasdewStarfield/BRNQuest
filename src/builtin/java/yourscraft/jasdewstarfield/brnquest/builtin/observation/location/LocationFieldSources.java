package yourscraft.jasdewstarfield.brnquest.builtin.observation.location;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.task.*;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.editor.ServerFieldSources;
import java.util.*;

/** Registry-backed search keeps IDs, tags and BRNQuest dimension groups distinct in the editor. */
public final class LocationFieldSources {
    private LocationFieldSources() {}
    public static void register(yourscraft.jasdewstarfield.brnquest.extension.BrnQuestExtensionRegistrar registrar) {
        for (String kind : List.of("dimension", "biome", "structure")) registrar.fieldSource(ResourceLocation.parse("brnquest:" + kind), new ServerFieldSources.Source() {
            public ServerFieldSources.Result query(net.minecraft.server.level.ServerPlayer player, String filter, String selected) {
                return queryPage(player, filter, selected, 0);
            }
            public ServerFieldSources.Result queryPage(net.minecraft.server.level.ServerPlayer player, String filter, String selected, int offset) {
                var candidates = new TreeSet<String>();
                if (kind.equals("dimension")) {
                    player.server.levelKeys().forEach(key -> candidates.add(key.location().toString()));
                    LocationTargets.groups(player.server).keySet().forEach(id -> candidates.add("#" + id));
                } else {
                    var registry = player.registryAccess().registryOrThrow(kind.equals("biome") ? Registries.BIOME : Registries.STRUCTURE);
                    registry.keySet().forEach(id -> candidates.add(id.toString()));
                    registry.getTagNames().forEach(tag -> candidates.add("#" + tag.location()));
                }
                var matching = candidates.stream().filter(value -> value.contains(filter.toLowerCase(Locale.ROOT))).toList();
                // Resolve only the requested page; total still describes every matching candidate.
                var entries = matching.stream().skip(offset).limit(ServerFieldSources.PAGE_SIZE).map(value -> new ServerFieldSources.Entry(value, LocationTargets.resolve(player, kind, value).ids().size())).toList();
                var resolution = selected.isBlank() ? new DimensionGroups.Result(Set.of(), "") : LocationTargets.resolve(player, kind, selected);
                String current = kind.equals("dimension") ? player.level().dimension().location().toString() : kind.equals("biome")
                        ? player.level().getBiome(player.blockPosition()).unwrapKey().map(key -> key.location().toString()).orElse("") : "";
                return new ServerFieldSources.Result(entries, matching.size(), resolution.ids().size(), resolution.error().isEmpty() ? "" : "missing", current, resolution.error());
            }
        });
        registrar.fieldSource(ResourceLocation.parse("brnquest:position"), (player, filter, selected) -> {
            String position = player.getBlockX() + "," + player.getBlockY() + "," + player.getBlockZ();
            String error = "";
            try { LocationConfig.vector(selected); } catch (IllegalArgumentException ignored) { error = "vector"; }
            return new ServerFieldSources.Result(List.of(new ServerFieldSources.Entry(position, 1)),1, error.isEmpty() ? 1 : 0,error, position);
        });
    }
}
