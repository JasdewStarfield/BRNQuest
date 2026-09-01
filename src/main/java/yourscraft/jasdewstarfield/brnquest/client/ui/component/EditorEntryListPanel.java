package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

/** Ready-to-compose icon/list/action panel, independent of task/reward definitions and networking. */
public final class EditorEntryListPanel<K, A> {
    public record Content(EditorEntryRow.Content row, ItemStack lookupItem) {}
    public record Hover(RecipeLookupTarget item, List<Component> tooltip) {}
    private final EditorListPanel<K> list = new EditorListPanel<>();
    private final EditorActionGroup<A> actions = new EditorActionGroup<>();

    public EditorListPanel<K> list() { return list; }
    public EditorActionGroup<A> actions() { return actions; }
    public void invalidate() { list.invalidate(); actions.clear(); }
    public void reset() { list.reset(); actions.clear(); }

    public Hover render(GuiGraphics graphics, Font font, UiRect bounds, UiRect clip, int trackX,
                        int rowPitch, int count, IntFunction<K> keyAt, double seconds, double speed,
                        Function<EditorListPanel.Row<K>, Content> contentAt,
                        Function<K, List<EditorActionGroup.Action<A>>> actionsAt,
                        List<EditorActionGroup.Placed<A>> footer, Component empty,
                        int mouseX, int mouseY) {
        var frame = list.advance(bounds, clip, trackX, rowPitch, 2, count, keyAt, seconds, speed);
        List<EditorActionGroup.Placed<A>> placed = new ArrayList<>();
        RecipeLookupTarget[] hovered = {null};
        list.render(graphics, row -> {
            List<EditorActionGroup.Action<A>> rowActions = actionsAt.apply(row.key());
            EditorEntryRow.Layout layout = EditorEntryRow.layout(row.bounds(), rowActions.size(), 18, 2);
            Content content = contentAt.apply(row);
            EditorEntryRow.render(graphics, font, layout, content.row());
            RecipeLookupTarget.clipped(content.lookupItem(), layout.icon(), row.visible())
                    .filter(target -> target.contains(mouseX, mouseY)).ifPresent(target -> hovered[0] = target);
            placed.addAll(EditorActionGroup.trailing(layout.actions(), row.visible(), 18, 2, rowActions));
        }, () -> graphics.drawCenteredString(font, empty, bounds.centerX(), frame.viewport().top() + 8, 0xFF9AA6B5));
        placed.addAll(footer);
        actions.setActions(placed);
        return new Hover(hovered[0], actions.render(graphics, font, mouseX, mouseY));
    }
}
