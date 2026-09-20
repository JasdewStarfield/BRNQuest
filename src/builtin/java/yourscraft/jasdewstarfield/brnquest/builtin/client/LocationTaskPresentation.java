package yourscraft.jasdewstarfield.brnquest.builtin.client;
import yourscraft.jasdewstarfield.brnquest.client.ui.*;

import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;

/** Passive exploration resolves selector members for read-only browsing and bounded tooltip previews. */
public record LocationTaskPresentation(String kind) implements ClientTaskPresentation {
    public NodeStyle nodeStyle(TaskView task) { return NodeStyle.CUSTOM; }
    public String symbol(TaskView task) { return switch(kind) { case "dimension" -> "D"; case "biome" -> "B"; case "structure" -> "S"; default -> "P"; }; }
    public Component typeName(TaskView task) { return Component.translatable("screen.brnquest.type.task." + kind); }
    public Component title(TaskPresentationContext context) {
        var config = context.task().config();
        String title = config.getOrDefault("title", "");
        return title.isBlank() ? typeName(context.task()).copy().append(": " + config.getOrDefault(kind.equals("location") ? "position" : kind, "")) : Component.literal(title);
    }
    public java.util.Optional<java.util.List<Component>> resolvedOptions(TaskView task) {
        String registry=kind.equals("location") ? "dimension" : kind;
        if(kind.equals("location") && Boolean.parseBoolean(task.config().getOrDefault("ignore_dimension","false"))) return java.util.Optional.empty();
        String selector=task.config().getOrDefault(registry,"");
        var ids=selector.startsWith("#") ? LocationOptionsCache.members(registry,selector) : java.util.List.of(selector);
        return java.util.Optional.of(ids.stream().map(id->ResolvedOptions.entry(id,displayName(registry,id))).toList());
    }
    private static Component displayName(String kind,String id) {
        var parsed=net.minecraft.resources.ResourceLocation.tryParse(id);
        String key=parsed==null ? "" : kind+"."+parsed.getNamespace()+"."+parsed.getPath().replace('/','.');
        return net.minecraft.locale.Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(id);
    }
    public Component interactionHint(TaskPresentationContext context, boolean interactive) {
        var config = context.task().config();
        var text=typeName(context.task()).copy();
        resolvedOptions(context.task()).ifPresent(members->ResolvedOptions.appendPreview(text,members));
        if(kind.equals("location")) text.append("\n"+config.getOrDefault("position","")+" | "+config.getOrDefault("size","1,1,1"));
        return text;

    }
}
