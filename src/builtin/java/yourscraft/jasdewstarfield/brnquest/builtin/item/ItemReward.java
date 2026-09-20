package yourscraft.jasdewstarfield.brnquest.builtin.item;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.BuiltinComposition;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.ItemRewardDelivery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigValueType;

import java.util.List;

/** Item execution and its typed schema belong to the item plugin. */
public final class ItemReward implements RewardType<ItemReward.ItemRewardConfig> {
    public record ItemRewardConfig(String item, String count) {
        private static final Codec<ItemRewardConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("item").forGetter(ItemRewardConfig::item),
                Codec.STRING.optionalFieldOf("count", "1").forGetter(ItemRewardConfig::count)
        ).apply(instance, ItemRewardConfig::new));
    }

        public java.util.Optional<ComposableReward> composition() { return java.util.Optional.of(new BuiltinComposition("item")); }
        public Codec<ItemRewardConfig> configCodec() { return ItemRewardConfig.CODEC; }

        public List<ConfigFieldDescriptor> configFields() {
            return List.of(
                    ConfigFieldDescriptor.field("item", ConfigValueType.ITEM_STACK).asRequired()
                            .withHelp("ItemStack SNBT delivered by this reward"),
                    ConfigFieldDescriptor.field("count", ConfigValueType.INTEGER).withDefault("1")
                            .withRange(1, Integer.MAX_VALUE).withHelp("Stack multiplier")
            );
        }

        public RewardResult execute(RewardContext context, ItemRewardConfig config) {
            try {
                var player = context.player();
                CompoundTag tag = TagParser.parseTag(config.item);
                ItemStack stack = ItemStack.parseOptional(player.registryAccess(), tag);
                int multiplier = Integer.parseInt(config.count.replaceAll("[^0-9-]", ""));
                if (multiplier > 1) stack.setCount(stack.getCount() * multiplier);
                if (stack.isEmpty()) return RewardResult.failure("Invalid item reward");
                ItemRewardDelivery.deliver(player, stack);
                return RewardResult.success("Item reward delivered");
            } catch (Exception exception) {
                return RewardResult.failure(exception.getMessage());
            }
        }


}
