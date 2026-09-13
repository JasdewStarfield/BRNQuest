package yourscraft.jasdewstarfield.brnquest.data;

import com.google.gson.*;
import java.util.*;

/** Shared book policies; creation defaults are materialized only when adding an entry. */
public record BookSettings(boolean consumeItems, boolean rewardTeam, String rewardClaimPolicy,
                           boolean suppressAutoClaim, boolean pauseGame) {
    public static final BookSettings DEFAULT = new BookSettings(false, false, "manual", false, false);
    public BookSettings {
        if (Arrays.stream(RewardClaimPolicy.values()).noneMatch(p -> p.serializedName().equals(rewardClaimPolicy)))
            throw new IllegalArgumentException("Invalid default reward claim policy: " + rewardClaimPolicy);
    }
    public Map<String, String> values() {
        return Map.of("consume_items", Boolean.toString(consumeItems), "reward_team", Boolean.toString(rewardTeam),
                "reward_claim_policy", rewardClaimPolicy, "suppress_auto_claim", Boolean.toString(suppressAutoClaim),
                "pause_game", Boolean.toString(pauseGame));
    }
    public JsonObject toJson() {
        var json = new JsonObject();
        new TreeMap<>(values()).forEach((key, value) -> {
            if (key.equals("reward_claim_policy")) json.addProperty(key, value);
            else json.addProperty(key, Boolean.parseBoolean(value));
        });
        return json;
    }
    public static BookSettings fromJson(JsonElement element) {
        if (element == null) return DEFAULT;
        if (!element.isJsonObject()) throw new IllegalArgumentException("Book settings must be an object");
        var json = element.getAsJsonObject();
        for (String key : json.keySet()) {
            if (!DEFAULT.values().containsKey(key)) throw new IllegalArgumentException("Unknown book setting: " + key);
            var value = json.get(key);
            if (!value.isJsonPrimitive() || (key.equals("reward_claim_policy")
                    ? !value.getAsJsonPrimitive().isString() : !value.getAsJsonPrimitive().isBoolean()))
                throw new IllegalArgumentException("Invalid book setting: " + key);
        }
        return new BookSettings(bool(json, "consume_items"), bool(json, "reward_team"),
                json.has("reward_claim_policy") ? json.get("reward_claim_policy").getAsString() : "manual",
                bool(json, "suppress_auto_claim"), bool(json, "pause_game"));
    }
    private static boolean bool(JsonObject json, String key) { return json.has(key) && json.get(key).getAsBoolean(); }
    /** A chapter's explicit false must override a consuming book default. */
    public Map<String, String> taskDefaults(ChapterDefinition chapter) {
        return Map.of("consume_items", Boolean.toString(chapter.consumeItems() == null ? consumeItems : chapter.consumeItems()));
    }
}
