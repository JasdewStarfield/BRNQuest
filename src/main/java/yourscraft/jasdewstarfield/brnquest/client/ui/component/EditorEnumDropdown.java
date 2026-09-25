package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** A small foreground choice menu shared by editor screens with staged enum values. */
public final class EditorEnumDropdown {
    private UiRect anchor;
    private List<String> values = List.of();
    private Function<String, Component> labels = Component::literal;
    private Consumer<String> selection;

    public boolean open() { return anchor != null; }
    public boolean openAt(UiRect bounds) { return anchor != null && anchor.equals(bounds); }

    public void show(UiRect bounds, List<String> choices, Function<String, Component> label,
                     Consumer<String> onSelect) {
        if (bounds == null || choices.isEmpty()) return;
        anchor = bounds;
        values = List.copyOf(choices);
        labels = label;
        selection = onSelect;
    }

    public void close() {
        anchor = null;
        values = List.of();
        selection = null;
    }

    public void render(GuiGraphics graphics, Font font, int screenWidth, int contentTop, int contentBottom,
                       int mouseX, int mouseY) {
        if (!open()) return;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        EditorPopupMenu.render(graphics, font, layout(screenWidth, contentTop, contentBottom), entries(), mouseX, mouseY);
        graphics.pose().popPose();
    }

    /** An open menu consumes even an outside click so the covered control cannot activate. */
    public boolean click(double mouseX, double mouseY, int button, int screenWidth, int contentTop, int contentBottom) {
        if (!open()) return false;
        String action = button == 0 ? EditorPopupMenu.actionAt(
                layout(screenWidth, contentTop, contentBottom), entries(), mouseX, mouseY) : "";
        if (action.startsWith("ENUM_")) {
            int index = Integer.parseInt(action.substring("ENUM_".length()));
            if (index < values.size() && selection != null) selection.accept(values.get(index));
        }
        close();
        return true;
    }

    private List<EditorPopupMenu.Entry> entries() {
        List<EditorPopupMenu.Entry> entries = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            entries.add(new EditorPopupMenu.Entry("ENUM_" + index, labels.apply(values.get(index)), false, true, List.of()));
        }
        return entries;
    }

    private EditorPopupMenu.CascadeLayout layout(int screenWidth, int contentTop, int contentBottom) {
        List<EditorPopupMenu.Entry> entries = entries();
        int menuWidth = Math.max(100, anchor.width());
        UiRect root = EditorPopupMenu.layout(anchor.left(), anchor.bottom() + 1, screenWidth,
                contentTop, contentBottom, menuWidth, entries.size());
        return EditorPopupMenu.cascadeLayout(root, entries, -1, screenWidth, contentTop, contentBottom, menuWidth);
    }
}
