package yourscraft.jasdewstarfield.brnquestexample;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigNormalizationContext;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigValueType;
import yourscraft.jasdewstarfield.brnquest.task.TaskType;
import yourscraft.jasdewstarfield.brnquest.task.TaskContext;
import yourscraft.jasdewstarfield.brnquest.task.TaskSubmissionSelection;
import yourscraft.jasdewstarfield.brnquest.task.TaskSubmissionResult;

import java.util.*;

/** Independent item objective: no built-in matcher, definition, network or mutable progress APIs. */
final class ExampleItemTask implements TaskType<Map<String, String>> {
    private static final Codec<Map<String, String>> CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING)
            .flatXmap(ExampleItemTask::validate, ExampleItemTask::validate);

    private static DataResult<Map<String, String>> validate(Map<String, String> config) {
        try {
            itemId(config); count(config);
            for (String key : List.of("consume", "crafting_only")) {
                String value = config.getOrDefault(key, "false");
                if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException(key + " must be true or false");
            }
            return DataResult.success(config);
        } catch (IllegalArgumentException invalid) { return DataResult.error(invalid::getMessage); }
    }

    static ResourceLocation itemId(Map<String, String> config) { return ResourceLocation.parse(config.getOrDefault("item", "minecraft:stone")); }
    static int count(Map<String, String> config) {
        int count = Integer.parseInt(config.getOrDefault("count", "2"));
        if (count < 1 || count > 2304) throw new IllegalArgumentException("count must be between 1 and 2304");
        return count;
    }
    static boolean crafting(Map<String, String> config) { return Boolean.parseBoolean(config.getOrDefault("crafting_only", "false")); }
    static boolean consuming(Map<String, String> config) { return Boolean.parseBoolean(config.getOrDefault("consume", "true")); }
    static boolean matches(Map<String, String> config, ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemId(config));
    }

    public Codec<Map<String, String>> configCodec() { return CODEC; }
    public Map<String, String> normalizeConfig(ConfigNormalizationContext context, Map<String, String> config) {
        validate(config).result().orElseThrow(() -> new IllegalArgumentException("Invalid example item configuration"));
        // Resolve against the caller's live lookup, including resources supplied by other mods.
        context.registries().ifPresent(lookup -> {
            if (lookup.lookupOrThrow(Registries.ITEM).get(ResourceKey.create(Registries.ITEM, itemId(config))).isEmpty()
                    || itemId(config).equals(ResourceLocation.parse("minecraft:air"))) {
                throw new IllegalArgumentException("Unknown example item: " + itemId(config));
            }
        });
        return Map.of("item", itemId(config).toString(), "count", Integer.toString(count(config)));
    }

    public List<ConfigFieldDescriptor> configFields() {
        return List.of(ConfigFieldDescriptor.field("item", ConfigValueType.RESOURCE_LOCATION).withDefault("minecraft:stone").asRequired(),
                ConfigFieldDescriptor.field("count", ConfigValueType.INTEGER).withDefault("2").withRange(1, 2304),
                ConfigFieldDescriptor.field("consume", ConfigValueType.BOOLEAN).withDefault("true"),
                ConfigFieldDescriptor.field("crafting_only", ConfigValueType.BOOLEAN).withDefault("false"));
    }
    public boolean satisfied(TaskContext context, Map<String, String> config) {
        return context.progress() >= (crafting(config) ? count(config) : 1);
    }
    public boolean consume(TaskContext context, Map<String, String> config) { return satisfied(context, config); }
    public boolean allowsManualSubmission(Map<String, String> config) { return !crafting(config); }
    public boolean reevaluateOnInventoryChange(Map<String, String> config) { return true; }

    @Override public TaskSubmissionResult submit(TaskContext context, Map<String, String> config, TaskSubmissionSelection selection) {
        if (crafting(config)) return TaskSubmissionResult.failure("CRAFT_REQUIRED", "Craft the configured output");
        if (consuming(config) && !selection.explicit()) return TaskSubmissionResult.failure("SELECTION_REQUIRED", "Choose inventory slots");
        var inventory = context.player().getInventory();
        var slots = selection.explicit() ? selection.inventorySlots() : java.util.stream.IntStream.range(0, 36).boxed().toList();
        int remaining = count(config);
        Map<Integer, Integer> removals = new LinkedHashMap<>();
        for (int slot : slots) {
            var stack = inventory.getItem(slot);
            if (!matches(config, stack)) {
                if (selection.explicit()) return TaskSubmissionResult.failure("INVALID_SELECTION", "A selected stack no longer matches");
                continue;
            }
            int amount = Math.min(remaining, stack.getCount());
            if (amount > 0) removals.put(slot, amount);
            remaining -= amount;
        }
        if (remaining > 0) return TaskSubmissionResult.failure("INSUFFICIENT_ITEMS", "Not enough selected items");
        // Every selected slot is checked before any mutation. Only the core can persist the receipt.
        if (consuming(config)) {
            removals.forEach((slot, amount) -> inventory.getItem(slot).shrink(amount));
            inventory.setChanged();
        }
        return TaskSubmissionResult.accepted();
    }

    @Override public long craftedProgress(TaskContext context, Map<String, String> config, ItemStack crafted) {
        if (!crafting(config) || !matches(config, crafted) || context.progress() >= count(config)) return context.progress();
        return context.progress() + Math.min((long) crafted.getCount(), count(config) - context.progress());
    }
    public Component describe(TaskView task, Map<String, String> config) {
        return Component.translatable("screen.brnquest_example.task.item.title", count(config), itemId(config).toString());
    }
}
