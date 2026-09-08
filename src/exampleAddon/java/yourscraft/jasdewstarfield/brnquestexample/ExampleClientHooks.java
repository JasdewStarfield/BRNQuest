package yourscraft.jasdewstarfield.brnquestexample;

import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.client.ui.ClientRewardPresentation;
import yourscraft.jasdewstarfield.brnquest.client.ui.ClientRewardPresentationRegistry;
import yourscraft.jasdewstarfield.brnquest.client.ui.ClientTaskPresentation;
import yourscraft.jasdewstarfield.brnquest.client.ui.ClientTaskPresentationRegistry;
import yourscraft.jasdewstarfield.brnquest.client.ui.RewardPresentationContext;
import yourscraft.jasdewstarfield.brnquest.client.ui.TaskPresentationContext;

/** Client-only presentation half of the example extension. */
final class ExampleClientHooks {
    private ExampleClientHooks() {}

    static void register() {
        ClientRewardPresentationRegistry.register(BrnQuestExampleAddon.GUARDED_TAG_REWARD, new ClientRewardPresentation() {
            public String symbol(RewardView reward) { return "G"; }
            public Component typeName(RewardView reward) { return Component.translatable("screen.brnquest_example.reward.guarded_tag"); }
            public Component title(RewardPresentationContext context) { return typeName(context.reward()); }
        });
        ClientTaskPresentationRegistry.register(BrnQuestExampleAddon.MARKER_TASK, new MarkerPresentation());
        ClientTaskPresentationRegistry.register(BrnQuestExampleAddon.SIGNAL_TASK, new SignalPresentation());
        ClientRewardPresentationRegistry.register(BrnQuestExampleAddon.EXPERIENCE_REWARD,
                new ExperiencePresentation());
    }

    private static final class MarkerPresentation implements ClientTaskPresentation {
        public NodeStyle nodeStyle(TaskView task) { return NodeStyle.CUSTOM; }
        public String symbol(TaskView task) { return "M"; }
        public Component typeName(TaskView task) {
            return Component.translatable("screen.brnquest_example.task.marker");
        }
        public Component title(TaskPresentationContext context) {
            return Component.literal("Receive marker "
                    + context.task().config().getOrDefault("tag", "brnquest_example_ready"));
        }
    }

    private static final class SignalPresentation implements ClientTaskPresentation {
        public NodeStyle nodeStyle(TaskView task) { return NodeStyle.CHECKMARK; }
        public String symbol(TaskView task) { return "S"; }
        public Component typeName(TaskView task) {
            return Component.translatable("screen.brnquest_example.task.signal");
        }
        public boolean interactive(TaskView task) { return true; }
        public Component title(TaskPresentationContext context) {
            return Component.literal(context.task().config().getOrDefault("title", "Send signal"));
        }
    }

    private static final class ExperiencePresentation implements ClientRewardPresentation {
        public String symbol(RewardView reward) { return "✦"; }
        public Component typeName(RewardView reward) {
            return Component.translatable("screen.brnquest_example.reward.experience");
        }
        public Component title(RewardPresentationContext context) {
            return Component.translatable("screen.brnquest_example.reward.experience.title",
                    context.reward().config().getOrDefault("amount", "3"));
        }
    }
}
