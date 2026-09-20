package yourscraft.jasdewstarfield.brnquest.builtin.client;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import yourscraft.jasdewstarfield.brnquest.client.ui.*;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypes;
/** Registers all remaining built-in views and their private editors at client construction time. */
public final class BuiltinClientTypes {
    private BuiltinClientTypes() {}
    public static void register() {

        registerTask(yourscraft.jasdewstarfield.brnquest.builtin.observation.encounter.EncounterConfig.OBSERVE,new EncounterPresentation(true));
        registerTask(yourscraft.jasdewstarfield.brnquest.builtin.observation.encounter.EncounterConfig.KILL,new EncounterPresentation(false));
        registerTask(yourscraft.jasdewstarfield.brnquest.builtin.observation.advancement.AdvancementConfig.ID, new AdvancementPresentation.Task());
        for (String kind : java.util.List.of("dimension", "biome", "location", "structure"))
            registerTask(ResourceLocation.fromNamespaceAndPath("brnquest", kind), new LocationTaskPresentation(kind));
        registerTask(TaskTypes.ITEM, new BuiltinItemPresentation.ItemChoicePresentation());
        registerTask(TaskTypes.ITEM_CHOICE, new BuiltinItemPresentation.ItemChoicePresentation());


        registerReward(yourscraft.jasdewstarfield.brnquest.builtin.reward.LootTableReward.ID, new ClientRewardPresentation() {
            public String symbol(RewardView reward) { return "▣"; }
            public void refresh(RewardView reward) { RewardTableClientState.refresh(reward.id().toString()); }
            public void prepareClaim(String revision, RewardView reward) { RewardTableChoiceScreen.expect(revision, reward.id().toString()); }
            public Component interactionHint(RewardPresentationContext context) {
                if (!context.claimed() && !context.claimable()) return title(context);
                return RewardTableClientState.hint(context.reward().id().toString(), context.claimed());
            }
            public Component typeName(RewardView reward) { return Component.translatable("screen.brnquest.type.reward.loot_table"); }
            public Component title(RewardPresentationContext context) {
                String title = context.reward().config().getOrDefault("title", "");
                return title.isBlank() ? typeName(context.reward()).copy().append(" · ")
                        .append(context.reward().config().getOrDefault("loot_table", "")) : Component.literal(title);
            }
            // A single loot-table reference has no candidate browser; rules belong to the editor field help.
        });
        registerReward(yourscraft.jasdewstarfield.brnquest.builtin.reward.table.RewardTableReward.ID, new ClientRewardPresentation() {
            public String symbol(RewardView reward) { return "▤"; }
            public void refresh(RewardView reward) { RewardTableClientState.refresh(reward.id().toString()); }
            public void prepareClaim(String revision, RewardView reward) { RewardTableChoiceScreen.expect(revision, reward.id().toString()); }
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
                    return java.util.Optional.of(RewardTablePreview.parse(view).lines(
                            child -> RewardEntryDetails.resolve(net.minecraft.client.Minecraft.getInstance(), child).summary()));
                } catch (RuntimeException error) { return java.util.Optional.of(java.util.List.of(Component.literal("Invalid reward table"))); }
            }
        });
        registerReward(yourscraft.jasdewstarfield.brnquest.builtin.observation.advancement.AdvancementConfig.ID, new AdvancementPresentation.Reward());
        registerReward(RewardTypes.COMMAND, new ClientRewardPresentation() {
            public String symbol(RewardView reward) { return ">_"; }
            public Component title(RewardPresentationContext context) {
                String title = context.reward().config().getOrDefault("title", "");
                return title.isBlank() ? typeName(context.reward()) : Component.literal(title);
            }
            public Component typeName(RewardView reward) {
                return Component.translatable("screen.brnquest.type.reward.command");
            }
        });
        registerReward(RewardTypes.ITEM, new ClientRewardPresentation() {
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


        BuiltinItemEditors.register();
        ClientConfigEditors.register(ResourceLocation.parse("brnquest:reward_table"), "table", RewardTableEditorScreen::new);
    }
    private static void registerTask(ResourceLocation id, ClientTaskPresentation presentation) {
        ClientTaskPresentationRegistry.register(id, presentation, QuestTypeIcons.forType(id));
    }
    private static void registerReward(ResourceLocation id, ClientRewardPresentation presentation) {
        ClientRewardPresentationRegistry.register(id, presentation, QuestTypeIcons.forType(id));
    }
    public static int displayedCount(RewardView reward, int baseCount) {
        int multiplier;
        try {
            multiplier = Math.max(1, Integer.parseInt(reward.config().getOrDefault("count", "1")
                    .replaceAll("[^0-9-]", "")));
        } catch (NumberFormatException ignored) {
            multiplier = 1;
        }
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.max(1, baseCount) * multiplier);
    }
}
