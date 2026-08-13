package yourscraft.jasdewstarfield.brnquest.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Immutable public task definition, including unknown external type configuration. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record TaskView(ResourceLocation bookId, ResourceLocation id, ResourceLocation typeId,
                       Map<String, String> config, boolean optional) {
    public TaskView {
        config = Map.copyOf(config);
    }
}
