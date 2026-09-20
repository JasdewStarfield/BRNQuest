package yourscraft.jasdewstarfield.brnquest.builtin.observation.location;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.task.*;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import yourscraft.jasdewstarfield.brnquest.task.TargetGroups;
import java.util.*;

/** Dimension facade preserves callers while sharing group parsing with advancements. */
public final class DimensionGroups {
    private DimensionGroups() {}
    public record Result(Set<ResourceLocation> ids, String error) {
        public Result { ids = Set.copyOf(ids); }
    }
    public static Map<ResourceLocation, List<String>> read(ResourceManager resources) {
        return TargetGroups.read(resources, "dimension");
    }
    public static Result resolve(String selector, Map<ResourceLocation, List<String>> groups, Set<ResourceLocation> dimensions) {
        var result = TargetGroups.resolve(selector, groups, dimensions);
        return new Result(result.ids(), result.error());
    }
}
