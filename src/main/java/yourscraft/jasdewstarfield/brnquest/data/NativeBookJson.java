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
        JsonArray groups = new JsonArray();
        book.chapterGroups().stream().sorted(Comparator.comparingInt(ChapterGroupDefinition::order)
                .thenComparing(g -> g.id().toString())).forEach(group -> {
            JsonObject value = new JsonObject();
            value.addProperty("id", group.id().toString());
            value.addProperty("title", group.title());
            value.addProperty("order", group.order());
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
            groups.add(new ChapterGroupDefinition(bookId, id(value.get("id").getAsString()), text(value, "title"), integer(value, "order")));
        }
        List<ChapterDefinition> chapters = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("chapters")) chapters.add(decodeChapter(bookId, element.getAsJsonObject()));
        Map<String, ResourceLocation> aliases = new TreeMap<>();
        if (root.has("legacy_ids")) root.getAsJsonObject("legacy_ids").entrySet().forEach(e -> aliases.put(e.getKey(), id(e.getValue().getAsString())));
        return new QuestBookDefinition(bookId, schema, text(root, "title"), groups, chapters, aliases);
    }

    private static JsonObject encodeChapter(ChapterDefinition chapter) {
        JsonObject value = new JsonObject();
        value.addProperty("id", chapter.id().toString());
        value.addProperty("group_id", chapter.groupId().toString());
        value.addProperty("title", chapter.title());
        value.addProperty("icon", chapter.icon());
        value.addProperty("order", chapter.order());
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
        return new ChapterDefinition(bookId, chapterId, id(value.get("group_id").getAsString()), text(value, "title"), text(value, "icon"), integer(value, "order"), quests);
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
        return new QuestDefinition(bookId, id(value.get("id").getAsString()), chapterId,
                text(value, "title"), text(value, "subtitle"), text(value, "description"), text(value, "icon"),
                value.get("x").getAsDouble(), value.get("y").getAsDouble(), dependencies, tasks, rewards, text(value, "legacy_id"));
    }

    private static Map<String, String> config(JsonObject value) {
        Map<String, String> result = new TreeMap<>();
        value.getAsJsonObject("config").entrySet().forEach(e -> result.put(e.getKey(), e.getValue().getAsString()));
        return result;
    }

    private static String text(JsonObject value, String key) { return value.has(key) ? value.get(key).getAsString() : ""; }
    private static int integer(JsonObject value, String key) { return value.has(key) ? value.get(key).getAsInt() : 0; }
    private static boolean bool(JsonObject value, String key) { return value.has(key) && value.get(key).getAsBoolean(); }
    private static ResourceLocation id(String value) {
        ResourceLocation result = ResourceLocation.tryParse(value);
        if (result == null) throw new JsonParseException("Invalid resource location " + value);
        return result;
    }
}
