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
        register(yourscraft.jasdewstarfield.brnquest.reward.table.RewardTableReward.ID, new ClientRewardPresentation() {
            public String symbol(RewardView reward) { return "▤"; }
            public Component interactionHint(RewardPresentationContext context) {
                if (!context.claimed() && !context.claimable()) return ClientRewardPresentation.super.interactionHint(context);
                return RewardTableClientState.hint(context.reward().id().toString(), context.claimed());
            }
            public Component typeName(RewardView reward) { return Component.translatable("screen.brnquest.type.reward.reward_table"); }
            public Component title(RewardPresentationContext context) {
                String title = context.reward().config().getOrDefault("title", "");
                return title.isBlank() ? typeName(context.reward()) : Component.literal(title);
            }
            public java.util.Optional<java.util.List<Component>> resolvedOptions(RewardView view) {
                try {
                    var tree = yourscraft.jasdewstarfield.brnquest.reward.table.RewardTableTree.parse(view.config().get("table"));
                    java.util.List<Component> options = new java.util.ArrayList<>();
                    describeTable(view,tree,"",options);
                    return java.util.Optional.of(java.util.List.copyOf(options));
                } catch (RuntimeException error) { return java.util.Optional.of(java.util.List.of(Component.literal("Invalid reward table"))); }
            }
        });
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
    /** Preview configuration recursively without drawing random outcomes or creating server attempts. */
    private static void describeTable(RewardView view,yourscraft.jasdewstarfield.brnquest.reward.table.RewardTableTree tree,
            String indent,java.util.List<Component> options) {
        options.add(Component.literal(indent).append(Component.translatable("screen.brnquest.reward_table.mode."+tree.mode())));
        if(tree.mode().equals("random")) options.add(Component.literal(indent).append(Component.translatable("screen.brnquest.reward_table.preview",
                tree.rolls(),Component.translatable("screen.brnquest.reward_table."+(tree.replacement()?"with_replacement":"without_replacement")),tree.emptyWeight().toPlainString())));
        for(var entry:tree.entries()) {
            var type=ResourceLocation.parse(entry.get("type").getAsString());
            var config=yourscraft.jasdewstarfield.brnquest.reward.table.RewardTableTree.config(entry);
            var child=new RewardView(view.bookId(),view.id(),type,config,"manual",false);
            var label=Component.literal(indent+"  ").append(previewEntry(child));
            if(tree.mode().equals("random"))label.append(" · ").append(Component.translatable(
                    yourscraft.jasdewstarfield.brnquest.reward.table.RewardTableTree.always(entry)?"screen.brnquest.reward_table.guaranteed":"screen.brnquest.reward_table.weight_summary",
                    yourscraft.jasdewstarfield.brnquest.reward.table.RewardTableTree.weight(entry).toPlainString()));
            options.add(label);
            if(entry.has("table"))describeTable(view,yourscraft.jasdewstarfield.brnquest.reward.table.RewardTableTree.parse(entry.get("table").toString()),indent+"  ",options);
        }
    }

    /** Reuse reward presentation and stack multipliers; inspecting a table never prepares or grants a reward. */
    private static Component previewEntry(RewardView reward) {
        var presentation = get(reward.typeId());
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        ItemStack stack = ItemStack.EMPTY;
        try {
            String snbt = presentation.itemSnbt(reward);
            if (!snbt.isBlank() && minecraft.level != null) {
                stack = presentation.displayedItem(reward, ItemStack.parseOptional(minecraft.level.registryAccess(),
                        net.minecraft.nbt.TagParser.parseTag(snbt)));
            }
        } catch (Exception ignored) {
            // A missing registry entry or malformed display stack must not hide the remaining table entries.
        }
        Component detail;
        if (!stack.isEmpty()) {
            detail = stack.getHoverName().copy().append(" × " + stack.getCount());
        } else if (reward.typeId().equals(RewardTypes.XP) || reward.typeId().equals(RewardTypes.XP_LEVELS)) {
            String key = reward.typeId().equals(RewardTypes.XP) ? "xp" : "xp_levels";
            detail = presentation.typeName(reward).copy().append(" × " + reward.config().getOrDefault(key, "0"));
        } else {
            detail = presentation.title(new RewardPresentationContext(minecraft, reward, false, false, stack));
            if (detail.equals(Component.translatable("screen.brnquest.reward.unknown"))) {
                detail = presentation.typeName(reward);
            }
        }
        // Keep an author's label while still exposing the actual item and quantity underneath that label.
        String title = reward.config().getOrDefault("title", "");
        return title.isBlank() || detail.getString().equals(title) ? detail
                : Component.literal(title).append(" · ").append(detail);
    }

}
