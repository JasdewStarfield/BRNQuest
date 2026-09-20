package yourscraft.jasdewstarfield.brnquest.builtin.client;
import yourscraft.jasdewstarfield.brnquest.client.ui.*;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.builtin.observation.encounter.EncounterConfig;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;
import java.util.Optional;

/** Decorative icons and transient gaze text never expose an ingredient or grant progress. */
public record EncounterPresentation(boolean observe) implements ClientTaskPresentation {
    public NodeStyle nodeStyle(TaskView task) { return NodeStyle.CUSTOM; }
    public Optional<EditorIcon> icon(TaskView task) { return Optional.of(EditorIcon.item(new ItemStack(observe ? Items.SPYGLASS : Items.IRON_SWORD))); }
    public Component typeName(TaskView task) { return Component.translatable("screen.brnquest.type.task."+(observe ? "observe" : "kill_entity")); }
    public Component title(TaskPresentationContext context) {
        String title=context.task().config().getOrDefault("title","");
        return title.isBlank() ? typeName(context.task()).copy().append(": ").append(targetName(EncounterConfig.parse(context.task().config(),observe))) : Component.literal(title);
    }
    /** Built-in names retain translation components; tag selectors keep their authored identity. */
    public static Component targetName(EncounterConfig config) {
        var id=net.minecraft.resources.ResourceLocation.tryParse(config.selector());
        if(id!=null) {
            if(config.kind().equals("block")) return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(id).map(block->(Component)block.getName()).orElse(Component.literal(config.selector()));
            return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(type->(Component)type.getDescription()).orElse(Component.literal(config.selector()));
        }
        return Component.literal(config.selector());
    }
    /** A kill counter is a receipt only when the configured total has been reached. */
    public boolean confirmed(TaskView task,long storedProgress) {
        return storedProgress >= (observe ? 1 : EncounterConfig.parse(task.config(),false).count());
    }
    public Optional<java.util.List<Component>> resolvedOptions(TaskView task) {
        var config=EncounterConfig.parse(task.config(),observe);
        return Optional.of(config.kind().equals("block")
                ? members(net.minecraft.core.registries.BuiltInRegistries.BLOCK,config,block->block.getName())
                : members(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE,config,type->type.getDescription()));
    }
    private static <T> java.util.List<Component> members(net.minecraft.core.Registry<T> registry,EncounterConfig config,java.util.function.Function<T,Component> name) {
        if(!config.selector().startsWith("#")) return java.util.List.of(ResolvedOptions.entry(config.selector(),targetName(config)));
        var id=net.minecraft.resources.ResourceLocation.parse(config.selector().substring(1));
        return registry.getTag(net.minecraft.tags.TagKey.create(registry.key(),id))
                .map(set->set.stream().sorted(java.util.Comparator.comparing(holder->registry.getKey(holder.value()).toString())).map(holder->ResolvedOptions.entry(registry.getKey(holder.value()).toString(),name.apply(holder.value()))).toList()).orElse(java.util.List.of());
    }
    public boolean satisfied(TaskPresentationContext context) {
        return context.questStatus()==QuestStatus.COMPLETED || context.questStatus()==QuestStatus.REWARD_CLAIMED
                || confirmed(context.task(),context.storedProgress());
    }
    public Component progressText(TaskPresentationContext context,boolean satisfied) {
        var config=EncounterConfig.parse(context.task().config(),observe);
        long required=observe ? Math.max(1,config.duration()) : config.count();
        long current=satisfied ? required : observe ? ObservationDisplay.ticks(context.task()) : context.storedProgress();
        return Component.literal(Math.min(current,required)+" / "+required).append(observe && config.duration()>0 ? " tick" : "");
    }
    public Component interactionHint(TaskPresentationContext context,boolean interactive) {
        var config=EncounterConfig.parse(context.task().config(),observe);
        var text=Component.translatable("screen.brnquest.encounter."+(observe ? "observe_heading" : "kill_heading"));
        ResolvedOptions.appendPreview(text,resolvedOptions(context.task()).orElse(java.util.List.of()));
        if(observe) text.append("\n").append(Component.translatable("screen.brnquest.encounter.gaze",config.distance(),config.duration()));
        return text;
    }
}
