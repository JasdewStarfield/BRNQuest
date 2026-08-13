package yourscraft.jasdewstarfield.brnquest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Extensible task registry with a construction-time registration window. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public final class TaskTypeRegistry {
    private static final Map<ResourceLocation, TaskType<?>> TYPES = new ConcurrentHashMap<>();
    private static volatile boolean frozen;

    static {
        register(TaskTypes.CHECKMARK, new CheckmarkTask());
        register(TaskTypes.CUSTOM, new ProgressTask());
        register(TaskTypes.ITEM, new ItemTask());
    }

    private TaskTypeRegistry() {}

    /**
     * Registers a type during mod construction or common setup.
     * The registry is frozen before the first server resource reload.
     */
    public static synchronized void register(ResourceLocation id, TaskType<?> type) {
        if (frozen) throw new IllegalStateException("Task type registry is already frozen");
        if (TYPES.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(type)) != null) {
            throw new IllegalArgumentException("Task type is already registered: " + id);
        }
    }

    public static TaskType<?> get(ResourceLocation id) { return TYPES.get(id); }

    /** Closes the public registration window before task books are decoded. */
    public static synchronized void freeze() { frozen = true; }

    public static boolean isFrozen() { return frozen; }

    private static final class CheckmarkTask implements TaskType<Map<String, String>> {
        public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
        public boolean satisfied(TaskContext context, Map<String, String> config) {
            return context.progress() >= 1;
        }
        public boolean allowsManualSubmission(Map<String, String> config) { return true; }
        public boolean acceptsQuestCompletionIntent(Map<String, String> config) { return true; }
        public TaskSubmissionResult submit(TaskContext context, Map<String, String> config) {
            return TaskSubmissionResult.accepted();
        }
        public Component describe(yourscraft.jasdewstarfield.brnquest.api.TaskView task, Map<String, String> config) {
            return Component.literal(config.getOrDefault("title", task.typeId().toString()));
        }
    }

    private static final class ProgressTask implements TaskType<Map<String, String>> {
        public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
        public boolean satisfied(TaskContext context, Map<String, String> config) {
            return context.progress() >= 1;
        }
        public Component describe(yourscraft.jasdewstarfield.brnquest.api.TaskView task, Map<String, String> config) {
            return Component.literal(config.getOrDefault("title", task.typeId().toString()));
        }
    }

    /** Typed runtime view of the stable schema-1 string map for item objectives. */
    private record ItemTaskConfig(String item, String count, String consumeItems, String consume, String title) {
        private static final Codec<ItemTaskConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("item").forGetter(ItemTaskConfig::item),
                Codec.STRING.optionalFieldOf("count", "1").forGetter(ItemTaskConfig::count),
                Codec.STRING.optionalFieldOf("consume_items", "").forGetter(ItemTaskConfig::consumeItems),
                Codec.STRING.optionalFieldOf("consume", "false").forGetter(ItemTaskConfig::consume),
                Codec.STRING.optionalFieldOf("title", "").forGetter(ItemTaskConfig::title)
        ).apply(instance, ItemTaskConfig::new));

        boolean consumesItems() {
            String value = consumeItems.isBlank() ? consume : consumeItems;
            return "true".equalsIgnoreCase(value) || "1b".equalsIgnoreCase(value);
        }
    }

    private static final class ItemTask implements TaskType<ItemTaskConfig> {
        public Codec<ItemTaskConfig> configCodec() { return ItemTaskConfig.CODEC; }

        public boolean satisfied(TaskContext context, ItemTaskConfig config) {
            if (context.progress() >= 1) return true;
            ItemStack expected = expected(context.player(), config);
            if (expected.isEmpty()) return false;
            int required = requiredCount(config, expected);
            return context.player().getInventory().items.stream()
                    .filter(stack -> ItemStack.isSameItemSameComponents(stack, expected))
                    .mapToInt(ItemStack::getCount).sum() >= required;
        }

        public boolean consume(TaskContext context, ItemTaskConfig config) {
            if (!config.consumesItems()) return true;
            ItemStack expected = expected(context.player(), config);
            int remaining = requiredCount(config, expected);
            for (ItemStack stack : context.player().getInventory().items) {
                if (ItemStack.isSameItemSameComponents(stack, expected)) {
                    int removed = Math.min(remaining, stack.getCount());
                    stack.shrink(removed);
                    remaining -= removed;
                    if (remaining == 0) return true;
                }
            }
            return false;
        }

        public boolean allowsManualSubmission(ItemTaskConfig config) { return true; }
        public boolean reevaluateOnInventoryChange(ItemTaskConfig config) { return !config.consumesItems(); }
        public Component describe(yourscraft.jasdewstarfield.brnquest.api.TaskView task, ItemTaskConfig config) {
            return Component.literal(config.title.isBlank() ? config.item : config.title);
        }

        private int requiredCount(ItemTaskConfig config, ItemStack expected) {
            try {
                long parsed = Long.parseLong(config.count.replaceAll("[^0-9-]", ""));
                return (int) Math.max(1, Math.min(Integer.MAX_VALUE, parsed));
            } catch (NumberFormatException ignored) {
                return Math.max(1, expected.getCount());
            }
        }

        private ItemStack expected(net.minecraft.server.level.ServerPlayer player, ItemTaskConfig config) {
            try {
                CompoundTag tag = TagParser.parseTag(config.item);
                return ItemStack.parseOptional(player.registryAccess(), tag);
            } catch (Exception ignored) {
                return ItemStack.EMPTY;
            }
        }
    }
}
