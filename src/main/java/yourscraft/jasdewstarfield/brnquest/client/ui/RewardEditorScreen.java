package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.*;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;
import java.util.List;

/** Shared child-panel chrome; the quest screen continues to own the authoring lease. */
abstract class RewardEditorScreen extends Screen {
    protected final Screen parent;
    protected final EditorActionGroup<String> controls = new EditorActionGroup<>();
    protected String issue = "";
    private long lastFrame;

    RewardEditorScreen(Screen parent, Component title) {
        super(title);
        this.parent = parent;
    }

    protected UiRect panel() {
        int w = Math.min(620, width - 24), h = Math.min(460, height - 24);
        int x = (width - w) / 2, y = (height - h) / 2;
        return new UiRect(x, y, x + w, y + h);
    }

    protected UiRect body() {
        var p = panel();
        return new UiRect(p.left() + 12, p.top() + 32, p.right() - 18, p.bottom() - 60);
    }

    protected double frameSeconds() {
        long now = System.nanoTime();
        double seconds = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1_000_000_000.0);
        lastFrame = now;
        return seconds;
    }

    // Respect the same scroll preferences as the parent editor and its other selectors.
    protected double scrollSpeed() { return BrnQuestClientConfig.VALUES.smoothSpeed.get(); }
    protected double scrollStep() { return BrnQuestClientConfig.VALUES.scrollStep.get(); }

    protected EditorActionGroup.Placed<String> button(String id, Component label, UiRect bounds,
                                                      boolean enabled, EditorButton.Tone tone, Runnable action) {
        return new EditorActionGroup.Placed<>(new EditorActionGroup.Action<>(id,
                EditorButton.Definition.text(label, null), enabled, tone, (x, y) -> action.run()), bounds, panel());
    }

    protected List<EditorActionGroup.Placed<String>> footer(boolean enabled, Runnable apply) {
        var p = panel();
        int middle = p.centerX();
        return List.of(button("cancel", Component.translatable("gui.cancel"),
                        new UiRect(p.left() + 12, p.bottom() - 30, middle - 3, p.bottom() - 10),
                        true, EditorButton.Tone.NEUTRAL, this::onClose),
                button("apply", Component.translatable("gui.done"),
                        new UiRect(middle + 3, p.bottom() - 30, p.right() - 12, p.bottom() - 10),
                        enabled, EditorButton.Tone.PRIMARY, apply));
    }

    protected void renderPanel(GuiGraphics graphics, int x, int y, float partial) {
        ChildScreenBackground.render(parent, graphics, width, height, partial);
        super.renderBackground(graphics, x, y, partial);
        graphics.fill(0, 0, width, height, 0x70151820);
        var p = panel();
        GraystoneSurface.raised(graphics, p, 0xFF30332E, true);
        graphics.drawCenteredString(font, Component.literal(font.plainSubstrByWidth(title.getString(), p.width() - 24)),
                p.centerX(), p.top() + 10, -1);
        graphics.drawString(font, font.plainSubstrByWidth(issue, p.width() - 24),
                p.left() + 12, p.bottom() - 44, 0xFFFF7070, false);
    }

    @Override protected void init() { controls.clear(); lastFrame = 0; }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 258) {
            boolean backwards = (modifiers & 1) != 0;
            if (children().isEmpty()) return controls.focusNext(backwards);
            if (controls.focusedKey().isPresent() && getFocused() == null) {
                if (!controls.atFocusBoundary(backwards)) return controls.focusNext(backwards);
                controls.clearFocus();
                return super.keyPressed(key, scan, modifiers);
            }
            // Insert the custom action group when native focus wraps past the last/first field.
            var previous = getFocused();
            boolean handled = super.keyPressed(key, scan, modifiers);
            int before = children().indexOf(previous), after = children().indexOf(getFocused());
            if (before >= 0 && after >= 0 && (backwards ? after >= before : after <= before)
                    && controls.focusNext(backwards)) { setFocused(null); return true; }
            return handled;
        }
        if ((key == 257 || key == 335 || key == 32) && getFocused() == null && controls.activateFocused()) return true;
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) {}
    @Override public void tick() { parent.tick(); }
    @Override public boolean isPauseScreen() { return parent.isPauseScreen(); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean mouseClicked(double x, double y, int button) {
        return controls.mouseClicked(x, y, button) || super.mouseClicked(x, y, button);
    }
}
