package yourscraft.jasdewstarfield.brnquest.editor;

import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.*;

/** One registration supplies instance controls, creation-default controls, validation and storage projection. */
@ApiStatus(ApiStability.INTERNAL)
public final class QuestPropertySchema {
    public enum Storage { APPEARANCE, BEHAVIOR, EXTENSION }
    public record Property(ConfigFieldDescriptor field, String section, Storage storage,
                           String extensionKey, boolean creationDefault) {
        public Property {
            Objects.requireNonNull(field); Objects.requireNonNull(section); Objects.requireNonNull(storage);
            if (field.defaultValue().isEmpty()) throw new IllegalArgumentException("Property needs a core default: " + field.key());
            if (storage == Storage.EXTENSION && (extensionKey == null || extensionKey.isBlank()))
                throw new IllegalArgumentException("Extension property needs a storage key");
        }
        public String key() { return field.key(); }
        public String coreDefault() { return field.defaultValue().orElseThrow(); }
    }

    /** An immutable catalog permits isolated registration tests without changing the running mod's schema. */
    public static final class Catalog {
        private final List<Property> properties;
        private final Map<String, Property> byKey;
        public Catalog(List<Property> registrations) {
            var keys = new LinkedHashMap<String, Property>();
            for (var property : registrations)
                if (keys.putIfAbsent(property.key(), property) != null)
                    throw new IllegalArgumentException("Duplicate quest property: " + property.key());
            properties = List.copyOf(keys.values()); byKey = Map.copyOf(keys);
            // Validate the complete core configuration so cross-field validators see every declared default.
            var core = new TreeMap<String, String>();
            properties.forEach(property -> core.put(property.key(), property.coreDefault()));
            var canonical = normalize(core, properties);
            properties.forEach(property -> {
                if (!property.coreDefault().equals(canonical.get(property.key())))
                    throw new IllegalArgumentException("Non-canonical property default: " + property.key());
            });
        }
        public List<Property> properties() { return properties; }
        public List<Property> defaults() { return properties.stream().filter(Property::creationDefault).toList(); }
        public Property property(String key) {
            var property = byKey.get(key);
            if (property == null) throw new IllegalArgumentException("Unknown quest property: " + key);
            return property;
        }
        /** Group membership and order come from registration, including any newly declared property. */
        public Map<String, List<Property>> sections(List<Property> fields) {
            var sections = new LinkedHashMap<String, List<Property>>();
            fields.forEach(property -> sections.computeIfAbsent(property.section(), ignored -> new ArrayList<>()).add(property));
            var result = new LinkedHashMap<String, List<Property>>();
            sections.forEach((section, members) -> result.put(section, List.copyOf(members)));
            return Collections.unmodifiableMap(result);
        }
        public Map<String, String> normalize(Map<String, String> input, List<Property> fields) {
            var allowed = new HashMap<String, Property>();
            fields.forEach(property -> allowed.put(property.key(), property));
            var normalized = new TreeMap<String, String>();
            input.forEach((key, value) -> {
                var property = allowed.get(key);
                if (property == null || value == null) throw new IllegalArgumentException("Unknown property: " + key);
                String canonical = switch (property.field().valueType()) {
                    case BOOLEAN -> {
                        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Expected boolean: " + key);
                        yield value;
                    }
                    case INTEGER -> Integer.toString(Integer.parseInt(value));
                    case DECIMAL -> {
                        double number = Double.parseDouble(value);
                        if (!Double.isFinite(number)) throw new IllegalArgumentException("Non-finite property: " + key);
                        yield Double.toString(number);
                    }
                    case ENUM -> key.equals("quest_name_position") ? value.toLowerCase(Locale.ROOT) : value;
                    case TEXT -> {
                        if (property.field().required() && value.isBlank()) throw new IllegalArgumentException("Empty property: " + key);
                        yield value;
                    }
                    default -> throw new IllegalArgumentException("Unsupported common property type: " + key);
                };
                normalized.put(key, canonical);
            });
            var issues = ConfigEditorSchemas.validate(fields.stream().map(Property::field).toList(), normalized);
            if (issues.stream().anyMatch(issue -> issue.severity() == ConfigFieldIssue.Severity.ERROR))
                throw new IllegalArgumentException("Invalid property: " + issues.getFirst().fieldKey());
            return Collections.unmodifiableMap(normalized);
        }
        public JsonObject json(Map<String, String> values) {
            var json = new JsonObject();
            values.forEach((key, value) -> {
                switch (property(key).field().valueType()) {
                    case BOOLEAN -> json.addProperty(key, Boolean.parseBoolean(value));
                    case INTEGER -> json.addProperty(key, Integer.parseInt(value));
                    case DECIMAL -> json.addProperty(key, Double.parseDouble(value));
                    default -> json.addProperty(key, value);
                }
            });
            return json;
        }
        /** Missing fields leave existing extensions intact; core-default values remove only their owned keys. */
        public Map<String, String> extensions(Map<String, String> existing, Map<String, String> changes) {
            var fields = properties.stream().filter(property -> property.storage() == Storage.EXTENSION).toList();
            var selected = new TreeMap<String, String>();
            fields.forEach(property -> { if (changes.containsKey(property.key())) selected.put(property.key(), changes.get(property.key())); });
            var valid = normalize(selected, fields);
            var result = new TreeMap<>(existing);
            fields.forEach(property -> {
                if (!valid.containsKey(property.key())) return;
                String value = valid.get(property.key());
                if (value.equals(property.coreDefault())) result.remove(property.extensionKey());
                else result.put(property.extensionKey(), value);
            });
            return Map.copyOf(result);
        }
    }

