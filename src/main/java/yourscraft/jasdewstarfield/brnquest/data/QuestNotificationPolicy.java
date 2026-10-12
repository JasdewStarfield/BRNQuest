package yourscraft.jasdewstarfield.brnquest.data;

import java.util.Map;
import java.util.TreeMap;

/** Author-owned notification switches travel with the book, independently of client sound preferences. */
public record QuestNotificationPolicy(boolean taskCompleted, boolean questCompleted, boolean rewardClaimed) {
    public static final String TASK = "notify_task_complete", QUEST = "notify_quest_complete", REWARD = "notify_reward_claimed";
    public static final QuestNotificationPolicy DEFAULT = new QuestNotificationPolicy(true, true, false);
    private static String key(String field) { return "brnquest:" + field; }

    public static QuestNotificationPolicy read(Map<String, String> extensions) {
        return new QuestNotificationPolicy(read(extensions, key(TASK), true), read(extensions, key(QUEST), true),
                read(extensions, key(REWARD), false));
    }
    private static boolean read(Map<String, String> values, String key, boolean fallback) {
        String value = values.get(key);
        if (value == null) return fallback;
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Invalid notification flag: " + key);
        return Boolean.parseBoolean(value);
    }
    public Map<String, String> fields() {
        return Map.of(TASK, Boolean.toString(taskCompleted), QUEST, Boolean.toString(questCompleted),
                REWARD, Boolean.toString(rewardClaimed));
    }
    /** Partial/legacy requests preserve other settings; explicit defaults omit redundant reserved keys. */
    public static Map<String, String> apply(Map<String, String> extensions, Map<String, String> changes) {
        var result = new TreeMap<>(extensions);
        for (var entry : DEFAULT.fields().entrySet()) {
            if (!changes.containsKey(entry.getKey())) continue;
            boolean value = read(changes, entry.getKey(), Boolean.parseBoolean(entry.getValue()));
            if (Boolean.toString(value).equals(entry.getValue())) result.remove(key(entry.getKey()));
            else result.put(key(entry.getKey()), Boolean.toString(value));
        }
        return Map.copyOf(result);
    }
}
