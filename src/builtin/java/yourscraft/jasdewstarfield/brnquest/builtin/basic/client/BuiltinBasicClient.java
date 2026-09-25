package yourscraft.jasdewstarfield.brnquest.builtin.basic.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import yourscraft.jasdewstarfield.brnquest.client.ui.*;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypes;

/** Client-only foundation presentations, loaded by the physical-client bootstrap. */
public final class BuiltinBasicClient {
    private BuiltinBasicClient() {}
    public static void register() {
        registerTask(TaskTypes.CHECKMARK, new CheckmarkPresentation());
        registerTask(TaskTypes.XP, new ClientTaskPresentation() {
            public NodeStyle nodeStyle(TaskView task) { return NodeStyle.CUSTOM; }
            public String symbol(TaskView task) { return "✦"; }
            public Component typeName(TaskView task) { return Component.translatable("screen.brnquest.type.task.xp"); }
            public boolean interactive(TaskView task) { return true; }
            public boolean satisfied(TaskPresentationContext context) { return context.storedProgress() >= 1; }
            public boolean readyForSubmission(TaskPresentationContext context) {
                return context.minecraft().player != null && experienceAvailable(context.task(),
                        context.minecraft().player.totalExperience, context.minecraft().player.experienceLevel);
            }
            public Component title(TaskPresentationContext context) {
                boolean points = usesRawExperiencePoints(context.task());
                return Component.translatable(points ? "screen.brnquest.task.xp.points" : "screen.brnquest.task.xp.levels",
                        context.task().config().getOrDefault("value", "1"));
            }
        });
        registerTask(TaskTypes.CUSTOM, new ClientTaskPresentation() {
            public NodeStyle nodeStyle(TaskView task) { return NodeStyle.CUSTOM; }
            public String symbol(TaskView task) { return "◆"; }
            public Component typeName(TaskView task) {
                return Component.translatable("screen.brnquest.type.task.custom");
            }
        });
        registerReward(RewardTypes.CUSTOM, new ClientRewardPresentation() {
            public String symbol(RewardView reward) { return "◆"; }
            public Component typeName(RewardView reward) {
                return Component.translatable("screen.brnquest.type.reward.custom");
            }
        });
        registerReward(RewardTypes.XP, experience(false));
        registerReward(RewardTypes.XP_LEVELS, experience(true));
    }
    /** Keep the existing resource-pack sprite paths while ownership moves into this plugin. */
    private static EditorIcon icon(ResourceLocation id) {
        return EditorIcon.sprite(ResourceLocation.fromNamespaceAndPath("brnquest", "editor/type/" + id.getPath()));
    }
    private static void registerTask(ResourceLocation id, ClientTaskPresentation presentation) {
        ClientTaskPresentationRegistry.register(id, presentation, icon(id));
    }
    private static void registerReward(ResourceLocation id, ClientRewardPresentation presentation) {
        ClientRewardPresentationRegistry.register(id, presentation, icon(id));
    }
    private static final class CheckmarkPresentation implements ClientTaskPresentation {
        public NodeStyle nodeStyle(TaskView task) { return NodeStyle.CHECKMARK; }
        public String symbol(TaskView task) { return "✓"; }
        public Component typeName(TaskView task) {
            return Component.translatable("screen.brnquest.type.task.checkmark");
        }
        public boolean interactive(TaskView task) { return true; }
        public boolean acceptsQuestCompletionIntent(TaskView task) { return true; }
        public Component progressText(TaskPresentationContext context, boolean satisfied) {
            return Component.translatable(satisfied ? "screen.brnquest.task.checked" : "screen.brnquest.task.manual");
        }
        public Component title(TaskPresentationContext context) {
            String configured = context.task().config().getOrDefault("title", "");
            return configured.isBlank() ? Component.translatable("screen.brnquest.task.checkmark") : Component.literal(configured);
        }
    }

    static boolean experienceAvailable(TaskView task, int totalExperience, int experienceLevel) {
        int required;
        try {
            required = Integer.parseInt(task.config().getOrDefault("value", "1").replaceAll("[^0-9-]", ""));
        } catch (NumberFormatException ignored) {
            return false;
        }
        if (required < 1) return false;
        return usesRawExperiencePoints(task) ? totalExperience >= required : experienceLevel >= required;
    }

    private static boolean usesRawExperiencePoints(TaskView task) {
        String value = task.config().getOrDefault("points", "true");
        return "true".equalsIgnoreCase(value) || "1b".equalsIgnoreCase(value);
    }

    private static ClientRewardPresentation experience(boolean levels) {
        return new ClientRewardPresentation() {
            public String symbol(RewardView reward) { return "✦"; }
            public java.util.Optional<Component> contentSummary(RewardView reward) {
                // XP units and config keys belong to this type, not the shared reward layout.
                String key = levels ? "xp_levels" : "xp";
                boolean showLevels = levels || !Boolean.parseBoolean(reward.config().getOrDefault("points", "true"));
                String unitKey = showLevels ? "screen.brnquest.type.reward.xp_levels" : "screen.brnquest.type.reward.xp_points";
                return java.util.Optional.of(Component.translatable(unitKey)
                        .append(" × " + reward.config().getOrDefault(key, "0")));
            }
            public Component typeName(RewardView reward) {
                return Component.translatable(levels ? "screen.brnquest.type.reward.xp_levels" : "screen.brnquest.type.reward.xp");
            }
        };
    }
}
