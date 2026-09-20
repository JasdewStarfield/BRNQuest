package yourscraft.jasdewstarfield.brnquest.builtin.reward.table;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.task.*;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.*;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Versioned, lossless tree document. Accessors return copies so editor cancellation is isolated. */
public final class RewardTableTree {
    public static final int MAX_BYTES = 65_536, MAX_NODES = 256, MAX_DEPTH = 8, MAX_OCCURRENCES = 1024;
    private final JsonObject document;

    private RewardTableTree(JsonObject document) { this.document = document.deepCopy(); }

    /** Validate the complete tree, including currently hidden random parameters, before accepting it. */
    public static RewardTableTree parse(String text) {
        if (text == null || text.length() > MAX_BYTES || text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw invalid("root", "table exceeds 64 KiB");
        checkJsonDepth(text);
        JsonElement value = JsonParser.parseString(text);
        if (!value.isJsonObject()) throw invalid("root", "expected an object");
        validate(value.getAsJsonObject(), "root", 1, new int[]{0});
        return new RewardTableTree(value.getAsJsonObject());
    }

    public JsonObject document() { return document.deepCopy(); }
    public String encode() { return document.toString(); }
    public String mode() { return string(document, "mode", "all", "root"); }
    public List<JsonObject> entries() {
        List<JsonObject> result = new ArrayList<>();
        document.getAsJsonArray("entries").forEach(e -> result.add(e.getAsJsonObject().deepCopy()));
        return List.copyOf(result);
    }

    /** A deliberately refuses future modes and nesting instead of silently flattening them. */
    public void requireSingleAll() {
        if (!mode().equals("all")) throw invalid("root", "only all is supported in F5-A");
        requireSingleLayer();
    }

    /** Legacy single-layer validation helper; the nested coordinator does not use this restriction. */
    public void requireSingleLayer() {
        for (JsonObject entry : entries()) if (entry.has("table"))
            throw invalid("root/" + entry.get("entry_id").getAsString(), "expected a single-layer table");
    }

    public int rolls() { return integer(document, "rolls", 1, "root"); }
    public boolean replacement() { return bool(document, "replacement", true, "root"); }
    public java.math.BigDecimal emptyWeight() { return decimal(document, "empty_weight", 0); }
    public static java.math.BigDecimal weight(JsonObject entry) { return decimal(entry, "weight", 1); }
    public static boolean always(JsonObject entry) { return bool(entry, "always", false, "entry"); }

    /** Decimal tickets preserve fractional ratios without floating-point cumulative rounding. */
    private static java.math.BigDecimal decimal(JsonObject object, String key, int fallback) {
        var value = object.has(key) ? object.get(key).getAsBigDecimal().stripTrailingZeros() : java.math.BigDecimal.valueOf(fallback);
        if (value.precision() > 34 || value.scale() < -308 || value.scale() > 324)
            throw invalid("root", key + " exceeds 34 significant digits or decimal scale -308..324");
        return value;
    }

    /** Stable local IDs survive sorting; copying allocates a fresh identity and retains extensions. */
    public static JsonObject copyEntry(JsonObject source) {
        JsonObject copy = source.deepCopy();
        copy.addProperty("entry_id", "entry_" + UUID.randomUUID().toString().replace("-", ""));
        return copy;
    }

    public static Map<String, String> config(JsonObject entry) {
        Map<String, String> result = new LinkedHashMap<>();
        if (entry.has("config")) entry.getAsJsonObject("config").entrySet()
                .forEach(e -> result.put(e.getKey(), e.getValue().getAsString()));
        return Map.copyOf(result);
    }

    private static long validate(JsonObject table, String path, int depth, int[] nodes) {
        if (depth > MAX_DEPTH) throw invalid(path, "table depth exceeds " + MAX_DEPTH);
        count(nodes, path);
        if (integer(table, "version", 1, path) != 1) throw invalid(path, "unsupported tree version; preserve read-only");
        String mode = string(table, "mode", "all", path);
        if (!Set.of("all", "choice", "random").contains(mode)) throw invalid(path, "unknown mode");
        int rolls = integer(table, "rolls", 1, path);
        if (rolls < 1 || rolls > 64) throw invalid(path, "rolls must be 1..64");
        bool(table, "replacement", true, path);
        double total = number(table, "empty_weight", 0, path);
        decimal(table, "empty_weight", 0);
        if (total < 0) throw invalid(path, "negative empty_weight");
        if (!table.has("entries") || !table.get("entries").isJsonArray()) throw invalid(path, "entries must be an array");
        JsonArray entries = table.getAsJsonArray("entries");
        if (entries.isEmpty() && !(mode.equals("random") && total > 0)) throw invalid(path, "empty table");
        Set<String> ids = new HashSet<>();
        long sum = 0, maximum = 0, always = 0;
        List<Long> weightedLeaves = new ArrayList<>();
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) throw invalid(path, "entry must be an object");
            JsonObject entry = element.getAsJsonObject();
            String id = string(entry, "entry_id", "", path);
            if (!id.matches("[a-z0-9_.-]{1,64}") || !ids.add(id)) throw invalid(path, "invalid or duplicate entry_id: " + id);
            String childPath = path + "/" + id;
            String type = string(entry, "type", "", childPath);
            if (!type.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw invalid(childPath, "invalid type ID");
            double weight = number(entry, "weight", 1, childPath);
            decimal(entry, "weight", 1);
            if (weight <= 0) throw invalid(childPath, "weight must be positive");
            boolean guaranteed = bool(entry, "always", false, childPath);
            if (!guaranteed) total += weight;
            if (!Double.isFinite(total)) throw invalid(path, "weight sum overflow");
            if (entry.has("config")) {
                if (!entry.get("config").isJsonObject()) throw invalid(childPath, "config must be a string map");
                for (var field : entry.getAsJsonObject("config").entrySet())
                    if (!field.getValue().isJsonPrimitive() || !field.getValue().getAsJsonPrimitive().isString())
                        throw invalid(childPath, "config values must be strings");
            }
            long leaves;
            if (type.equals("brnquest:reward_table")) {
                if (!entry.has("table") || !entry.get("table").isJsonObject() || config(entry).containsKey("table"))
                    throw invalid(childPath, "nested table must have exactly one structured tree");
                leaves = validate(entry.getAsJsonObject("table"), childPath, depth + 1, nodes);
            } else {
                if (entry.has("table")) throw invalid(childPath, "leaf cannot contain a table");
                count(nodes, childPath);
                leaves = 1;
            }
            sum = Math.addExact(sum, leaves);
            maximum = Math.max(maximum, leaves);
            if (guaranteed) always = Math.addExact(always, leaves);
            else weightedLeaves.add(leaves);
        }
        long expanded = switch (mode) {
            case "choice" -> maximum;
            // Guaranteed subtrees are not in the random pool; no-replacement cannot repeat the largest subtree.
            case "random" -> Math.addExact(always, bool(table,"replacement",true,path)
                    ? Math.multiplyExact(weightedLeaves.stream().mapToLong(Long::longValue).max().orElse(0),rolls)
                    : weightedLeaves.stream().sorted(Comparator.reverseOrder()).limit(rolls).mapToLong(Long::longValue).sum());
            default -> sum;
        };
        if (expanded > MAX_OCCURRENCES) throw invalid(path, "expanded leaf budget exceeds " + MAX_OCCURRENCES);
        return expanded;
    }

