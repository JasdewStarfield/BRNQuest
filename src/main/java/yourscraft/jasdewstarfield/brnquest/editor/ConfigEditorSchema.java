package yourscraft.jasdewstarfield.brnquest.editor;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable editor projection that always retains the original raw string map. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record ConfigEditorSchema(Kind kind, ResourceLocation typeId, List<ConfigFieldDescriptor> fields,
                                 Map<String, String> rawConfig, List<ConfigFieldIssue> issues,
                                 boolean rawFallback) {
    public ConfigEditorSchema {
        kind = Objects.requireNonNull(kind, "kind");
        typeId = Objects.requireNonNull(typeId, "typeId");
        fields = List.copyOf(fields);
        rawConfig = Map.copyOf(rawConfig);
        issues = List.copyOf(issues);
    }

    public enum Kind { TASK, REWARD }
}
