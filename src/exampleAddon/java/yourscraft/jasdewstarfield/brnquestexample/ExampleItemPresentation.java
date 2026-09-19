package yourscraft.jasdewstarfield.brnquestexample;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.client.ui.ClientTaskPresentation;
import yourscraft.jasdewstarfield.brnquest.client.ui.TaskPresentationContext;
import yourscraft.jasdewstarfield.brnquest.client.ui.TaskSubmissionInteraction;

import java.util.Optional;

/** Independently compiled presentation whose page is opened through the public capability factory. */
final class ExampleItemPresentation implements ClientTaskPresentation {
    public NodeStyle nodeStyle(TaskView task) { return NodeStyle.ITEM; }
    public Component typeName(TaskView task) { return Component.translatable("screen.brnquest_example.task.item"); }
    public boolean interactive(TaskView task) { return !ExampleItemTask.crafting(task.config()); }
    public boolean confirmed(TaskView task, long progress) {
        return progress >= (ExampleItemTask.crafting(task.config()) ? ExampleItemTask.count(task.config()) : 1);
    }
    public boolean satisfied(TaskPresentationContext context) { return confirmed(context.task(), context.storedProgress()); }
    public ItemStack displayedItem(TaskPresentationContext context) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ExampleItemTask.itemId(context.task().config())));
    }
    public Component title(TaskPresentationContext context) {
        return Component.translatable("screen.brnquest_example.task.item.title",
                ExampleItemTask.count(context.task().config()), displayedItem(context).getHoverName());
    }
    private int available(TaskPresentationContext context) {
        if (context.minecraft().player == null) return 0;
        return context.minecraft().player.getInventory().items.stream()
                .filter(stack -> ExampleItemTask.matches(context.task().config(), stack)).mapToInt(ItemStack::getCount).sum();
    }
    public boolean readyForSubmission(TaskPresentationContext context) {
        return interactive(context.task()) && available(context) >= ExampleItemTask.count(context.task().config());
    }
    public Component progressText(TaskPresentationContext context, boolean satisfied) {
        // A committed consuming objective keeps its completed count after the inventory shrinks.
        long current = satisfied ? ExampleItemTask.count(context.task().config())
                : ExampleItemTask.crafting(context.task().config()) ? context.storedProgress() : available(context);
        return Component.literal(Math.min(current, ExampleItemTask.count(context.task().config())) + " / " + ExampleItemTask.count(context.task().config()));
    }
    public Optional<TaskSubmissionInteraction> submissionInteraction(TaskPresentationContext context) {
        if (!ExampleItemTask.consuming(context.task().config()) || !readyForSubmission(context)) return Optional.empty();
        return Optional.of((parent, submit) -> new ExampleItemSubmissionScreen(parent, context.task().config(), submit));
    }
}
