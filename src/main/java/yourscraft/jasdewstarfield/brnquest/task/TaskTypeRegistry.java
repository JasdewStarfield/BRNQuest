package yourscraft.jasdewstarfield.brnquest.task;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.progress.PlayerProgress;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Extensible task registry with server-authoritative built-ins. */
public final class TaskTypeRegistry {
    private static final Map<ResourceLocation, TaskType<?>> TYPES = new ConcurrentHashMap<>();
    static {
        register(ResourceLocation.fromNamespaceAndPath("brnquest", "checkmark"), new ProgressTask());
        register(ResourceLocation.fromNamespaceAndPath("brnquest", "custom"), new ProgressTask());
        register(ResourceLocation.fromNamespaceAndPath("brnquest", "item"), new ItemTask());
    }
    private TaskTypeRegistry() {}
    public static void register(ResourceLocation id, TaskType<?> type) {
        if (TYPES.putIfAbsent(java.util.Objects.requireNonNull(id), java.util.Objects.requireNonNull(type)) != null) {
            throw new IllegalArgumentException("Task type is already registered: " + id);
        }
    }
    public static TaskType<?> get(ResourceLocation id) { return TYPES.get(id); }

    private static final class ProgressTask implements TaskType<Map<String, String>> {
        public com.mojang.serialization.Codec<Map<String, String>> configCodec() { return com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING, com.mojang.serialization.Codec.STRING); }
        public boolean satisfied(ServerPlayer player, TaskDefinition definition, PlayerProgress progress) { return progress.taskProgress(definition.id().toString()) >= 1; }
        public Component describe(TaskDefinition definition) { return Component.literal(definition.typeId().toString()); }
    }

    private static final class ItemTask implements TaskType<Map<String, String>> {
        public com.mojang.serialization.Codec<Map<String, String>> configCodec() { return com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING, com.mojang.serialization.Codec.STRING); }
        public boolean satisfied(ServerPlayer player, TaskDefinition definition, PlayerProgress progress) {
            ItemStack expected = expected(player, definition);
            if (expected.isEmpty()) return false;
            int required = requiredCount(definition, expected);
            return player.getInventory().items.stream().filter(stack -> ItemStack.isSameItemSameComponents(stack, expected)).mapToInt(ItemStack::getCount).sum() >= required;
        }

        public boolean consume(ServerPlayer player, TaskDefinition definition) {
            String consume = definition.config().getOrDefault("consume_items", definition.config().getOrDefault("consume", "false"));
            if (!"true".equalsIgnoreCase(consume) && !"1b".equalsIgnoreCase(consume)) return true;
            ItemStack expected = expected(player, definition);
            int remaining = requiredCount(definition, expected);
            for (ItemStack stack : player.getInventory().items) {
                if (ItemStack.isSameItemSameComponents(stack, expected)) {
                    int removed = Math.min(remaining, stack.getCount());
                    stack.shrink(removed);
                    remaining -= removed;
                    if (remaining == 0) return true;
                }
            }
            return false;
        }

        public Component describe(TaskDefinition definition) { return Component.literal(definition.config().getOrDefault("item", "item")); }

        private int requiredCount(TaskDefinition definition, ItemStack expected) {
            String raw = definition.config().getOrDefault("count", Integer.toString(expected.getCount()));
            try {
                long parsed = Long.parseLong(raw.replaceAll("[^0-9-]", ""));
                return (int) Math.max(1, Math.min(Integer.MAX_VALUE, parsed));
            } catch (NumberFormatException ignored) {
                return Math.max(1, expected.getCount());
            }
        }

        private ItemStack expected(ServerPlayer player, TaskDefinition definition) {
            try {
                String snbt = definition.config().get("item");
                if (snbt == null) return ItemStack.EMPTY;
                CompoundTag tag = TagParser.parseTag(snbt);
                return ItemStack.parseOptional(player.registryAccess(), tag);
            } catch (Exception ignored) { return ItemStack.EMPTY; }
        }
    }
}
