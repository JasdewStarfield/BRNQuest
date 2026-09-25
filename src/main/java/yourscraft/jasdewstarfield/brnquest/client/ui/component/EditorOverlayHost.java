package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.GuiGraphics;
import java.util.EnumMap;
import java.util.function.Supplier;

/**
 * Tracks one input-capturing overlay. Callers supply presentation and semantic
 * callbacks; routing, mutual exclusion and focus restoration belong to this host.
 */
public final class EditorOverlayHost {
    public enum Kind {
        NONE, CATALOG, CONTEXT_MENU, ENUM_DROPDOWN, STRUCTURE_FORM, DEPENDENCY_PICKER, TYPED_TYPE_PICKER,
        QUICK_TEXT, DELETE_CONFIRMATION, DISCARD_CONFIRMATION, QUEST_RENAME_CONFIRMATION, PUBLISH_CONFIRMATION,
        CONFLICT_RECOVERY, DRAFT_SOURCE_CHOICE, DRAFT_VERSIONS, DRAFT_RESTORE_CONFIRMATION
    }

    @FunctionalInterface public interface Draw { void render(GuiGraphics graphics, int x, int y); }
    @FunctionalInterface public interface Click { boolean click(double x, double y, int button); }
    @FunctionalInterface public interface Key { boolean key(int key, int scan, int modifiers); }
    @FunctionalInterface public interface Text { boolean type(char character, int modifiers); }
    @FunctionalInterface public interface Wheel { void scroll(double x, double y, double amount); }
    @FunctionalInterface public interface Drag { boolean drag(double x, double y, int button, double dx, double dy); }

    /** A modal owns all input even if its callback does not perform an action. */
    public record Route(Draw draw, Click click, Key key, Text text, Wheel wheel, Runnable cancel,
                        boolean nativePointer) {
        public Route(Draw draw, Click click, Key key, Text text, Wheel wheel, Runnable cancel) {
            this(draw, click, key, text, wheel, cancel, false);
        }
    }
    private final EnumMap<Kind, Route> routes = new EnumMap<>(Kind.class);
    private Supplier<Runnable> captureFocus = () -> () -> {};
    private Runnable restoreFocus = () -> {};
    private Kind active = Kind.NONE;

    public void focusRestoration(Supplier<Runnable> capture) { captureFocus = capture; }
    public void register(Kind kind, Route route) {
        if (kind == Kind.NONE) throw new IllegalArgumentException("NONE is not a modal");
        routes.put(kind, route);
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (active == Kind.NONE) return false;
        Route route = routes.get(active);
        if (route != null && route.click() != null) route.click().click(x, y, button);
        return true;
    }

    public boolean keyPressed(int key, int scan, int modifiers) {
        if (active == Kind.NONE) return false;
        Route route = routes.get(active);
        if (key == 256) {
            Kind closing = active;
            if (route != null && route.cancel() != null) route.cancel().run();
            if (active == closing) close();
        } else if (route != null && route.key() != null) {
            route.key().key(key, scan, modifiers);
        }
        return true;
    }

    public boolean charTyped(char character, int modifiers) {
        if (active == Kind.NONE) return false;
        Route route = routes.get(active);
        if (route != null && route.text() != null) route.text().type(character, modifiers);
        return true;
    }

    public boolean mouseScrolled(double x, double y, double amount) {
        if (active == Kind.NONE) return false;
        Route route = routes.get(active);
        if (route != null && route.wheel() != null) route.wheel().scroll(x, y, amount);
        return true;
    }

    /** Native text selection may receive drag/release; every other modal still blocks the canvas. */
    public boolean mouseDragged(double x, double y, int button, double dx, double dy, Drag nativeInput) {
        if (active == Kind.NONE) return false;
        Route route = routes.get(active);
        if (route != null && route.nativePointer()) nativeInput.drag(x, y, button, dx, dy);
        return true;
    }

    public boolean mouseReleased(double x, double y, int button, Click nativeInput) {
        if (active == Kind.NONE) return false;
        Route route = routes.get(active);
        if (route != null && route.nativePointer()) nativeInput.click(x, y, button);
        return true;
    }

    /** Called at the caller's existing overlay depth; it deliberately does not add another blur. */
    public void render(GuiGraphics graphics, int x, int y) {
        Route route = routes.get(active);
        if (route != null && route.draw() != null) route.draw().render(graphics, x, y);
    }

    public Kind active() {
        return active;
    }

    public boolean isOpen(Kind kind) {
        return active == kind;
    }

    public void show(Kind kind) {
        if (kind == Kind.NONE) throw new IllegalArgumentException("Use close() to clear the active overlay");
        if (active == Kind.NONE) restoreFocus = captureFocus.get();
        active = kind;
    }

    public void close() {
        if (active == Kind.NONE) return;
        active = Kind.NONE;
        Runnable restore = restoreFocus;
        restoreFocus = () -> {};
        restore.run();
    }
}
