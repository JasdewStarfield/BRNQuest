package yourscraft.jasdewstarfield.brnquest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Built-in and third-party rewards share one decoded execution path. */
public final class RewardTypeRegistry {
    private static final Map<ResourceLocation, RewardType<?>> TYPES = new ConcurrentHashMap<>();
    private static volatile boolean frozen;

    static {
        register(RewardTypes.ITEM, new ItemReward());
        register(RewardTypes.CUSTOM, new RewardType<Map<String, String>>() {
            public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
            public RewardResult execute(ServerPlayer player, RewardDefinition definition, Map<String, String> config) {
                return RewardResult.success("Custom reward acknowledged");
            }
        });
    }

    private RewardTypeRegistry() {}

    /** Registers a type before the first server resource reload freezes the registry. */
    public static synchronized void register(ResourceLocation id, RewardType<?> type) {
        if (frozen) throw new IllegalStateException("Reward type registry is already frozen");
        if (TYPES.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(type)) != null) {
            throw new IllegalArgumentException("Reward type is already registered: " + id);
        }
    }

    public static RewardType<?> get(ResourceLocation id) { return TYPES.get(id); }
    public static synchronized void freeze() { frozen = true; }
    public static boolean isFrozen() { return frozen; }

    /** Typed runtime view of an item reward while schema 1 remains string-map compatible. */
    private record ItemRewardConfig(String item, String count) {
        private static final Codec<ItemRewardConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("item").forGetter(ItemRewardConfig::item),
                Codec.STRING.optionalFieldOf("count", "1").forGetter(ItemRewardConfig::count)
        ).apply(instance, ItemRewardConfig::new));
    }

    private static final class ItemReward implements RewardType<ItemRewardConfig> {
        public Codec<ItemRewardConfig> configCodec() { return ItemRewardConfig.CODEC; }

        public RewardResult execute(ServerPlayer player, RewardDefinition definition, ItemRewardConfig config) {
            try {
                CompoundTag tag = TagParser.parseTag(config.item);
                ItemStack stack = ItemStack.parseOptional(player.registryAccess(), tag);
                int multiplier = Integer.parseInt(config.count.replaceAll("[^0-9-]", ""));
                if (multiplier > 1) stack.setCount(stack.getCount() * multiplier);
                if (stack.isEmpty()) return RewardResult.failure("Invalid item reward");
                ItemStack delivered = stack.copy();
                int before = matchingCount(player, delivered);
                player.getInventory().add(stack);
                int inserted = Math.max(0, matchingCount(player, delivered) - before);
                int remainder = Math.max(0, delivered.getCount() - inserted);
                // Creative inventories clear an uninserted remainder, and Inventory.add's
                // boolean does not report a full fit. Reconstruct the remainder from the actual
                // inventory delta so both survival and creative players receive every item.
                if (remainder > 0) {
                    // Reward overflow is a server delivery fallback, not a player toss. Bypass
                    // the cancellable toss event so another mod cannot silently void the reward.
                    player.drop(delivered.copyWithCount(remainder), false, false);
                }
                return RewardResult.success("Item reward delivered");
            } catch (Exception exception) {
                return RewardResult.failure(exception.getMessage());
            }
        }

        private int matchingCount(ServerPlayer player, ItemStack expected) {
            int total = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack candidate = player.getInventory().getItem(slot);
                if (ItemStack.isSameItemSameComponents(candidate, expected)) total += candidate.getCount();
            }
            return total;
        }
    }
}
