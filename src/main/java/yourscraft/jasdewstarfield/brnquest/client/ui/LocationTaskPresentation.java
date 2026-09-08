package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;

/** Passive exploration titles and hints retain the authored selector, never its expanded member list. */
public record LocationTaskPresentation(String kind) implements ClientTaskPresentation {
    public NodeStyle nodeStyle(TaskView task) { return NodeStyle.CUSTOM; }
    public String symbol(TaskView task) { return switch(kind) { case "dimension" -> "D"; case "biome" -> "B"; case "structure" -> "S"; default -> "P"; }; }
    public Component typeName(TaskView task) { return Component.translatable("screen.brnquest.type.task." + kind); }
    public Component title(TaskPresentationContext context) {
        var config = context.task().config();
        String title = config.getOrDefault("title", "");
        return title.isBlank() ? typeName(context.task()).copy().append(": " + config.getOrDefault(kind.equals("location") ? "position" : kind, "")) : Component.literal(title);
    }
    public Component interactionHint(TaskPresentationContext context, boolean interactive) {
        var config = context.task().config();
        return title(context).copy().append("\n").append(Component.translatable("screen.brnquest.location.hint"))
                .append(kind.equals("location") ? "\n" + config.getOrDefault("dimension", "*") + " | " + config.getOrDefault("size", "1,1,1") : "");
    }
}