    private static void count(int[] nodes, String path) {
        if (++nodes[0] > MAX_NODES) throw invalid(path, "node budget exceeds " + MAX_NODES);
    }
    private static String string(JsonObject object, String key, String fallback, String path) {
        if (!object.has(key)) return fallback;
        var value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid(path, key + " must be a string");
        return value.getAsString();
    }
    private static double number(JsonObject object, String key, double fallback, String path) {
        if (!object.has(key)) return fallback;
        var value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid(path, key + " must be numeric");
        double number = value.getAsDouble();
        if (!Double.isFinite(number)) throw invalid(path, key + " must be finite");
        return number;
    }
    private static int integer(JsonObject object, String key, int fallback, String path) {
        if (!object.has(key)) return fallback;
        number(object, key, fallback, path);
        try { return object.get(key).getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException error) { throw invalid(path, key + " must be an integer"); }
    }
    private static boolean bool(JsonObject object, String key, boolean fallback, String path) {
        if (!object.has(key)) return fallback;
        var value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw invalid(path, key + " must be boolean");
        return value.getAsBoolean();
    }
    private static IllegalArgumentException invalid(String path, String message) { return new IllegalArgumentException(path + ": " + message); }
    /** Reject pathological extension JSON before Gson deepCopy can recurse on untrusted input. */
    static void checkJsonDepth(String text) {
        int depth = 0; boolean quoted = false, escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') { if (++depth > 64) throw invalid("root", "JSON structural depth exceeds 64"); }
            else if (c == '}' || c == ']') depth--;
        }
    }
}
