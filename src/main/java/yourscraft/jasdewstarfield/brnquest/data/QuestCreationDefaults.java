package yourscraft.jasdewstarfield.brnquest.data;

import com.google.gson.*;
import java.util.*;

/** Sparse creation template. Missing keys inherit; explicit false and zero remain real overrides. */
public record QuestCreationDefaults(Map<String, String> values) {
    public static final QuestCreationDefaults EMPTY = new QuestCreationDefaults(Map.of());
    public static final List<String> FIELDS = List.of("shape", "size", "icon_scale", "min_width",
            "hide_until_dependencies_visible", "hide_until_dependencies_complete", "hide_details_until_startable",
            "hide_text_until_complete", "sequential_tasks", "repeatable", "invisible_until_complete",
            "visible_after_tasks", "hide_lock_icon", "dependency_requirement", "repeat_cooldown_seconds", "ignore_reward_blocking");
    /** Field kinds are explicit so UI pagination and JSON validation do not depend on list positions. */
    public static boolean integerField(String key) {
        return key.equals("visible_after_tasks") || key.equals("repeat_cooldown_seconds");
    }
    public static boolean numberField(String key) {
        return integerField(key) || Set.of("size", "icon_scale", "min_width").contains(key);
    }
    public static boolean stringField(String key) { return key.equals("shape") || key.equals("dependency_requirement"); }
    public QuestCreationDefaults {
        var normalized = new TreeMap<String, String>();
        values.forEach((key, value) -> {
            if (!FIELDS.contains(key) || value == null) throw new IllegalArgumentException("Unknown creation default: " + key);
            if (key.equals("shape")) {
                if (value.isBlank()) throw new IllegalArgumentException("Empty default shape");
                normalized.put(key, value);
            } else if (key.equals("dependency_requirement")) {
                // Unlike the tolerant legacy parser, author templates must not silently accept misspelled modes.
                boolean known = Arrays.stream(DependencyRequirement.values()).anyMatch(mode -> mode.serializedName().equals(value));
                if (!known) throw new IllegalArgumentException("Invalid dependency requirement");
                normalized.put(key, value);
            } else if (integerField(key)) {
                int number = Integer.parseInt(value);
                if (number < 0) throw new IllegalArgumentException("Negative creation default: " + key);
                normalized.put(key, Integer.toString(number));
            } else if (numberField(key)) {
                double number = Double.parseDouble(value);
                if (!Double.isFinite(number) || (key.equals("min_width") ? number < 0 : number <= 0))
                    throw new IllegalArgumentException("Invalid creation default: " + key);
                normalized.put(key, Double.toString(number));
            } else {
                if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Expected boolean: " + key);
                normalized.put(key, value);
            }
        });
        values = Collections.unmodifiableMap(normalized);
    }
    public QuestCreationDefaults overlay(QuestCreationDefaults overrides) {
        var result = new TreeMap<>(values); result.putAll(overrides.values());
        return new QuestCreationDefaults(result);
    }
    public JsonObject toJson() {
        var json = new JsonObject();
        values.forEach((key, value) -> {
            if (stringField(key)) json.addProperty(key, value);
            else if (integerField(key)) json.addProperty(key, Integer.parseInt(value));
            else if (numberField(key)) json.addProperty(key, Double.parseDouble(value));
            else json.addProperty(key, Boolean.parseBoolean(value));
        });
        return json;
    }
    public static QuestCreationDefaults fromJson(JsonElement input) {
        if (input == null) return EMPTY;
        if (!input.isJsonObject()) throw new IllegalArgumentException("quest_defaults must be an object");
        var values = new TreeMap<String, String>();
        input.getAsJsonObject().entrySet().forEach(entry -> {
            if (!entry.getValue().isJsonPrimitive()) throw new IllegalArgumentException("Invalid creation default");
            var value = entry.getValue().getAsJsonPrimitive();
            String key = entry.getKey();
            if (!FIELDS.contains(key) || (stringField(key) ? !value.isString() : numberField(key) ? !value.isNumber() : !value.isBoolean()))
                throw new IllegalArgumentException("Wrong creation default type: " + entry.getKey());
            values.put(entry.getKey(), value.getAsString());
        });
        return new QuestCreationDefaults(values);
    }
    public QuestAppearance appearance() {
        var d = QuestAppearance.DEFAULT;
        return new QuestAppearance(values.getOrDefault("shape", d.shape()), number("size", d.size()),
                number("icon_scale", d.iconScale()), number("min_width", d.minWidth()));
    }
    public QuestBehavior behavior() {
        var d = QuestBehavior.DEFAULT;
        return new QuestBehavior(flag("hide_until_dependencies_visible"), flag("hide_until_dependencies_complete"),
                flag("invisible_until_complete"), integer("visible_after_tasks"), flag("hide_details_until_startable"),
                flag("hide_text_until_complete"), flag("hide_lock_icon"),
                DependencyRequirement.parse(values.get("dependency_requirement")), d.minimumRequiredDependencies(),
                flag("sequential_tasks"), flag("repeatable"), integer("repeat_cooldown_seconds"), flag("ignore_reward_blocking"));
    }
    private int integer(String key) { return Integer.parseInt(values.getOrDefault(key, "0")); }
    private double number(String key, double fallback) { return values.containsKey(key) ? Double.parseDouble(values.get(key)) : fallback; }
    private boolean flag(String key) { return Boolean.parseBoolean(values.getOrDefault(key, "false")); }
}
