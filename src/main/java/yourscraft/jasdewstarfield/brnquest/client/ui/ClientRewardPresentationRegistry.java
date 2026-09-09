package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypes;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Client reward presentation registry frozen with the task presentation registry. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public final class ClientRewardPresentationRegistry {
    private static final Map<ResourceLocation, ClientRewardPresentation> PRESENTATIONS = new ConcurrentHashMap<>();
    private static final ClientRewardPresentation FALLBACK = new ClientRewardPresentation() {};
    private static volatile boolean frozen;

    static {
        register(yourscraft.jasdewstarfield.brnquest.task.advancement.AdvancementConfig.ID, new AdvancementPresentation.Reward());
        register(RewardTypes.COMMAND, new ClientRewardPresentation() {
            public String symbol(RewardView reward) { return ">_"; }
            public Component title(RewardPresentationContext context) {
                String title = context.reward().config().getOrDefault("title", "");
                return title.isBlank() ? typeName(context.reward()) : Component.literal(title);
            }
            public Component typeName(RewardView reward) {
                return Component.translatable("screen.brnquest.type.reward.command");
            }
        });
        register(RewardTypes.ITEM, new ClientRewardPresentation() {
            public String itemSnbt(RewardView reward) { return reward.config().getOrDefault("item", ""); }
            public ItemStack displayedItem(RewardView reward, ItemStack parsedItem) {
                if (parsedItem == null || parsedItem.isEmpty()) return ItemStack.EMPTY;
                ItemStack displayed = parsedItem.copy();
                displayed.setCount(displayedCount(reward, displayed.getCount()));
                return displayed;
            }
            public Component typeName(RewardView reward) {
                return Component.translatable("screen.brnquest.type.reward.item");
            }

        });
        register(RewardTypes.CUSTOM, new ClientRewardPresentation() {
            public String symbol(RewardView reward) { return "◆"; }
            public Component typeName(RewardView reward) {
                return Component.translatable("screen.brnquest.type.reward.custom");
            }
        });
        register(RewardTypes.XP, experience(false));
        register(RewardTypes.XP_LEVELS, experience(true));
    }

    private ClientRewardPresentationRegistry() {}

    public static synchronized void register(ResourceLocation id, ClientRewardPresentation presentation) {
        if (frozen) throw new IllegalStateException("Client reward presentation registry is already frozen");
        if (PRESENTATIONS.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(presentation)) != null) {
            throw new IllegalArgumentException("Client reward presentation is already registered: " + id);
        }
    }

    public static ClientRewardPresentation get(ResourceLocation id) {
        return PRESENTATIONS.getOrDefault(id, FALLBACK);
    }

    public static synchronized void freeze() { frozen = true; }
    public static boolean isFrozen() { return frozen; }

    /** Computes the effective visual stack size without mutating the cached parsed ItemStack. */
    static int displayedCount(RewardView reward, int baseCount) {
        int multiplier;
        try {
            multiplier = Math.max(1, Integer.parseInt(reward.config().getOrDefault("count", "1")
                    .replaceAll("[^0-9-]", "")));
        } catch (NumberFormatException ignored) {
            multiplier = 1;
        }
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.max(1, baseCount) * multiplier);
    }

    private static ClientRewardPresentation experience(boolean levels) {
        return new ClientRewardPresentation() {
            public String symbol(RewardView reward) { return "✦"; }
            public Component typeName(RewardView reward) {
                return Component.translatable(levels ? "screen.brnquest.type.reward.xp_levels" : "screen.brnquest.type.reward.xp");
            }
        };
    }
}
