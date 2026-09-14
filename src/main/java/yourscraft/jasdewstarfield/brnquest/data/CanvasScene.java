package yourscraft.jasdewstarfield.brnquest.data;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Independent shared artwork; stored under a reserved, versioned core extension for schema-1 compatibility. */
public record CanvasScene(List<Decoration> decorations, Background canvas, Background screen, Boolean screenAbove) {
    public static final String KEY = "brnquest:canvas_v1";
    public static final CanvasScene EMPTY = new CanvasScene(List.of(), null, null);
    public static final int MAX_DECORATIONS = 128;
    private static final Map<String, CanvasScene> CACHE = new LinkedHashMap<>();

    /** Coordinates and sizes use the same grid units as quests; layer orders artwork below all quest nodes. */
    public record Decoration(ResourceLocation id, String texture, double x, double y,
                             double width, double height, boolean aspectLocked, int layer, boolean locked) {
        public Decoration {
            Objects.requireNonNull(id, "Decoration ID");
            texture = resource(texture, false);
            coordinate(x); coordinate(y); size(width); size(height);
            if (Math.abs((long) layer) > 10000) throw new IllegalArgumentException("Decoration layer exceeds 10000");
        }
        /** Copying changes only identity and placement, leaving the captured configuration intact. */
        public Decoration placed(ResourceLocation newId, double newX, double newY, double w, double h) {
            return new Decoration(newId, texture, newX, newY, w, h, aspectLocked, layer, locked);
        }
    }

    public enum Fit { TILE, CONTAIN, COVER }
    /** Null background inherits; an empty texture explicitly disables the inherited background. */
    public record Background(String texture, Fit fit, double opacity, double scale) {
        /** Older books and callers retain their original 1x appearance. */
        public Background(String texture, Fit fit, double opacity) { this(texture, fit, opacity, 1); }
        public Background {
            texture = resource(texture, true);
            if (!Double.isFinite(scale) || scale < 0.25 || scale > 8)
                throw new IllegalArgumentException("Background scale must be between 0.25 and 8");
            Objects.requireNonNull(fit, "Background fit");
            if (!Double.isFinite(opacity) || opacity < 0 || opacity > 1)
                throw new IllegalArgumentException("Background opacity must be between 0 and 1");
        }
    }

    /** Missing order inherits the book setting; old books default to canvas above screen. */
    public CanvasScene(List<Decoration> decorations, Background canvas, Background screen) {
        this(decorations, canvas, screen, null);
    }
    public boolean screenAbove(CanvasScene book) {
        return Boolean.TRUE.equals(screenAbove == null ? book.screenAbove() : screenAbove);
    }
    public CanvasScene {
        decorations = List.copyOf(decorations);
        if (decorations.size() > MAX_DECORATIONS) throw new IllegalArgumentException("Too many decorations");
        Set<ResourceLocation> ids = new HashSet<>();
        for (var decoration : decorations)
            if (!ids.add(decoration.id())) throw new IllegalArgumentException("Duplicate decoration ID");
    }

    private static void coordinate(double v) {
        if (!Double.isFinite(v) || Math.abs(v) > 1_000_000) throw new IllegalArgumentException("Invalid decoration coordinate");
    }
    private static void size(double v) {
        if (!Double.isFinite(v) || v < 0.05 || v > 1024) throw new IllegalArgumentException("Decoration size must be 0.05–1024");
    }
    private static String resource(String value, boolean empty) {
        if (empty && "".equals(value)) return value;
        ResourceLocation id = value == null || value.length() > 512 ? null : ResourceLocation.tryParse(value);
        if (id == null || !id.getPath().startsWith("textures/") || !id.getPath().endsWith(".png")
                || id.getPath().contains("..")) throw new IllegalArgumentException("Use a textures/*.png resource ID");
        return id.toString();
    }

    public static synchronized CanvasScene read(Map<String, String> extensions) {
        String json = extensions.get(KEY);
        if (json == null) return EMPTY;
        var cached = CACHE.get(json);
        if (cached != null) return cached;
        var result = decode(json);
        if (CACHE.size() >= 32) CACHE.remove(CACHE.keySet().iterator().next());
        CACHE.put(json, result);
        return result;
    }
    /** Preserve every unrelated plugin value; the core owns only its explicitly reserved key. */
    public Map<String, String> write(Map<String, String> extensions) {
        var result = new TreeMap<>(extensions);
        if (equals(EMPTY)) result.remove(KEY); else result.put(KEY, encode());
        return Map.copyOf(result);
    }
    /** Property forms own backgrounds only, so saving metadata cannot replace decoration objects. */
    public CanvasScene backgrounds() { return new CanvasScene(List.of(), canvas, screen, screenAbove); }
    public CanvasScene withBackgrounds(CanvasScene value) {
        if (!value.decorations().isEmpty()) throw new IllegalArgumentException("Background properties cannot contain decorations");
        return new CanvasScene(decorations, value.canvas(), value.screen(), value.screenAbove());
    }
    public CanvasScene copied() {
        return new CanvasScene(decorations.stream().map(d -> d.placed(newId(), d.x(), d.y(), d.width(), d.height())).toList(), canvas, screen, screenAbove);
    }
    public static ResourceLocation newId() { return ResourceLocation.parse("brnquest:decoration_" + UUID.randomUUID().toString().replace("-", "")); }
    public static Background effective(Background chapter, Background book) { return chapter == null ? book : chapter; }

