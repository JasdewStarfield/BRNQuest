package yourscraft.jasdewstarfield.brnquest.editor;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds description-driven editor schemas without branching on built-in type IDs. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public final class ConfigEditorSchemas {
    private ConfigEditorSchemas() {}

    public static ConfigEditorSchema forTask(TaskView task) {
        var type = TaskTypeRegistry.get(task.typeId());
        List<ConfigFieldDescriptor> fields = type == null ? List.of() : fields(type::configFields, task.typeId());
        return schema(ConfigEditorSchema.Kind.TASK, task.typeId(), fields, task.config());
    }

    public static ConfigEditorSchema forReward(RewardView reward) {
        var type = RewardTypeRegistry.get(reward.typeId());
        List<ConfigFieldDescriptor> fields = type == null ? List.of() : fields(type::configFields, reward.typeId());
        return schema(ConfigEditorSchema.Kind.REWARD, reward.typeId(), fields, reward.config());
    }

    /** Public for editor previews and extension contract tests. */
    public static List<ConfigFieldIssue> validate(List<ConfigFieldDescriptor> fields, Map<String, String> config) {
        List<ConfigFieldIssue> issues = new ArrayList<>();
        Map<String, String> immutableConfig = Map.copyOf(config);
        for (ConfigFieldDescriptor field : List.copyOf(fields)) {
            String value = immutableConfig.get(field.key());
            if ((value == null || value.isBlank()) && field.required() && field.defaultValue().isEmpty()) {
                issues.add(issue(field, "REQUIRED", "Required field is missing"));
                continue;
            }
            if (value == null || value.isBlank()) value = field.defaultValue().orElse("");
            if (value.isBlank()) continue;
            validateShape(field, value, issues);
            if (field.validator().isPresent()) {
                try {
                    List<ConfigFieldIssue> custom = field.validator().orElseThrow().validate(value, immutableConfig);
                    if (custom != null) issues.addAll(List.copyOf(custom));
                } catch (RuntimeException | LinkageError exception) {
                    issues.add(issue(field, "VALIDATOR_FAILED", "Field validator failed: " + exception.getClass().getSimpleName()));
                }
            }
        }
        return List.copyOf(issues);
    }

    private static ConfigEditorSchema schema(ConfigEditorSchema.Kind kind, ResourceLocation typeId,
                                             List<ConfigFieldDescriptor> fields, Map<String, String> config) {
        List<ConfigFieldDescriptor> immutableFields = fields == null ? List.of() : List.copyOf(fields);
        return new ConfigEditorSchema(kind, typeId, immutableFields, config,
                validate(immutableFields, config), immutableFields.isEmpty());
    }

    private static List<ConfigFieldDescriptor> fields(java.util.function.Supplier<List<ConfigFieldDescriptor>> supplier,
                                                      ResourceLocation typeId) {
        try {
            List<ConfigFieldDescriptor> supplied = supplier.get();
            return supplied == null ? List.of() : List.copyOf(supplied);
        } catch (RuntimeException | LinkageError exception) {
            // Broken optional metadata falls back to the lossless raw map; runtime Codec behavior is untouched.
            BRNQuest.LOGGER.error("[BRNQuest/EDITOR] Field metadata failed for {}; using raw config", typeId, exception);
            return List.of();
        }
    }

    private static void validateShape(ConfigFieldDescriptor field, String value, List<ConfigFieldIssue> issues) {
        try {
            switch (field.valueType()) {
                case BOOLEAN -> {
                    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")
                            && !value.equalsIgnoreCase("1b") && !value.equalsIgnoreCase("0b")) {
                        throw new IllegalArgumentException();
                    }
                }
                case INTEGER -> range(field, Long.parseLong(value.replaceAll("[bBsSlL]$", "")), issues);
                case DECIMAL -> range(field, Double.parseDouble(value.replaceAll("[fFdD]$", "")), issues);
                case ENUM -> {
                    if (!field.allowedValues().contains(value)) issues.add(issue(field, "ENUM", "Value is not allowed"));
                }
                case RESOURCE_LOCATION -> {
                    if (ResourceLocation.tryParse(value) == null) throw new IllegalArgumentException();
                }
                case TEXT, ITEM_STACK -> { /* Codec and publish validation retain final authority. */ }
            }
        } catch (IllegalArgumentException exception) {
            issues.add(issue(field, "TYPE", "Value does not match " + field.valueType()));
        }
    }

    private static void range(ConfigFieldDescriptor field, double value, List<ConfigFieldIssue> issues) {
        if (field.minimum().isPresent() && value < field.minimum().getAsDouble()) {
            issues.add(issue(field, "MINIMUM", "Value is below minimum " + field.minimum().getAsDouble()));
        }
        if (field.maximum().isPresent() && value > field.maximum().getAsDouble()) {
            issues.add(issue(field, "MAXIMUM", "Value exceeds maximum " + field.maximum().getAsDouble()));
        }
    }

    private static ConfigFieldIssue issue(ConfigFieldDescriptor field, String code, String message) {
        return new ConfigFieldIssue(field.key(), ConfigFieldIssue.Severity.ERROR, code, message);
    }
}
