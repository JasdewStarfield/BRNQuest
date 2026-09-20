package yourscraft.jasdewstarfield.brnquest.builtin.observation.advancement;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.task.*;

import com.mojang.serialization.*;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.editor.*;
import java.util.*;

/** Schema-1 string maps retain unknown extension keys; a criterion is never ambiguous across a group. */
public record AdvancementConfig(String selector, String criterion, boolean all) {
    public static final ResourceLocation ID = ResourceLocation.parse("brnquest:advancement");
    public static final Codec<Map<String, String>> CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING).validate(values -> {
        try { parse(values); return DataResult.success(values); }
        catch (IllegalArgumentException error) { return DataResult.error(error::getMessage); }
    });
    public static AdvancementConfig parse(Map<String, String> values) {
        String selector = values.getOrDefault("advancement", "").strip();
        String criterion = values.getOrDefault("criterion", "").strip();
        String mode = values.getOrDefault("mode", "any").strip().toLowerCase(Locale.ROOT);
        if (selector.isBlank() || ResourceLocation.tryParse(selector.startsWith("#") ? selector.substring(1) : selector) == null)
            throw new IllegalArgumentException("advancement: expected an ID or #group");
        if (selector.startsWith("#") && !criterion.isEmpty())
            throw new IllegalArgumentException("criterion: only supported with a single advancement ID");
        if (!mode.equals("any") && !mode.equals("all")) throw new IllegalArgumentException("mode: expected any/all");
        return new AdvancementConfig(selector, criterion, mode.equals("all"));
    }
    public static Map<String, String> normalize(Map<String, String> values) {
        var result = new TreeMap<>(values);
        for (String key : List.of("advancement", "criterion")) result.computeIfPresent(key, (k,v) -> v.strip());
        result.computeIfPresent("mode", (k,v) -> v.strip().toLowerCase(Locale.ROOT));
        return result;
    }
    public static List<ConfigFieldDescriptor> fields(boolean task) {
        var fields = new ArrayList<ConfigFieldDescriptor>();
        fields.add(ConfigFieldDescriptor.field("title", ConfigValueType.TEXT).withLabel("screen.brnquest.editor.config.title"));
        fields.add(ConfigFieldDescriptor.field("advancement", ConfigValueType.TEXT).asRequired()
                .withDefault("minecraft:story/root").withServerSource(ID).withLabel("screen.brnquest.advancement.selector")
                .withHelp(task ? "screen.brnquest.advancement.task_hint" : "screen.brnquest.advancement.reward_hint"));
        fields.add(ConfigFieldDescriptor.field("criterion", ConfigValueType.TEXT).withDefault("").withServerSource(AdvancementFieldSource.CRITERIA)
                .withLabel("screen.brnquest.advancement.criterion").withHelp("screen.brnquest.advancement.criterion_hint"));
        if (task) fields.add(ConfigFieldDescriptor.enumeration("mode", List.of("any", "all")).withDefault("any")
                .withLabel("screen.brnquest.advancement.mode").withValueLabels(Map.of(
                        "any", "screen.brnquest.advancement.any", "all", "screen.brnquest.advancement.all")));
        return List.copyOf(fields);
    }
}
