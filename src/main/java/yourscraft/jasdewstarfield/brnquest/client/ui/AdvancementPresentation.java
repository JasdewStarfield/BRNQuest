package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import java.util.Map;

/** Read-only presentation never grants an advancement to obtain a preview. */
public final class AdvancementPresentation {
    private AdvancementPresentation() {}
    /** Only client-synchronized display data is read; hidden or grouped targets use the fallback. */
    static java.util.Optional<yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon> icon(Map<String,String> values) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        var connection = minecraft == null ? null : minecraft.getConnection();
        var stack = displayIcon(values.getOrDefault("advancement", ""), id -> {
            var node = connection == null ? null : connection.getAdvancements().getTree().get(id);
            return node == null ? net.minecraft.world.item.ItemStack.EMPTY
                    : node.holder().value().display().map(display -> display.getIcon()).orElse(net.minecraft.world.item.ItemStack.EMPTY);
        });
        return java.util.Optional.of(yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon.item(stack));
    }
    /** A missing display, unknown ID or group is a visual fallback, never an ingredient. */
    static net.minecraft.world.item.ItemStack displayIcon(String selector,
            java.util.function.Function<net.minecraft.resources.ResourceLocation,net.minecraft.world.item.ItemStack> lookup) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(selector);
        var stack = id == null ? net.minecraft.world.item.ItemStack.EMPTY : lookup.apply(id);
        return stack == null || stack.isEmpty() ? new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.KNOWLEDGE_BOOK)
                : stack.copyWithCount(1);
    }
    private static Component title(Map<String,String> values) {
        String title = values.getOrDefault("title", "");
        if (!title.isBlank()) return Component.literal(title);
        return advancementTitle(values.getOrDefault("advancement", ""));
    }
    /** The objective label states the action; its tooltip identifies the selected advancement. */
    static Component taskTitle(Map<String,String> values) {
        String title = values.getOrDefault("title", "");
        return title.isBlank() ? Component.translatable("screen.brnquest.advancement.task_title") : Component.literal(title);
    }
    private static Component advancementTitle(String selector) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        var connection = minecraft == null ? null : minecraft.getConnection();
        return displayTitle(selector, id -> {
            var node = connection == null ? null : connection.getAdvancements().getTree().get(id);
            return node == null ? null : node.holder().value().display().map(display -> display.getTitle()).orElse(null);
        });
    }
    /** Keep the native component so the current client language and formatting remain authoritative. */
    static Component displayTitle(String selector,
            java.util.function.Function<net.minecraft.resources.ResourceLocation,Component> lookup) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(selector);
        var title = id == null ? null : lookup.apply(id);
        if (title == null || title.getString().isBlank()) return Component.literal(selector);
        if (title.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated
                && translated.getFallback() == null && !net.minecraft.locale.Language.getInstance().has(translated.getKey()))
            return Component.literal(selector);
        return title.copy();
    }
    private static Component detail(Map<String,String> values, boolean task) {
        var detail = Component.translatable(task ? "screen.brnquest.advancement.task_heading"
                : "screen.brnquest.advancement.reward_heading").append("\n").append(title(values));
        if (!values.getOrDefault("title", "").isBlank()) detail.append("\n").append(advancementTitle(values.getOrDefault("advancement", "")));
        String criterion = values.getOrDefault("criterion", "");
        if (!criterion.isBlank()) detail.append("\n").append(Component.translatable("screen.brnquest.advancement.criterion")).append(": " + criterion);
        if (task) detail.append("\n").append(Component.translatable("screen.brnquest.advancement." + values.getOrDefault("mode", "any")));
        // Authoring field help retains the detailed mechanics; gameplay tooltips stay concise.
        return detail;
    }
    static Component rewardHint(Map<String,String> values, boolean claimable, boolean claimed) {
        var hint = detail(values,false).copy();
        if (claimed) hint.append("\n").append(Component.translatable("screen.brnquest.reward.claimed"));
        else if (claimable) hint.append("\n").append(Component.translatable("screen.brnquest.reward.click_to_claim"));
        return hint;
    }
    public static final class Task implements ClientTaskPresentation {
        public NodeStyle nodeStyle(TaskView task) { return NodeStyle.CUSTOM; }
        public java.util.Optional<yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon> icon(TaskView task) { return AdvancementPresentation.icon(task.config()); }
        public Component typeName(TaskView task) { return Component.translatable("screen.brnquest.type.task.advancement"); }
        public Component title(TaskPresentationContext context) { return taskTitle(context.task().config()); }
        public Component interactionHint(TaskPresentationContext context, boolean interactive) { return detail(context.task().config(),true); }
    }
    public static final class Reward implements ClientRewardPresentation {
        public java.util.Optional<yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon> icon(RewardView reward) { return AdvancementPresentation.icon(reward.config()); }
        public Component typeName(RewardView reward) { return Component.translatable("screen.brnquest.type.reward.advancement"); }
        public Component title(RewardPresentationContext context) { return AdvancementPresentation.title(context.reward().config()); }
        public Component interactionHint(RewardPresentationContext context) {
            return rewardHint(context.reward().config(),context.claimable(),context.claimed());
        }
    }
}
