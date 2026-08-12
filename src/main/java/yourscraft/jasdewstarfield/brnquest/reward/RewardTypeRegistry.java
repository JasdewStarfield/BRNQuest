package yourscraft.jasdewstarfield.brnquest.reward;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Built-in rewards share one execution path with future third-party reward types. */
public final class RewardTypeRegistry {
    private static final Map<ResourceLocation, RewardType<?>> TYPES = new ConcurrentHashMap<>();
    static {
        register(ResourceLocation.fromNamespaceAndPath("brnquest", "item"), new RewardType<Map<String, String>>() {
            public com.mojang.serialization.Codec<Map<String, String>> configCodec() { return com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING, com.mojang.serialization.Codec.STRING); }
            public RewardResult execute(net.minecraft.server.level.ServerPlayer player, RewardDefinition definition) {
            try {
                CompoundTag tag = TagParser.parseTag(definition.config().getOrDefault("item", "{}"));
                ItemStack stack = ItemStack.parseOptional(player.registryAccess(), tag);
                int multiplier = Integer.parseInt(definition.config().getOrDefault("count", "1").replaceAll("[^0-9-]", ""));
                if (multiplier > 1) stack.setCount(stack.getCount() * multiplier);
                if (stack.isEmpty()) return RewardResult.failure("Invalid item reward");
                if (!player.getInventory().add(stack)) player.drop(stack, false);
                return RewardResult.success("Item reward delivered");
            } catch (Exception exception) { return RewardResult.failure(exception.getMessage()); }
            }
        });
        register(ResourceLocation.fromNamespaceAndPath("brnquest", "custom"), new RewardType<Map<String, String>>() {
            public com.mojang.serialization.Codec<Map<String, String>> configCodec() { return com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING, com.mojang.serialization.Codec.STRING); }
            public RewardResult execute(net.minecraft.server.level.ServerPlayer player, RewardDefinition definition) { return RewardResult.success("Custom reward acknowledged"); }
        });
    }
    private RewardTypeRegistry() {}
    public static void register(ResourceLocation id, RewardType<?> type) {
        if (TYPES.putIfAbsent(java.util.Objects.requireNonNull(id), java.util.Objects.requireNonNull(type)) != null) {
            throw new IllegalArgumentException("Reward type is already registered: " + id);
        }
    }
    public static RewardType<?> get(ResourceLocation id) { return TYPES.get(id); }
}