    public static final Catalog CATALOG = new Catalog(registrations());
    private QuestPropertySchema() {}

    private static List<Property> registrations() {
        var fields = new ArrayList<Property>();
        fields.add(property(ConfigFieldDescriptor.field("shape", ConfigValueType.TEXT).asRequired().withDefault("chamfer")
                .withAllowedValues(List.of("chamfer", "square", "circle", "diamond")).withValueLabels(labels("screen.brnquest.editor.value.shape.",
                        List.of("chamfer", "square", "circle", "diamond"))), "appearance", Storage.APPEARANCE));
        for (String key : List.of("size", "icon_scale", "min_width"))
            fields.add(property(ConfigFieldDescriptor.field(key, ConfigValueType.DECIMAL).withDefault(key.equals("min_width") ? "0.0" : "1.0")
                    .withRange(key.equals("min_width") ? 0 : Double.MIN_VALUE, Double.MAX_VALUE), "appearance", Storage.APPEARANCE));
        fields.add(property(ConfigFieldDescriptor.enumeration("quest_name_position", List.of("inherit", "off", "above", "below"))
                .withDefault("inherit").withHelp("screen.brnquest.defaults.policy.client")
                .withValueLabels(labels("screen.brnquest.display.name.", List.of("inherit", "off", "above", "below"))),
                "appearance", Storage.EXTENSION));
        for (String key : List.of("hide_until_dependencies_visible", "hide_until_dependencies_complete", "invisible_until_complete"))
            fields.add(flag(key, "visibility", Storage.BEHAVIOR, false));
        fields.add(integer("visible_after_tasks", "visibility"));
        for (String key : List.of("hide_details_until_startable", "hide_text_until_complete", "hide_lock_icon"))
            fields.add(flag(key, "visibility", Storage.BEHAVIOR, false));
        var modes = Arrays.stream(DependencyRequirement.values()).map(DependencyRequirement::serializedName).toList();
        fields.add(property(ConfigFieldDescriptor.enumeration("dependency_requirement", modes).withDefault("all_completed")
                .withValueLabels(labels("screen.brnquest.defaults.dependency.", modes)), "dependencies", Storage.BEHAVIOR));
        fields.add(integer("minimum_required_dependencies", "dependencies"));
        // This live chapter-inheritance policy already has a chapter control; it is not a creation snapshot.
        fields.add(new Property(ConfigFieldDescriptor.enumeration("hide_dependency_lines", List.of("default", "true", "false"))
                .withDefault("default").withLabel("screen.brnquest.quest.hide_dependency_lines")
                .withHelp("screen.brnquest.defaults.policy.chapter")
                .withValueLabels(Map.of("default", "screen.brnquest.dependency_lines.inherit", "true", "screen.brnquest.dependency_lines.hide",
                        "false", "screen.brnquest.dependency_lines.show")), "dependencies", Storage.APPEARANCE, "", false));
        fields.add(flag("sequential_tasks", "completion", Storage.BEHAVIOR, false));
        fields.add(flag("repeatable", "completion", Storage.BEHAVIOR, false));
        fields.add(integer("repeat_cooldown_seconds", "completion"));
        fields.add(flag("ignore_reward_blocking", "completion", Storage.BEHAVIOR, false));
        fields.add(flag("require_all_team_members", "completion", Storage.BEHAVIOR, false));
        var notifications = QuestNotificationPolicy.DEFAULT.fields();
        for (String key : List.of(QuestNotificationPolicy.TASK, QuestNotificationPolicy.QUEST, QuestNotificationPolicy.REWARD))
            fields.add(flag(key, "notifications", Storage.EXTENSION, Boolean.parseBoolean(notifications.get(key))));
        return List.copyOf(fields);
    }
    private static Property flag(String key, String section, Storage storage, boolean value) {
        return property(ConfigFieldDescriptor.field(key, ConfigValueType.BOOLEAN).withDefault(Boolean.toString(value)), section, storage);
    }
    private static Property integer(String key, String section) {
        return property(ConfigFieldDescriptor.field(key, ConfigValueType.INTEGER).withDefault("0").withRange(0, Integer.MAX_VALUE), section, Storage.BEHAVIOR);
    }
    private static Property property(ConfigFieldDescriptor field, String section, Storage storage) {
        return new Property(field.withLabel("screen.brnquest.defaults." + field.key()), section, storage,
                storage == Storage.EXTENSION ? "brnquest:" + field.key() : "", true);
    }
    private static Map<String, String> labels(String prefix, List<String> values) {
        var labels = new LinkedHashMap<String, String>();
        values.forEach(value -> labels.put(value, prefix + value));
        return Map.copyOf(labels);
    }

