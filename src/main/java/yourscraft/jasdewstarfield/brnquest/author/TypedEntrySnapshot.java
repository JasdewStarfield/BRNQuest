package yourscraft.jasdewstarfield.brnquest.author;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Configuration-only clipboard value. No progress, receipts, leases or source-object lookup is involved. */
public record TypedEntrySnapshot(ResourceLocation bookId, boolean task, ResourceLocation typeId,
                                 Map<String, String> config, boolean optional, String claimPolicy, boolean teamReward) {
    // Leave room for JSON escaping and the existing 256 KiB authoring request envelope.
    public static final int MAX_BYTES = 65536;
    public TypedEntrySnapshot {
        java.util.Objects.requireNonNull(bookId);
        java.util.Objects.requireNonNull(typeId);
        config = Map.copyOf(config);
        if (config.size() > 64 || config.entrySet().stream().anyMatch(e -> e.getKey().isBlank()
                || e.getKey().length() > 128 || e.getValue().length() > 65536))
            throw new IllegalArgumentException("Clipboard configuration exceeds editor limits");
        if (!RewardClaimPolicy.isKnown(claimPolicy)) throw new IllegalArgumentException("Invalid clipboard claim policy");
    }
    public static TypedEntrySnapshot of(TaskDefinition task) {
        return new TypedEntrySnapshot(task.bookId(), true, task.typeId(), task.config(), task.optional(), "manual", false);
    }
    public static TypedEntrySnapshot of(RewardDefinition reward) {
        return new TypedEntrySnapshot(reward.bookId(), false, reward.typeId(), reward.config(), false, reward.claimPolicy(), reward.teamReward());
    }
    public String encode() {
        var json = new JsonObject();
        json.addProperty("book", bookId.toString()); json.addProperty("task", task);
        json.addProperty("type", typeId.toString()); json.addProperty("optional", optional);
        json.addProperty("claim_policy", claimPolicy); json.addProperty("team_reward", teamReward);
        var values = new JsonObject(); new TreeMap<>(config).forEach(values::addProperty); json.add("config", values);
        String result = json.toString();
        if (result.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES
                || new com.google.gson.Gson().toJson(result).getBytes(StandardCharsets.UTF_8).length
                > yourscraft.jasdewstarfield.brnquest.BrnQuestConstants.MAX_EDITOR_METADATA_BYTES - 4096) throw new IllegalArgumentException("Clipboard snapshot is too large");
        return result;
    }
    /** Strict decoding prevents Gson scalar coercions and arbitrary extra envelope fields. */
    public static TypedEntrySnapshot decode(String text) {
        if (text == null || text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Missing or oversized clipboard snapshot");
        var value = JsonParser.parseString(text);
        if (!value.isJsonObject()) throw new IllegalArgumentException("Clipboard snapshot must be an object");
        var json = value.getAsJsonObject();
        if (!json.keySet().equals(Set.of("book", "task", "type", "optional", "claim_policy", "team_reward", "config")))
            throw new IllegalArgumentException("Invalid clipboard fields");
        for (String key : Set.of("book", "type", "claim_policy"))
            if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isString())
                throw new IllegalArgumentException("Expected clipboard string: " + key);
        for (String key : Set.of("task", "optional", "team_reward"))
            if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isBoolean())
                throw new IllegalArgumentException("Expected clipboard boolean: " + key);
        if (!json.get("config").isJsonObject()) throw new IllegalArgumentException("Clipboard config must be an object");
        var config = new TreeMap<String, String>();
        json.getAsJsonObject("config").entrySet().forEach(e -> {
            if (!e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("Clipboard configuration values must be strings");
            config.put(e.getKey(), e.getValue().getAsString());
        });
        return new TypedEntrySnapshot(ResourceLocation.parse(json.get("book").getAsString()), json.get("task").getAsBoolean(),
                ResourceLocation.parse(json.get("type").getAsString()), config, json.get("optional").getAsBoolean(),
                json.get("claim_policy").getAsString(), json.get("team_reward").getAsBoolean());
    }
    /** Same-book restriction is checked on the server as well as in the client paste affordance. */
    public void requireDestination(ResourceLocation destinationBook, boolean destinationTask) {
        if (!bookId.equals(destinationBook) || task != destinationTask)
            throw new IllegalArgumentException("Clipboard kind or source book does not match the destination");
    }
    public TaskDefinition task(ResourceLocation newId) {
        if (!task) throw new IllegalArgumentException("Not a task snapshot");
        return new TaskDefinition(bookId, newId, typeId, config, optional);
    }
    public RewardDefinition reward(ResourceLocation newId) {
        if (task) throw new IllegalArgumentException("Not a reward snapshot");
        return new RewardDefinition(bookId, newId, typeId, config, claimPolicy, teamReward);
    }
}
