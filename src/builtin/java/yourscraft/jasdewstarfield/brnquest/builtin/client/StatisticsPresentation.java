package yourscraft.jasdewstarfield.brnquest.builtin.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.builtin.observation.statistics.*;
import yourscraft.jasdewstarfield.brnquest.client.ui.*;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.*;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;
import java.util.Optional;

/** Detail rows, tracking HUD and completion plaques share the native statistic name and units. */
public final class StatisticsPresentation implements ClientTaskPresentation {
    public NodeStyle nodeStyle(TaskView task) { return NodeStyle.CUSTOM; }
    public Component typeName(TaskView task) { return Component.translatable("screen.brnquest.type.task.statistics"); }
    public Optional<EditorIcon> icon(TaskView task) {
        return StatisticsTargets.resolve(StatisticsConfig.parse(task.config())).map(stat -> {
            Object value = stat.getValue();
            if (value instanceof Block block && block.asItem() != Items.AIR) return EditorIcon.item(new ItemStack(block));
            if (value instanceof Item item && item != Items.AIR) return EditorIcon.item(new ItemStack(item));
            if (value instanceof EntityType<?> entity) {
                SpawnEggItem egg = SpawnEggItem.byId(entity);
                if (egg != null) return EditorIcon.item(new ItemStack(egg));
            }
            return QuestTypeIcons.forType(StatisticsConfig.ID);
        });
    }
    public Component title(TaskPresentationContext context) {
        String title = context.task().config().getOrDefault("title", "");
        var config = StatisticsConfig.parse(context.task().config());
        return title.isBlank() ? StatisticsTargets.categoryName(config).copy().append(": ")
                .append(StatisticsTargets.targetName(config)) : NativeText.component(title);
    }
    public boolean confirmed(TaskView task, long storedProgress) {
        var config = StatisticsConfig.parse(task.config());
        return StatisticsTargets.resolve(config).isPresent() && storedProgress >= config.count();
    }
    public boolean satisfied(TaskPresentationContext context) {
        // Completed quest history follows the same receipt semantics as the other built-in views.
        return context.questStatus() == QuestStatus.COMPLETED || context.questStatus() == QuestStatus.REWARD_CLAIMED
                || confirmed(context.task(), context.storedProgress());
    }
    public Component progressText(TaskPresentationContext context, boolean satisfied) {
        var config = StatisticsConfig.parse(context.task().config());
        long current = satisfied ? config.count() : Math.min(context.storedProgress(), config.count());
        return Component.literal(StatisticsTargets.format(config, current) + " / " + StatisticsTargets.format(config, config.count()));
    }
    public Component completionDescription(TaskPresentationContext context) {
        var config = StatisticsConfig.parse(context.task().config());
        // An authored short title never hides the concrete requirement in a completion notification.
        return Component.translatable("screen.brnquest.statistics.requirement", StatisticsTargets.categoryName(config),
                StatisticsTargets.targetName(config), StatisticsTargets.format(config, config.count()));
    }
    public Component interactionHint(TaskPresentationContext context, boolean interactive) {
        var config = StatisticsConfig.parse(context.task().config());
        var result = completionDescription(context).copy().append("\n" + config.statType() + " / " + config.stat());
        if (StatisticsTargets.resolve(config).isEmpty()) result.append("\n").append(Component.translatable("screen.brnquest.statistics.missing"));
        return result.append("\n").append(Component.translatable("screen.brnquest.statistics.history_help"));
    }
}
