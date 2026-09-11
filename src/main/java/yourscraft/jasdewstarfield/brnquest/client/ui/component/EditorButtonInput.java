package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Focus and feedback adapter for existing hand-written screen button dispatch. */
public final class EditorButtonInput {
    private record Target(UiRect bounds, UiRect visible, boolean enabled) {}
    private final List<Target> targets = new ArrayList<>();
    private UiRect focus;
    private UiRect clip;
    private int offsetX;
    private final Consumer<UiRect> feedback;
    public EditorButtonInput() { this(EditorButtonFeedback::activate); }
    /** Injectable feedback keeps geometry and single-dispatch tests independent of the game audio device. */
    EditorButtonInput(Consumer<UiRect> feedback) { this.feedback = feedback; }
    public void begin() { targets.clear(); clip = null; offsetX = 0; }
    /** Match the owning panel's clipping and horizontal drawer translation for pointer feedback. */
    public void viewport(UiRect clip, int offsetX) { this.clip = clip; this.offsetX = offsetX; }

    public void register(UiRect area, boolean enabled) {
        UiRect screenArea = area.translated(offsetX, 0);
        UiRect visible = clip == null ? screenArea : screenArea.intersection(clip);
        targets.add(new Target(screenArea, visible, enabled && visible.width() > 0 && visible.height() > 0));
    }
    public void clearFocus() { focus = null; }
    public boolean atBoundary(boolean backwards) {
        List<Target> enabled = targets.stream().filter(Target::enabled).toList();
        return focus == null || enabled.isEmpty() || (backwards ? enabled.getFirst() : enabled.getLast()).bounds.equals(focus);
    }

    public boolean render(GuiGraphics g, Font font, UiRect area, EditorButton.Definition definition,
                          boolean enabled, boolean selected, EditorButton.Tone tone, double x, double y) {
        register(area, enabled);
        return EditorButton.renderInteractive(g, font, area, definition, enabled,
                selected || area.equals(focus), tone, x, y);
    }

    public void clicked(double x, double y, int button) {
        if (button != 0) return;
        targets.reversed().stream().filter(t -> t.visible.containsExclusive(x,y)).findFirst().filter(Target::enabled).ifPresent(t -> {
            focus = t.bounds;
            feedback.accept(t.bounds);
        });
    }

    public boolean keyPressed(int key, boolean backwards, Consumer<UiRect> activate) {
        List<Target> enabled = targets.stream().filter(Target::enabled).toList();
        if (enabled.isEmpty()) return false;
        if (key == 258) {
            int current = -1;
            for (int i=0;i<enabled.size();i++) if (enabled.get(i).bounds.equals(focus)) current=i;
            int next = current < 0 ? (backwards ? enabled.size()-1 : 0)
                    : Math.floorMod(current+(backwards ? -1 : 1), enabled.size());
            focus = enabled.get(next).bounds;
            return true;
        }
        if ((key == 257 || key == 335 || key == 32) && enabled.stream().anyMatch(t -> t.bounds.equals(focus))) {
            // The existing mouse route owns feedback and action execution; do not dispatch either twice.
            activate.accept(focus);
            return true;
        }
        return false;
    }
}
