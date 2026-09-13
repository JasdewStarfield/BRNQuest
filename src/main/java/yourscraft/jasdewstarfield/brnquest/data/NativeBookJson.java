package yourscraft.jasdewstarfield.brnquest.data;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;

import java.util.*;

/** Deterministic native JSON encoder/decoder used by datapacks and network synchronization. */
public final class NativeBookJson {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private NativeBookJson() {}

    public static String encode(QuestBookDefinition book) {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", BrnQuestConstants.DATA_SCHEMA);
        root.addProperty("id", book.id().toString());
        root.addProperty("title", book.title());
        root.add("localization", encodeLocalization(book.localization()));
        root.add("extensions", encodeStringMap(book.extensions()));
        if (!book.questDefaults().values().isEmpty()) root.add("quest_defaults", book.questDefaults().toJson());
        if (!book.settings().equals(BookSettings.DEFAULT)) root.add("settings", book.settings().toJson());
        JsonArray groups = new JsonArray();
        book.chapterGroups().stream().sorted(Comparator.comparingInt(ChapterGroupDefinition::order)
                .thenComparing(g -> g.id().toString())).forEach(group -> {
            JsonObject value = new JsonObject();
            value.addProperty("id", group.id().toString());
            value.addProperty("title", group.title());
            value.addProperty("order", group.order());
            // Omit empty additions so historical books keep their content revision on upgrade.
            if (!group.icon().isEmpty()) value.addProperty("icon", group.icon());
            if (!group.description().isEmpty()) value.addProperty("description", group.description());
            if (!group.extensions().isEmpty()) value.add("extensions", encodeStringMap(group.extensions()));
            groups.add(value);
        });
        root.add("chapter_groups", groups);
        JsonArray chapters = new JsonArray();
        Map<ResourceLocation, Integer> groupOrder = new HashMap<>();
        book.chapterGroups().forEach(group -> groupOrder.put(group.id(), group.order()));
        book.chapters().stream().sorted(Comparator
                        .comparingInt((ChapterDefinition chapter) -> groupOrder.getOrDefault(chapter.groupId(), Integer.MAX_VALUE))
                        .thenComparingInt(ChapterDefinition::order)
                        .thenComparing(chapter -> chapter.id().toString()))
                .forEach(chapter -> chapters.add(encodeChapter(chapter)));
        root.add("chapters", chapters);
        JsonObject aliases = new JsonObject();
        book.legacyIds().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> aliases.addProperty(e.getKey(), e.getValue().toString()));
        root.add("legacy_ids", aliases);
        return GSON.toJson(root) + "\n";
    }

    public static QuestBookDefinition decode(JsonObject root) {
        int schema = root.get("schema_version").getAsInt();
        if (schema != BrnQuestConstants.DATA_SCHEMA) throw new JsonParseException("Unsupported schema " + schema);
        ResourceLocation bookId = id(root.get("id").getAsString());
        List<ChapterGroupDefinition> groups = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("chapter_groups")) {
            JsonObject value = element.getAsJsonObject();
            groups.add(new ChapterGroupDefinition(bookId, id(value.get("id").getAsString()), text(value, "title"), integer(value, "order"),
                    text(value, "icon"), text(value, "description"), stringMap(value, "extensions")));
        }
        List<ChapterDefinition> chapters = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("chapters")) chapters.add(decodeChapter(bookId, element.getAsJsonObject()));
        Map<String, ResourceLocation> aliases = new TreeMap<>();
        if (root.has("legacy_ids")) root.getAsJsonObject("legacy_ids").entrySet().forEach(e -> aliases.put(e.getKey(), id(e.getValue().getAsString())));
        return new QuestBookDefinition(bookId, schema, text(root, "title"), groups, chapters, aliases,
                decodeLocalization(root), stringMap(root, "extensions"), QuestCreationDefaults.fromJson(root.get("quest_defaults")), BookSettings.fromJson(root.get("settings")));
    }

    /** Strict nullable booleans distinguish inheritance from an explicit false. */
    public static Boolean optionalBoolean(JsonObject value, String key) {
        if (!value.has(key)) return null;
        JsonElement field = value.get(key);
        if (!field.isJsonPrimitive() || !field.getAsJsonPrimitive().isBoolean())
            throw new JsonParseException("Expected boolean: " + key);
        return field.getAsBoolean();
    }

    private static JsonObject encodeChapter(ChapterDefinition chapter) {
        JsonObject value = new JsonObject();
        value.addProperty("id", chapter.id().toString());
        value.addProperty("group_id", chapter.groupId().toString());
        if (chapter.autofocusQuestId() != null) value.addProperty("autofocus_id", chapter.autofocusQuestId().toString());
        if (chapter.consumeItems() != null) value.addProperty("consume_items", chapter.consumeItems());
        value.addProperty("title", chapter.title());
        value.addProperty("icon", chapter.icon());
        value.addProperty("order", chapter.order());
        value.add("extensions", encodeStringMap(chapter.extensions()));
        if (!chapter.questDefaults().values().isEmpty()) value.add("quest_defaults", chapter.questDefaults().toJson());
        JsonArray quests = new JsonArray();
        // Quest, task, and reward list order is author-visible presentation data.
        chapter.quests().forEach(quest -> quests.add(encodeQuest(quest)));
        value.add("quests", quests);
        return value;
    }

    private static JsonObject encodeQuest(QuestDefinition quest) {
        JsonObject value = new JsonObject();
        value.addProperty("id", quest.id().toString());
        value.addProperty("title", quest.title());
        value.addProperty("subtitle", quest.subtitle());
        value.addProperty("description", quest.description());
        value.addProperty("icon", quest.icon());
        value.addProperty("x", quest.x());
        value.addProperty("y", quest.y());
        value.addProperty("legacy_id", quest.legacyId());
        JsonObject appearance = new JsonObject();
        appearance.addProperty("shape", quest.appearance().shape());
        appearance.addProperty("size", quest.appearance().size());
        appearance.addProperty("icon_scale", quest.appearance().iconScale());
        appearance.addProperty("min_width", quest.appearance().minWidth());
        value.add("appearance", appearance);
        if (!quest.behavior().equals(QuestBehavior.DEFAULT)) {
            JsonObject behavior = new JsonObject();
            behavior.addProperty("hide_until_dependencies_visible", quest.behavior().hideUntilDependenciesVisible());
            behavior.addProperty("hide_until_dependencies_complete", quest.behavior().hideUntilDependenciesComplete());
            behavior.addProperty("invisible_until_complete", quest.behavior().invisibleUntilComplete());
            behavior.addProperty("visible_after_tasks", quest.behavior().visibleAfterTasks());
            behavior.addProperty("hide_details_until_startable", quest.behavior().hideDetailsUntilStartable());
            behavior.addProperty("hide_text_until_complete", quest.behavior().hideTextUntilComplete());
            behavior.addProperty("hide_lock_icon", quest.behavior().hideLockIcon());
            behavior.addProperty("dependency_requirement", quest.behavior().dependencyRequirement().serializedName());
            behavior.addProperty("minimum_required_dependencies", quest.behavior().minimumRequiredDependencies());
            behavior.addProperty("sequential_tasks", quest.behavior().sequentialTasks());
            behavior.addProperty("repeatable", quest.behavior().repeatable());
            behavior.addProperty("repeat_cooldown_seconds", quest.behavior().repeatCooldownSeconds());
            behavior.addProperty("ignore_reward_blocking", quest.behavior().ignoreRewardBlocking());
            value.add("behavior", behavior);
        }
        value.add("extensions", encodeStringMap(quest.extensions()));
        JsonArray dependencies = new JsonArray();
        quest.dependencies().stream().sorted(Comparator.comparing(ResourceLocation::toString)).forEach(id -> dependencies.add(id.toString()));
        value.add("dependencies", dependencies);
        JsonArray tasks = new JsonArray();
        quest.tasks().forEach(task -> tasks.add(encodeTask(task)));
        value.add("tasks", tasks);
        JsonArray rewards = new JsonArray();
        quest.rewards().forEach(reward -> rewards.add(encodeReward(reward)));
        value.add("rewards", rewards);
        return value;
    }

    private static JsonObject encodeTask(TaskDefinition task) {
        JsonObject value = baseTyped(task.id(), task.typeId(), task.config());
        value.addProperty("optional", task.optional());
        return value;
    }

    private static JsonObject encodeReward(RewardDefinition reward) {
        JsonObject value = baseTyped(reward.id(), reward.typeId(), reward.config());
        value.addProperty("claim_policy", reward.claimPolicy());
        value.addProperty("team_reward", reward.teamReward());
        return value;
    }

    private static JsonObject baseTyped(ResourceLocation id, ResourceLocation type, Map<String, String> config) {
        JsonObject value = new JsonObject();
        value.addProperty("id", id.toString());
        value.addProperty("type", type.toString());
        JsonObject configJson = new JsonObject();
        config.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> configJson.addProperty(e.getKey(), e.getValue()));
        value.add("config", configJson);
        return value;
    }

    private static ChapterDefinition decodeChapter(ResourceLocation bookId, JsonObject value) {
        ResourceLocation chapterId = id(value.get("id").getAsString());
        List<QuestDefinition> quests = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray("quests")) quests.add(decodeQuest(bookId, chapterId, element.getAsJsonObject()));
        return new ChapterDefinition(bookId, chapterId, id(value.get("group_id").getAsString()), text(value, "title"),
                text(value, "icon"), integer(value, "order"), quests, stringMap(value, "extensions"), QuestCreationDefaults.fromJson(value.get("quest_defaults")), optionalBoolean(value, "consume_items"),
                value.has("autofocus_id") ? id(value.get("autofocus_id").getAsString()) : null);
    }

    private static QuestDefinition decodeQuest(ResourceLocation bookId, ResourceLocation chapterId, JsonObject value) {
        List<ResourceLocation> dependencies = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray("dependencies")) dependencies.add(id(element.getAsString()));
        List<TaskDefinition> tasks = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray("tasks")) {
            JsonObject task = element.getAsJsonObject();
            tasks.add(new TaskDefinition(bookId, id(task.get("id").getAsString()), id(task.get("type").getAsString()), config(task), bool(task, "optional")));
        }
        List<RewardDefinition> rewards = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray("rewards")) {
            JsonObject reward = element.getAsJsonObject();
            rewards.add(new RewardDefinition(bookId, id(reward.get("id").getAsString()), id(reward.get("type").getAsString()), config(reward), text(reward, "claim_policy"), bool(reward, "team_reward")));
        }
        JsonObject appearance = value.has("appearance") ? value.getAsJsonObject("appearance") : new JsonObject();
        JsonObject behavior = value.has("behavior") ? value.getAsJsonObject("behavior") : new JsonObject();
        return new QuestDefinition(bookId, id(value.get("id").getAsString()), chapterId,
                text(value, "title"), text(value, "subtitle"), text(value, "description"), text(value, "icon"),
                decimal(value, "x", 0.0), decimal(value, "y", 0.0), dependencies, tasks, rewards, text(value, "legacy_id"),
                new QuestAppearance(text(appearance, "shape", "chamfer"), decimal(appearance, "size", 1.0),
                        decimal(appearance, "icon_scale", 1.0), decimal(appearance, "min_width", 0.0)),
                new QuestBehavior(bool(behavior, "hide_until_dependencies_visible"),
                        bool(behavior, "hide_until_dependencies_complete"), bool(behavior, "invisible_until_complete"),
                        integer(behavior, "visible_after_tasks"), bool(behavior, "hide_details_until_startable"),
                        bool(behavior, "hide_text_until_complete"), bool(behavior, "hide_lock_icon"),
                        DependencyRequirement.parse(text(behavior, "dependency_requirement", "all_completed")),
                        integer(behavior, "minimum_required_dependencies"), bool(behavior, "sequential_tasks"),
                        bool(behavior, "repeatable"), integer(behavior, "repeat_cooldown_seconds"),
                        bool(behavior, "ignore_reward_blocking")),
                stringMap(value, "extensions"));
    }

    private static JsonObject encodeLocalization(BookLocalization localization) {
        JsonObject value = new JsonObject();
        value.addProperty("fallback_locale", localization.fallbackLocale());
        JsonObject translations = new JsonObject();
        localization.translations().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> translations.add(entry.getKey(), encodeStringMap(entry.getValue())));
        value.add("translations", translations);
        return value;
    }

    private static BookLocalization decodeLocalization(JsonObject root) {
        if (!root.has("localization")) return BookLocalization.EMPTY;
        JsonObject value = root.getAsJsonObject("localization");
        Map<String, Map<String, String>> translations = new TreeMap<>();
        if (value.has("translations")) value.getAsJsonObject("translations").entrySet()
                .forEach(entry -> translations.put(entry.getKey(), stringMap(entry.getValue().getAsJsonObject())));
        return new BookLocalization(text(value, "fallback_locale", "en_us"), translations);
    }

    private static JsonObject encodeStringMap(Map<String, String> values) {
        JsonObject result = new JsonObject();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.addProperty(entry.getKey(), entry.getValue()));
        return result;
    }

    private static Map<String, String> config(JsonObject value) {
        return stringMap(value, "config");
    }

    private static Map<String, String> stringMap(JsonObject parent, String key) {
        return parent.has(key) ? stringMap(parent.getAsJsonObject(key)) : Map.of();
    }

    private static Map<String, String> stringMap(JsonObject value) {
        Map<String, String> result = new TreeMap<>();
        value.entrySet().forEach(e -> result.put(e.getKey(), e.getValue().getAsString()));
        return result;
    }

    private static String text(JsonObject value, String key) { return value.has(key) ? value.get(key).getAsString() : ""; }
    private static String text(JsonObject value, String key, String fallback) { return value.has(key) ? value.get(key).getAsString() : fallback; }
    private static int integer(JsonObject value, String key) { return value.has(key) ? value.get(key).getAsInt() : 0; }
    private static double decimal(JsonObject value, String key, double fallback) { return value.has(key) ? value.get(key).getAsDouble() : fallback; }
    private static boolean bool(JsonObject value, String key) { return value.has(key) && value.get(key).getAsBoolean(); }
    private static ResourceLocation id(String value) {
        ResourceLocation result = ResourceLocation.tryParse(value);
        if (result == null) throw new JsonParseException("Invalid resource location " + value);
        return result;
    }
}
