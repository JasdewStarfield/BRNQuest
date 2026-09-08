package yourscraft.jasdewstarfield.brnquest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** String-map compatible command settings; only explicitly supported placeholders are interpreted. */
public record CommandRewardConfig(String command, String sourceMode, int permissionLevel,
                                  boolean silent, String feedback) {
    private static final Pattern UNSUPPORTED = Pattern.compile("\\{(?:chapter|quest|team|team_id|long_team_id|member_count|online_member_count)\\}");
    public static final Codec<CommandRewardConfig> CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING)
            .flatXmap(CommandRewardConfig::decode, value -> DataResult.success(value.encode()));

    public static DataResult<CommandRewardConfig> decode(Map<String, String> values) {
        try {
            String command = normalize(values.getOrDefault("command", ""));
            if (command.isBlank() || command.length() > 32767 || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0)
                throw new IllegalArgumentException("A single nonempty command is required (maximum 32767 characters)");
            if (UNSUPPORTED.matcher(command).find()) throw new IllegalArgumentException("Unsupported FTB team/quest placeholder");
            String mode = values.getOrDefault("source_mode", "explicit");
            if (!mode.equals("player") && !mode.equals("explicit")) throw new IllegalArgumentException("Unknown command source mode");
            int level = Integer.parseInt(values.getOrDefault("permission_level", "2"));
            if (level < 0 || level > 4) throw new IllegalArgumentException("Permission level must be between 0 and 4");
            String silent = values.getOrDefault("silent", "false");
            if (!silent.equals("true") && !silent.equals("false")) throw new IllegalArgumentException("silent must be true or false");
            if (values.containsKey("ftb.feedback_message") && values.getOrDefault("feedback", "").isBlank()) throw new IllegalArgumentException("Resolve the imported feedback translation key before publishing");
            return DataResult.success(new CommandRewardConfig(command, mode, level, Boolean.parseBoolean(silent),
                    values.getOrDefault("feedback", "")));
        } catch (IllegalArgumentException error) { return DataResult.error(error::getMessage); }
    }
    public static String normalize(String value) {
        String result = value.strip();
        return result.startsWith("/") ? result.substring(1).stripLeading() : result;
    }
    public String expand(String player, int x, int y, int z) {
        return command.replace("{p}", player).replace("{x}", Integer.toString(x))
                .replace("{y}", Integer.toString(y)).replace("{z}", Integer.toString(z));
    }
    private Map<String, String> encode() {
        Map<String, String> result = new TreeMap<>();
        result.put("command", command); result.put("source_mode", sourceMode);
        result.put("permission_level", Integer.toString(permissionLevel));
        result.put("silent", Boolean.toString(silent)); result.put("feedback", feedback);
        return result;
    }
}
