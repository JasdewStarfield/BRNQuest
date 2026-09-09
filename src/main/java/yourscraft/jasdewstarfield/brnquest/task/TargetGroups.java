package yourscraft.jasdewstarfield.brnquest.task;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import java.util.*;

/** Shared BRNQuest group format for targets without native registry tags. */
public final class TargetGroups {
    private TargetGroups() {}
    public record Result(Set<ResourceLocation> ids, String error) {
        public Result { ids = Set.copyOf(ids); }
    }
    public static Map<ResourceLocation, List<String>> read(ResourceManager resources, String kind) {
        Map<ResourceLocation, List<String>> groups = new TreeMap<>();
        resources.listResourceStacks("brnquest/target_groups/" + kind, id -> id.getPath().endsWith(".json")).forEach((file, stack) -> {
            String path = file.getPath();
            var id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path.substring(("brnquest/target_groups/" + kind + "/").length(), path.length() - 5));
            List<String> values = new ArrayList<>();
            for (var resource : stack) {
                try (var reader = resource.openAsReader()) {
                    var json = JsonParser.parseReader(reader).getAsJsonObject();
                    if (json.has("replace") && (!json.get("replace").isJsonPrimitive() || !json.get("replace").getAsJsonPrimitive().isBoolean()))
                        throw new IllegalArgumentException("replace must be boolean");
                    if (json.has("replace") && json.get("replace").getAsBoolean()) values.clear();
                    for (var entry : json.getAsJsonArray("values")) {
                        if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Group values must be ID strings");
                        values.add(entry.getAsString());
                    }
                } catch (Exception error) { values.clear(); values.add("invalid group document " + id); /* A later replacing pack may repair this group. */ }
            }
            groups.put(id, List.copyOf(values));
        });
        return Map.copyOf(groups);
    }
    public static Result resolve(String selector, Map<ResourceLocation, List<String>> groups, Set<ResourceLocation> targets) {
        try {
            Set<ResourceLocation> result = new LinkedHashSet<>();
            expand(selector, groups, targets, new HashSet<>(), result);
            if (result.isEmpty()) throw new IllegalArgumentException("Empty target group: " + selector);
            return new Result(result, "");
        } catch (IllegalArgumentException error) { return new Result(Set.of(), error.getMessage()); }
    }
    private static void expand(String selector, Map<ResourceLocation, List<String>> groups, Set<ResourceLocation> targets,
                               Set<ResourceLocation> visiting, Set<ResourceLocation> result) {
        boolean group = selector.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(group ? selector.substring(1) : selector);
        if (id == null) throw new IllegalArgumentException("Invalid target selector: " + selector);
        if (!group) {
            if (!targets.contains(id)) throw new IllegalArgumentException("Missing target: " + id);
            if (!result.add(id)) throw new IllegalArgumentException("Duplicate target: " + id);
            return;
        }
        if (visiting.size() >= 32 || !visiting.add(id)) throw new IllegalArgumentException("Cyclic or too deep target group: " + id);
        var entries = groups.get(id);
        if (entries == null) throw new IllegalArgumentException("Missing target group: " + id);
        if (entries.isEmpty()) throw new IllegalArgumentException("Empty target group: " + id);
        for (String entry : entries) expand(entry, groups, targets, visiting, result);
        visiting.remove(id);
    }
}
