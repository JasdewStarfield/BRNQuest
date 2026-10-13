package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;

/** Native Tab stop connects the custom toolbar to text fields, with arrow navigation inside the toolbar. */
public final class EditorControlNavigation extends AbstractWidget {
    private final IntPredicate input;
    private final BooleanSupplier boundary;
    private final Runnable enter, leave;
    public EditorControlNavigation(Component title, UiRect bounds, IntPredicate input,
                                   BooleanSupplier boundary, Runnable enter, Runnable leave) {
        super(bounds.left(),bounds.top(),bounds.width(),bounds.height(),title);
        this.input=input; this.boundary=boundary; this.enter=enter; this.leave=leave;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if (!isFocused() || !active) return false;
        if (key==258 && boundary.getAsBoolean()) return false;
        return input.test(key);
    }
    @Override public void setFocused(boolean focused) {
        boolean entering=focused && !isFocused();
        super.setFocused(focused);
        if (entering) enter.run();
        else if (!focused) leave.run();
    }
    @Override public boolean mouseClicked(double x,double y,int button) { return false; }
    @Override protected void renderWidget(GuiGraphics graphics,int x,int y,float partial) {}
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { output.add(NarratedElementType.TITLE,getMessage()); }
}
