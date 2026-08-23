package yourscraft.jasdewstarfield.brnquest.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

import java.util.Map;
import java.util.TreeMap;

/** Deterministic text projection for the schema-1 string map used by raw editor fallback views. */
@ApiStatus(ApiStability.INTERNAL)
public final class RawConfigText {
    private static final Gson PRETTY_GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private RawConfigText() {}

    /** Formats every value as a JSON string so reopening the editor is lossless. */
    public static String format(Map<String, String> config) {
        JsonObject object = new JsonObject();
        new TreeMap<>(config).forEach(object::addProperty);
        return PRETTY_GSON.toJson(object);
    }

    /**
     * Parses only a flat JSON primitive map. The server repeats protocol bounds and performs the
     * authoritative type Codec validation before granting a new draft revision.
     */
    public static Map<String, String> parse(String text) {
        JsonElement root;
        try {
            root = JsonParser.parseString(text == null ? "" : text);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Raw configuration is not valid JSON", exception);
        }
        if (!root.isJsonObject()) throw new IllegalArgumentException("Raw configuration must be a JSON object");
        JsonObject object = root.getAsJsonObject();
        if (object.size() > 64) throw new IllegalArgumentException("Raw configuration exceeds 64 fields");
        Map<String, String> result = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getKey().isBlank() || entry.getKey().length() > 128) {
                throw new IllegalArgumentException("Raw configuration contains an invalid field name");
            }
            if (!entry.getValue().isJsonPrimitive()) {
                throw new IllegalArgumentException("Raw configuration values must be strings, numbers, or booleans");
            }
            String value = entry.getValue().getAsString();
            if (value.length() > 65_536) {
                throw new IllegalArgumentException("Raw configuration contains a value longer than 65536 characters");
            }
            result.put(entry.getKey(), value);
        }
        return Map.copyOf(result);
    }
}