    public static List<Property> instance(String section) {
        return CATALOG.properties().stream().filter(property -> section == null || property.section().equals(section)).toList();
    }
    public static Map<String, String> values(QuestAppearance appearance, QuestBehavior behavior, Map<String, String> extensions) {
        JsonObject appearanceJson = encode(QuestAppearance.CODEC, appearance), behaviorJson = encode(QuestBehavior.CODEC, behavior);
        var result = new TreeMap<String, String>();
        for (var property : CATALOG.properties()) {
            var json = property.storage() == Storage.APPEARANCE ? appearanceJson : behaviorJson;
            result.put(property.key(), property.storage() == Storage.EXTENSION
                    ? extensions.getOrDefault(property.extensionKey(), property.coreDefault())
                    : json.has(property.key()) ? json.get(property.key()).getAsString() : property.coreDefault());
        }
        return Map.copyOf(result);
    }
    public static QuestAppearance appearance(Map<String, String> values) { return decode(QuestAppearance.CODEC, projection(values, Storage.APPEARANCE)); }
    public static QuestBehavior behavior(Map<String, String> values) { return decode(QuestBehavior.CODEC, projection(values, Storage.BEHAVIOR)); }
    private static JsonObject projection(Map<String, String> values, Storage storage) {
        var selected = new TreeMap<String, String>();
        var fields = CATALOG.properties().stream().filter(property -> property.storage() == storage).toList();
        fields.forEach(property -> {
            String value = values.getOrDefault(property.key(), property.coreDefault());
            if (!property.key().equals("hide_dependency_lines") || !value.equals("default")) selected.put(property.key(), value);
        });
        var json = CATALOG.json(CATALOG.normalize(selected, fields));
        // Its task control is tri-state while the native appearance codec stores an optional boolean.
        if (json.has("hide_dependency_lines")) json.addProperty("hide_dependency_lines", Boolean.parseBoolean(json.get("hide_dependency_lines").getAsString()));
        return json;
    }
    private static <T> JsonObject encode(Codec<T> codec, T value) { return codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow().getAsJsonObject(); }
    private static <T> T decode(Codec<T> codec, JsonObject value) { return codec.parse(JsonOps.INSTANCE, value).getOrThrow(); }
}
