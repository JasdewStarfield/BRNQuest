package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** One native Tab stop for a virtualized list; row drawing and pointer input stay with its owner. */
public final class EditorListNavigationWidget<K> extends AbstractWidget {
    private final EditorListPanel<K> list;
    private final Consumer<EditorListPanel.Row<K>> activate;

    public EditorListNavigationWidget(Component title, EditorListPanel<K> list,
                                     Consumer<EditorListPanel.Row<K>> activate) {
        super(0, 0, 1, 1, title);
        this.list = list; this.activate = activate;
    }

    public void update(EditorListPanel.Frame<K> frame) {
        UiRect area = frame.viewport();
        setX(area.left()); setY(area.top()); setWidth(area.width()); setHeight(area.height());
        active = area.width() > 0 && area.height() > 0 && frame.contentHeight() > 0;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!isFocused() || !active || key == 258) return false; // Tab exits to the next native control.
        if (list.navigate(key, (modifiers & 1) != 0)) return true;
        if (key == 257 || key == 335 || key == 32) {
            list.focusedRow().ifPresent(activate);
            return true;
        }
        return false;
    }

    @Override public void setFocused(boolean focused) {
        boolean entering = focused && !isFocused();
        super.setFocused(focused);
        if (!focused) list.clearFocus();
        else if (entering) list.navigate(264, false);
    }

    @Override public boolean mouseClicked(double x, double y, int button) { return false; }
    @Override protected void renderWidget(GuiGraphics graphics, int x, int y, float partial) {}
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
