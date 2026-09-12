package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.network.chat.Component;
import java.util.List;

/** Localizes stable publish-diff protocol values without changing their server representation. */
public final class EditorPublishReviewText {
    private static final String PREFIX = "screen.brnquest.editor.publish.review.diff.";

    private EditorPublishReviewText() {}

    public static Component heading(EditorPublishReviewModel.Change change) {
        return Component.translatable(PREFIX + "heading", objectKind(change.objectKind()), kind(change.kind()));
    }

    public static Component detail(EditorPublishReviewModel.Change change) {
        Component path = path(change.path());
        String value = summarizedValue(change);
        if (change.path().isBlank() || value.isBlank()) {
            return Component.literal(change.objectId());
        }
        return Component.translatable(PREFIX + "detail", change.objectId(), path, value);
    }

    /** Compact first-level values; the exact serialized values remain available in secondary details. */
    public static Component readableDetail(EditorPublishReviewModel.Change change) {
        String before = fieldValue(change.path(), change.before()), after = fieldValue(change.path(), change.after());
        String values = before.isBlank() ? "+ " + after : after.isBlank() ? "− " + before : before + " → " + after;
        return change.path().isBlank() ? Component.literal(values)
                : readablePath(change.path()).copy().append(": " + values);
    }

    private static Component readablePath(String value) {
        String key = value.startsWith("behavior.") ? "screen.brnquest.editor.behavior." + value.substring(9)
                : value.startsWith("appearance.") ? "screen.brnquest.editor.quest." + value.substring(11)
                : value.startsWith("config.") ? "screen.brnquest.editor.config." + value.substring(7) : "";
        return !key.isBlank() && net.minecraft.client.resources.language.I18n.exists(key) ? Component.translatable(key) : path(value);
    }

    private static String fieldValue(String path, String raw) {
        String category = path.equals("claim_policy") ? "claim" : path.equals("appearance.shape") ? "shape"
                : path.equals("behavior.dependency_requirement") ? "dependency" : "";
        String key = "screen.brnquest.editor.value." + category + "." + raw.replace("\"", "").toLowerCase(java.util.Locale.ROOT);
        return !category.isBlank() && net.minecraft.client.resources.language.I18n.exists(key)
                ? Component.translatable(key).getString() : compactValue(raw);
    }

    public static String storedTitle(String raw) {
        try {
            var value = com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
            if (value.has("title") && value.get("title").isJsonPrimitive()) return value.get("title").getAsString();
            if (value.has("config")) return storedTitle(value.get("config").toString());
        } catch (RuntimeException ignored) { /* Older review payloads remain usable without JSON summaries. */ }
        return "";
    }

    public static String compactValue(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String title = storedTitle(raw);
        if (!title.isBlank()) return title;
        if (raw.equals("true") || raw.equals("false"))
            return Component.translatable(Boolean.parseBoolean(raw) ? "options.on" : "options.off").getString();
        try {
            var json = com.google.gson.JsonParser.parseString(raw);
            if (json.isJsonObject()) return Component.translatable(PREFIX + "fields", json.getAsJsonObject().size()).getString();
            if (json.isJsonArray()) return Component.translatable(PREFIX + "entries", json.getAsJsonArray().size()).getString();
        } catch (RuntimeException ignored) { /* Scalar protocol values are already readable. */ }
        return raw.length() > 100 ? raw.substring(0, 97) + "…" : raw;
    }

    public static List<Component> valueTooltip(EditorPublishReviewModel.Change change) {
        if (change.before().isBlank()) {
            return List.of(Component.translatable(PREFIX + "value.added", change.after()));
        }
        if (change.after().isBlank()) {
            return List.of(Component.translatable(PREFIX + "value.removed", change.before()));
        }
        return List.of(Component.translatable(PREFIX + "value.before", change.before()),
                Component.translatable(PREFIX + "value.after", change.after()));
    }

    static Component kind(String kind) {
        return switch (kind) {
            case "ADDED" -> Component.translatable(PREFIX + "kind.added");
            case "REMOVED" -> Component.translatable(PREFIX + "kind.removed");
            case "RENAMED" -> Component.translatable(PREFIX + "kind.renamed");
            case "MOVED" -> Component.translatable(PREFIX + "kind.moved");
            case "ORDER_CHANGED" -> Component.translatable(PREFIX + "kind.order_changed");
            case "PROPERTY_CHANGED" -> Component.translatable(PREFIX + "kind.property_changed");
            case "DEPENDENCY_ADDED" -> Component.translatable(PREFIX + "kind.dependency_added");
            case "DEPENDENCY_REMOVED" -> Component.translatable(PREFIX + "kind.dependency_removed");
            case "TYPE_CHANGED" -> Component.translatable(PREFIX + "kind.type_changed");
            case "CONFIG_CHANGED" -> Component.translatable(PREFIX + "kind.config_changed");
            default -> Component.literal(readableFallback(kind));
        };
    }

    static Component objectKind(String kind) {
        return switch (kind) {
            case "BOOK" -> Component.translatable(PREFIX + "object.book");
            case "CHAPTER_GROUP" -> Component.translatable(PREFIX + "object.chapter_group");
            case "CHAPTER" -> Component.translatable(PREFIX + "object.chapter");
            case "QUEST" -> Component.translatable(PREFIX + "object.quest");
            case "TASK" -> Component.translatable(PREFIX + "object.task");
            case "REWARD" -> Component.translatable(PREFIX + "object.reward");
            default -> Component.literal(readableFallback(kind));
        };
    }

    static Component path(String path) {
        if (path.startsWith("config.")) {
            return Component.translatable(PREFIX + "path.config", path.substring("config.".length()));
        }
        return switch (path) {
            case "id" -> Component.translatable(PREFIX + "path.id");
            case "title" -> Component.translatable(PREFIX + "path.title");
            case "subtitle" -> Component.translatable(PREFIX + "path.subtitle");
            case "description" -> Component.translatable(PREFIX + "path.description");
            case "icon" -> Component.translatable(PREFIX + "path.icon");
            case "group_id" -> Component.translatable(PREFIX + "path.group");
            case "chapter_id" -> Component.translatable(PREFIX + "path.chapter");
            case "position" -> Component.translatable(PREFIX + "path.position");
            case "dependencies" -> Component.translatable(PREFIX + "path.dependencies");
            case "type" -> Component.translatable(PREFIX + "path.type");
            case "order" -> Component.translatable(PREFIX + "path.order");
            case "optional" -> Component.translatable(PREFIX + "path.optional");
            case "claim_policy" -> Component.translatable(PREFIX + "path.claim_policy");
            case "team_reward" -> Component.translatable(PREFIX + "path.team_reward");
            default -> Component.literal(readableFallback(path));
        };
    }

    private static String summarizedValue(EditorPublishReviewModel.Change change) {
        if (change.before().isBlank()) return "+ " + change.after();
        if (change.after().isBlank()) return "− " + change.before();
        return change.before() + " → " + change.after();
    }

    /** Future protocol values remain readable even before a matching translation is added. */
    private static String readableFallback(String value) {
        return value == null || value.isBlank() ? "" : value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }
}
