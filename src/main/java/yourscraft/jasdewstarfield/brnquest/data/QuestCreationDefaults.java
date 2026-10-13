package yourscraft.jasdewstarfield.brnquest.data;

import com.google.gson.*;
import java.util.*;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigValueType;
import yourscraft.jasdewstarfield.brnquest.editor.QuestPropertySchema;

/** Sparse creation template derived from the same registration as instance properties. */
public record QuestCreationDefaults(Map<String, String> values) {
    public static final QuestCreationDefaults EMPTY = new QuestCreationDefaults(Map.of());
    public static final List<String> FIELDS = QuestPropertySchema.CATALOG.defaults().stream().map(QuestPropertySchema.Property::key).toList();
    public QuestCreationDefaults {
        values = QuestPropertySchema.CATALOG.normalize(values, QuestPropertySchema.CATALOG.defaults());
    }
    public static boolean integerField(String key) { return type(key) == ConfigValueType.INTEGER; }
    public static boolean numberField(String key) { return integerField(key) || type(key) == ConfigValueType.DECIMAL; }
    public static boolean stringField(String key) { return !numberField(key) && type(key) != ConfigValueType.BOOLEAN; }
    private static ConfigValueType type(String key) { return QuestPropertySchema.CATALOG.property(key).field().valueType(); }

    public QuestCreationDefaults overlay(QuestCreationDefaults overrides) {
        var result = new TreeMap<>(values); result.putAll(overrides.values());
        return new QuestCreationDefaults(result);
    }
    public JsonObject toJson() { return QuestPropertySchema.CATALOG.json(values); }
    public static QuestCreationDefaults fromJson(JsonElement input) {
        if (input == null) return EMPTY;
        if (!input.isJsonObject()) throw new IllegalArgumentException("quest_defaults must be an object");
        var values = new TreeMap<String, String>();
        input.getAsJsonObject().entrySet().forEach(entry -> {
            if (!entry.getValue().isJsonPrimitive()) throw new IllegalArgumentException("Invalid creation default");
            var value = entry.getValue().getAsJsonPrimitive();
            String key = entry.getKey();
            if (!FIELDS.contains(key) || (stringField(key) ? !value.isString() : numberField(key) ? !value.isNumber() : !value.isBoolean()))
                throw new IllegalArgumentException("Wrong creation default type: " + key);
            values.put(key, value.getAsString());
        });
        return new QuestCreationDefaults(values);
    }
    public QuestAppearance appearance() { return QuestPropertySchema.appearance(values); }
    public QuestBehavior behavior() { return QuestPropertySchema.behavior(values); }
    /** Materialize registered extension properties once; explicit instance values retain precedence. */
    public Map<String, String> extensions(Map<String, String> explicit) {
        var resolved = new TreeMap<>(QuestPropertySchema.CATALOG.extensions(Map.of(), values));
        resolved.putAll(explicit);
        return Map.copyOf(resolved);
    }
}