    /** Artwork identities are book-wide and cannot alias an interactive object or another chapter's decoration. */
    static void validateBook(ResourceLocation bookId, List<ChapterGroupDefinition> groups, List<ChapterDefinition> chapters, CanvasScene bookScene) {
        if (!bookScene.decorations().isEmpty()) throw new IllegalArgumentException("Decorations belong to chapters");
        Set<ResourceLocation> artwork = new HashSet<>();
        for (var chapter : chapters) for (var d : chapter.canvasScene().decorations())
            if (!artwork.add(d.id())) throw new IllegalArgumentException("Duplicate book-wide decoration ID " + d.id());
        if (artwork.isEmpty()) return;
        Set<ResourceLocation> objects = new HashSet<>(); objects.add(bookId);
        groups.forEach(g -> objects.add(g.id()));
        for (var c : chapters) {
            objects.add(c.id());
            for (var q : c.quests()) {
                objects.add(q.id()); q.tasks().forEach(t -> objects.add(t.id())); q.rewards().forEach(r -> objects.add(r.id()));
            }
        }
        if (artwork.stream().anyMatch(objects::contains)) throw new IllegalArgumentException("Decoration ID aliases an interactive object");
    }

    public String encode() {
        var root = new JsonObject();
        var values = new JsonArray();
        for (var d : decorations) {
            var v = new JsonObject();
            v.addProperty("id", d.id().toString()); v.addProperty("texture", d.texture());
            v.addProperty("x", d.x()); v.addProperty("y", d.y());
            v.addProperty("width", d.width()); v.addProperty("height", d.height());
            v.addProperty("aspect_locked", d.aspectLocked()); v.addProperty("layer", d.layer()); v.addProperty("locked", d.locked());
            values.add(v);
        }
        root.add("decorations", values);
        if (screenAbove != null) root.addProperty("screen_above", screenAbove);
        if (canvas != null) root.add("canvas", backgroundJson(canvas));
        if (screen != null) root.add("screen", backgroundJson(screen));
        String json = root.toString();
        if (json.length() > 65536) throw new IllegalArgumentException("Canvas scene exceeds editor capacity");
        return json;
    }
    private static JsonObject backgroundJson(Background b) {
        var v = new JsonObject(); v.addProperty("texture", b.texture()); v.addProperty("fit", b.fit().name()); v.addProperty("opacity", b.opacity()); v.addProperty("scale", b.scale()); return v;
    }
    /** Parse with constructors so native files and network edits enforce the same finite geometry and capacity rules. */
    public static CanvasScene decode(String json) {
        try {
            if (json == null || json.length() > 65536) throw new IllegalArgumentException("Canvas data too large");
            var root = JsonParser.parseString(json).getAsJsonObject();
            var values = root.getAsJsonArray("decorations");
            if (values == null || values.size() > MAX_DECORATIONS) throw new IllegalArgumentException("Invalid decoration list");
            var result = new ArrayList<Decoration>();
            for (var element : values) {
                var v = element.getAsJsonObject();
                double layer = v.get("layer").getAsDouble();
                if (!Double.isFinite(layer) || layer != Math.rint(layer) || Math.abs(layer) > 10000) throw new IllegalArgumentException("Invalid layer");
                result.add(new Decoration(ResourceLocation.parse(v.get("id").getAsString()), v.get("texture").getAsString(),
                        v.get("x").getAsDouble(), v.get("y").getAsDouble(), v.get("width").getAsDouble(), v.get("height").getAsDouble(),
                        bool(v, "aspect_locked"), (int) layer, bool(v, "locked")));
            }
            return new CanvasScene(result, background(root, "canvas"), background(root, "screen"), root.has("screen_above") ? bool(root, "screen_above") : null);
        } catch (RuntimeException e) { throw new IllegalArgumentException("Invalid canvas scene: " + e.getMessage(), e); }
    }
    private static boolean bool(JsonObject v, String key) {
        var primitive = v.getAsJsonPrimitive(key);
        if (primitive == null || !primitive.isBoolean()) throw new IllegalArgumentException("Invalid " + key);
        return primitive.getAsBoolean();
    }
    private static Background background(JsonObject root, String key) {
        if (!root.has(key)) return null;
        var v = root.getAsJsonObject(key);
        return new Background(v.get("texture").getAsString(), Fit.valueOf(v.get("fit").getAsString()), v.get("opacity").getAsDouble(), v.has("scale") ? v.get("scale").getAsDouble() : 1);
    }
}
