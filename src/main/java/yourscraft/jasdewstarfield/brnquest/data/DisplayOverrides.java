package yourscraft.jasdewstarfield.brnquest.data;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Shared author display properties use reserved schema-1 extensions and preserve unrelated addon data. */
public final class DisplayOverrides {
    public static final String QUEST_NAME_KEY = "brnquest:quest_name_position";
    public static final String CHAPTER_PROGRESS_KEY = "brnquest:chapter_progress";
    private DisplayOverrides() {}

    public enum NamePosition {
        INHERIT, OFF, ABOVE, BELOW;
        public NamePosition resolve(NamePosition clientDefault) { return this == INHERIT ? clientDefault : this; }
    }
    public enum ProgressVisibility {
        INHERIT, SHOW, HIDE;
        public boolean resolve(boolean clientDefault) { return this == INHERIT ? clientDefault : this == SHOW; }
    }

    public static NamePosition name(Map<String, String> extensions) {
        return parseName(extensions.getOrDefault(QUEST_NAME_KEY, "inherit"));
    }
    public static ProgressVisibility progress(Map<String, String> extensions) {
        return parseProgress(extensions.getOrDefault(CHAPTER_PROGRESS_KEY, "inherit"));
    }
    public static NamePosition parseName(String value) {
        return NamePosition.valueOf(value.toUpperCase(Locale.ROOT));
    }
    public static ProgressVisibility parseProgress(String value) {
        return ProgressVisibility.valueOf(value.toUpperCase(Locale.ROOT));
    }

    /** Absent request fields preserve existing properties; explicit inherit removes only the owned key. */
    public static Map<String, String> updateName(Map<String, String> extensions, String value) {
        return value == null ? extensions : write(extensions, QUEST_NAME_KEY, parseName(value));
    }
    public static Map<String, String> updateProgress(Map<String, String> extensions, Map<String, String> config) {
        return config.containsKey("chapter_progress")
                ? write(extensions, CHAPTER_PROGRESS_KEY, parseProgress(config.get("chapter_progress"))) : extensions;
    }
    private static Map<String, String> write(Map<String, String> extensions, String key, Enum<?> value) {
        var result = new TreeMap<>(extensions);
        if (value.name().equals("INHERIT")) result.remove(key);
        else result.put(key, value.name().toLowerCase(Locale.ROOT));
        return Map.copyOf(result);
    }
}
