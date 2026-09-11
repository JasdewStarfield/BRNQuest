package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/** Ordered buttons that share presentation, clipped pointer input, keyboard focus and callbacks. */
public final class EditorActionGroup<K> {
    public record Action<K>(K key, EditorButton.Definition definition, boolean enabled,
                            EditorButton.Tone tone, BiConsumer<Double, Double> onPress) {
        public Action {
            Objects.requireNonNull(key);
            Objects.requireNonNull(definition);
            Objects.requireNonNull(tone);
            Objects.requireNonNull(onPress);
        }
    }

    public record Placed<K>(Action<K> action, UiRect bounds, UiRect clip) {
        public UiRect visible() { return bounds.intersection(clip); }
    }

    private List<Placed<K>> actions = List.of();
    private K focused;

    /** Stable action keys survive row reorder; hidden/disabled actions do not become ghost tab stops. */
    public void setActions(List<Placed<K>> actions) {
        if (actions.stream().map(p -> p.action().key()).distinct().count() != actions.size()) {
            throw new IllegalArgumentException("Action keys must be unique within the group");
        }
        this.actions = List.copyOf(actions);
        if (focusable().stream().noneMatch(p -> p.action().key().equals(focused))) focused = null;
    }

    /** Reusable right-aligned compact toolbar; callers choose icon-only, text or mixed definitions. */
    public static <K> List<Placed<K>> trailing(UiRect area, UiRect clip, int buttonWidth, int gap,
                                              List<Action<K>> actions) {
        if (buttonWidth <= 0 || gap < 0) throw new IllegalArgumentException("Invalid action metrics");
        int left = Math.max(area.left(), area.right() - actions.size() * (buttonWidth + gap) + gap);
        List<Placed<K>> result = new ArrayList<>();
        for (Action<K> action : actions) {
            UiRect bounds = new UiRect(left, area.top(), Math.max(left, Math.min(area.right(), left + buttonWidth)), area.bottom());
            result.add(new Placed<>(action, bounds, clip));
            left += buttonWidth + gap;
        }
        return result;
    }

    /** Tooltips are returned for the screen's final overlay pass, never drawn underneath another panel. */
    public List<Component> render(GuiGraphics graphics, Font font, double mouseX, double mouseY) {
        for (Placed<K> placed : actions) {
            UiRect visible = placed.visible();
            if (visible.width() == 0 || visible.height() == 0) continue;
            Action<K> action = placed.action();
            boolean hovered = visible.containsExclusive(mouseX, mouseY);
            graphics.enableScissor(placed.clip().left(), placed.clip().top(), placed.clip().right(), placed.clip().bottom());
            try {
                EditorButton.render(graphics, font, placed.bounds(), action.definition(),
                        new EditorButton.State(action.enabled(), hovered, action.key().equals(focused)), action.tone().palette());
            } finally {
                graphics.disableScissor();
            }
        }
        return tooltipAt(mouseX, mouseY);
    }

    /** Screens can defer hover text independently of drawing without recreating a larger hit region. */
    public List<Component> tooltipAt(double x, double y) {
        return actions.stream().filter(p -> p.visible().containsExclusive(x, y))
                .map(p -> p.action().definition().tooltip()).findFirst().orElse(List.of());
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return false;
        for (Placed<K> placed : actions) {
            if (!placed.visible().containsExclusive(x, y)) continue;
            // Disabled controls still consume the click, preventing a row/background fallback action.
            if (placed.action().enabled()) {
                focused = placed.action().key();
                EditorButtonFeedback.activate(placed.bounds());
                placed.action().onPress().accept(x, y);
            }
            return true;
        }
        return false;
    }

    public boolean focusNext(boolean backwards) {
        List<Placed<K>> candidates = focusable();
        if (candidates.isEmpty()) { focused = null; return false; }
        int index = -1;
        for (int i = 0; i < candidates.size(); i++) if (candidates.get(i).action().key().equals(focused)) index = i;
        int next = index < 0 ? (backwards ? candidates.size() - 1 : 0)
                : Math.floorMod(index + (backwards ? -1 : 1), candidates.size());
        focused = candidates.get(next).action().key();
        return true;
    }

    public boolean activateFocused() {
        for (Placed<K> placed : focusable()) {
            if (!placed.action().key().equals(focused)) continue;
            UiRect visible = placed.visible();
            EditorButtonFeedback.activate(placed.bounds());
            placed.action().onPress().accept((double) visible.centerX(), (double) visible.centerY());
            return true;
        }
        return false;
    }

    public Optional<Component> narration() {
        return focusable().stream().filter(p -> p.action().key().equals(focused))
                .map(p -> p.action().definition().narration()).findFirst();
    }

    public Optional<K> focusedKey() { return Optional.ofNullable(focused); }
    public void clearFocus() { focused = null; }
    public boolean atFocusBoundary(boolean backwards) {
        var candidates = focusable();
        return candidates.isEmpty() || java.util.Objects.equals(focused,
                (backwards ? candidates.getFirst() : candidates.getLast()).action().key());
    }

    public void clear() { actions = List.of(); focused = null; }

    private List<Placed<K>> focusable() {
        return actions.stream().filter(p -> p.action().enabled() && p.visible().width() > 0 && p.visible().height() > 0).toList();
    }
}
