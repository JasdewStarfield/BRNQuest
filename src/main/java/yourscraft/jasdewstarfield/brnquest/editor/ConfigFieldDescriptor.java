package yourscraft.jasdewstarfield.brnquest.editor;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/** Immutable metadata for one schema-1 string-map field. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record ConfigFieldDescriptor(String key, ConfigValueType valueType, boolean required,
                                    Optional<String> defaultValue, OptionalDouble minimum,
                                    OptionalDouble maximum, List<String> allowedValues,
                                    Optional<ResourceLocation> resourceRegistry, String helpText,
                                    Optional<ConfigFieldValidator> validator, String labelKey,
                                    java.util.Map<String, String> valueLabelKeys) {
    public ConfigFieldDescriptor {
        key = Objects.requireNonNull(key, "key");
        if (key.isBlank()) throw new IllegalArgumentException("Field key must not be blank");
        valueType = Objects.requireNonNull(valueType, "valueType");
        defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        minimum = Objects.requireNonNull(minimum, "minimum");
        maximum = Objects.requireNonNull(maximum, "maximum");
        allowedValues = List.copyOf(allowedValues);
        resourceRegistry = Objects.requireNonNull(resourceRegistry, "resourceRegistry");
        helpText = Objects.requireNonNull(helpText, "helpText");
        validator = Objects.requireNonNull(validator, "validator");
        labelKey = Objects.requireNonNull(labelKey, "labelKey");
        valueLabelKeys = java.util.Map.copyOf(valueLabelKeys);
        if (minimum.isPresent() && maximum.isPresent() && minimum.getAsDouble() > maximum.getAsDouble()) {
            throw new IllegalArgumentException("Field minimum exceeds maximum: " + key);
        }
        if (valueType == ConfigValueType.ENUM && allowedValues.isEmpty()) {
            throw new IllegalArgumentException("Enum field requires allowed values: " + key);
        }
    }

    /** Compatibility constructor: existing extensions keep raw value labels and legacy field labels. */
    public ConfigFieldDescriptor(String key, ConfigValueType valueType, boolean required,
                                 Optional<String> defaultValue, OptionalDouble minimum, OptionalDouble maximum,
                                 List<String> allowedValues, Optional<ResourceLocation> resourceRegistry,
                                 String helpText, Optional<ConfigFieldValidator> validator) {
        this(key, valueType, required, defaultValue, minimum, maximum, allowedValues, resourceRegistry,
                helpText, validator, "", java.util.Map.of());
    }

    /** Translation keys belong to the declaring type; saved field names and enum values stay unchanged. */
    public ConfigFieldDescriptor withLabel(String translationKey) {
        return new ConfigFieldDescriptor(key, valueType, required, defaultValue, minimum, maximum,
                allowedValues, resourceRegistry, helpText, validator, translationKey, valueLabelKeys);
    }

    public ConfigFieldDescriptor withValueLabels(java.util.Map<String, String> translationKeys) {
        return new ConfigFieldDescriptor(key, valueType, required, defaultValue, minimum, maximum,
                allowedValues, resourceRegistry, helpText, validator, labelKey, translationKeys);
    }

    public static ConfigFieldDescriptor field(String key, ConfigValueType type) {
        return new ConfigFieldDescriptor(key, type, false, Optional.empty(), OptionalDouble.empty(),
                OptionalDouble.empty(), List.of(), Optional.empty(), "", Optional.empty());
    }

    public static ConfigFieldDescriptor enumeration(String key, List<String> values) {
        return new ConfigFieldDescriptor(key, ConfigValueType.ENUM, false, Optional.empty(), OptionalDouble.empty(),
                OptionalDouble.empty(), values, Optional.empty(), "", Optional.empty());
    }

    public ConfigFieldDescriptor asRequired() {
        return copy(true, defaultValue, minimum, maximum, allowedValues, resourceRegistry, helpText, validator);
    }

    public ConfigFieldDescriptor withDefault(String value) {
        return copy(required, Optional.of(value), minimum, maximum, allowedValues, resourceRegistry, helpText, validator);
    }

    public ConfigFieldDescriptor withRange(double min, double max) {
        return copy(required, defaultValue, OptionalDouble.of(min), OptionalDouble.of(max), allowedValues,
                resourceRegistry, helpText, validator);
    }

    public ConfigFieldDescriptor withAllowedValues(List<String> values) {
        return copy(required, defaultValue, minimum, maximum, values, resourceRegistry, helpText, validator);
    }

    public ConfigFieldDescriptor withResourceRegistry(ResourceLocation registry) {
        return copy(required, defaultValue, minimum, maximum, allowedValues, Optional.of(registry), helpText, validator);
    }

    public ConfigFieldDescriptor withHelp(String text) {
        return copy(required, defaultValue, minimum, maximum, allowedValues, resourceRegistry, text, validator);
    }

    public ConfigFieldDescriptor withValidator(ConfigFieldValidator fieldValidator) {
        return copy(required, defaultValue, minimum, maximum, allowedValues, resourceRegistry, helpText,
                Optional.of(fieldValidator));
    }

    private ConfigFieldDescriptor copy(boolean nextRequired, Optional<String> nextDefault, OptionalDouble nextMinimum,
                                       OptionalDouble nextMaximum, List<String> nextAllowed,
                                       Optional<ResourceLocation> nextRegistry, String nextHelp,
                                       Optional<ConfigFieldValidator> nextValidator) {
        return new ConfigFieldDescriptor(key, valueType, nextRequired, nextDefault, nextMinimum, nextMaximum,
                nextAllowed, nextRegistry, nextHelp, nextValidator, labelKey, valueLabelKeys);
    }
}
