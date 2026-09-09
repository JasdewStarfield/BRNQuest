package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import java.util.Map;

/** Read-only presentation never grants an advancement to obtain a preview. */
public final class AdvancementPresentation {
    private AdvancementPresentation() {}
    private static final String ICON = "{id:\"minecraft:knowledge_book\",count:1}";
    private static Component title(Map<String,String> values) {
        String title = values.getOrDefault("title", "");
        return Component.literal(title.isBlank() ? values.getOrDefault("advancement", "") : title);
    }
    private static Component detail(Map<String,String> values, boolean task) {
        var detail = title(values).copy();
        String criterion = values.getOrDefault("criterion", "");
        if (!criterion.isBlank()) detail.append("\n").append(Component.translatable("screen.brnquest.advancement.criterion")).append(": " + criterion);
        if (task) detail.append("\n").append(Component.translatable("screen.brnquest.advancement." + values.getOrDefault("mode", "any")));
        return detail.append("\n").append(Component.translatable(task ? "screen.brnquest.advancement.task_hint" : "screen.brnquest.advancement.reward_hint"));
    }
    public static final class Task implements ClientTaskPresentation {
        public NodeStyle nodeStyle(TaskView task) { return NodeStyle.ITEM; }
        public String itemSnbt(TaskView task) { return ICON; }
        public Component typeName(TaskView task) { return Component.translatable("screen.brnquest.type.task.advancement"); }
        public Component title(TaskPresentationContext context) { return AdvancementPresentation.title(context.task().config()); }
        public Component interactionHint(TaskPresentationContext context, boolean interactive) { return detail(context.task().config(),true); }
    }
    public static final class Reward implements ClientRewardPresentation {
        public String itemSnbt(RewardView reward) { return ICON; }
        public Component typeName(RewardView reward) { return Component.translatable("screen.brnquest.type.reward.advancement"); }
        public Component title(RewardPresentationContext context) { return AdvancementPresentation.title(context.reward().config()); }
        public Component interactionHint(RewardPresentationContext context) {
            return detail(context.reward().config(),false).copy().append("\n").append(ClientRewardPresentation.super.interactionHint(context));
        }
    }
}
