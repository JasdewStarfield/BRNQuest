package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import java.util.List;

/** One preview limit shared by type tooltips; the full browser receives the unabridged list. */
final class ResolvedOptions {
    private ResolvedOptions() {}
    /** An untranslated key is not a useful name; preserve the registry ID in that case. */
    static Component nameOrId(String id, Component name) {
        if (name == null || name.getString().isBlank()) return Component.literal(id);
        if (name.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated
                && translated.getFallback() == null && !net.minecraft.locale.Language.getInstance().has(translated.getKey())) return Component.literal(id);
        return name.copy();
    }
    /** Keep identity in the component's standard hover metadata while its visible text remains the name. */
    static Component entry(String id, Component name) {
        return nameOrId(id,name).copy().withStyle(style -> style.withHoverEvent(
                new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,Component.literal(id))));
    }
    static Component hoverText(Component entry) {
        var hover=entry.getStyle().getHoverEvent();
        var text=hover==null ? null : hover.getValue(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT);
        return text==null ? entry : text;
    }
    static void appendPreview(MutableComponent text, List<Component> members) {
        for (var member : members.stream().limit(3).toList()) text.append("\n- ").append(member);
        if (members.size() > 3) text.append("\n…");
        if (members.isEmpty()) text.append("\n").append(Component.translatable("screen.brnquest.options.empty"));
    }
}
