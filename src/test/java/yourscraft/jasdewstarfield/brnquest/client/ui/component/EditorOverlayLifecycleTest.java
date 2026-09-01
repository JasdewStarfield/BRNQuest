package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class EditorOverlayLifecycleTest {
    @Test void activeModalConsumesUnhandledPointerKeyAndWheel() {
        var host = new EditorOverlayHost();
        assertFalse(host.mouseClicked(0, 0, 0));
        host.show(EditorOverlayHost.Kind.QUICK_TEXT);
        assertTrue(host.mouseClicked(-100, -100, 0));
        assertTrue(host.keyPressed(32, 0, 0));
        assertTrue(host.charTyped('x', 0));
        assertTrue(host.mouseScrolled(0, 0, 1));
    }
    @Test void escapeCancelsOnlyCurrentLayerAndRestoresFocusOnce() {
        var host = new EditorOverlayHost();
        var restores = new AtomicInteger();
        var cancels = new AtomicInteger();
        host.focusRestoration(() -> restores::incrementAndGet);
        host.register(EditorOverlayHost.Kind.QUICK_TEXT,
                new EditorOverlayHost.Route(null, null, null, null, null, cancels::incrementAndGet));
        host.show(EditorOverlayHost.Kind.QUICK_TEXT);
        assertTrue(host.keyPressed(256, 0, 0));
        assertEquals(1, cancels.get());
        assertEquals(1, restores.get());
        assertEquals(EditorOverlayHost.Kind.NONE, host.active());
        host.close();
        assertEquals(1, restores.get());
        assertFalse(host.keyPressed(256, 0, 0));
    }
    @Test void replacementKeepsOriginalFocusAndConfirmationCanOpenAnotherModal() {
        var host = new EditorOverlayHost();
        var captures = new AtomicInteger();
        host.focusRestoration(() -> { captures.incrementAndGet(); return () -> {}; });
        host.register(EditorOverlayHost.Kind.QUICK_TEXT, new EditorOverlayHost.Route(null,
                (x, y, button) -> { host.show(EditorOverlayHost.Kind.QUEST_RENAME_CONFIRMATION); return true; },
                null, null, null, null));
        host.show(EditorOverlayHost.Kind.QUICK_TEXT);
        host.mouseClicked(0, 0, 0);
        assertEquals(EditorOverlayHost.Kind.QUEST_RENAME_CONFIRMATION, host.active());
        assertEquals(1, captures.get());
    }
    @Test void routeReceivesNativeTextAndKeysWithoutLeakingThem() {
        var host = new EditorOverlayHost();
        var typed = new StringBuilder();
        host.register(EditorOverlayHost.Kind.QUICK_TEXT, new EditorOverlayHost.Route(null, null,
                (key, scan, modifiers) -> { typed.append(key); return false; },
                (character, modifiers) -> { typed.append(character); return false; }, null, null));
        host.show(EditorOverlayHost.Kind.QUICK_TEXT);
        host.charTyped('A', 0);
        host.keyPressed(257, 0, 0);
        assertEquals("A257", typed.toString());
    }
    @Test void childRemovalIsOneShotAndParentReturnClearsStaleTransition() {
        var lifecycle = new TransientScreenLifecycle();
        lifecycle.prepareChild();
        assertTrue(lifecycle.consumeRemoval());
        assertFalse(lifecycle.consumeRemoval());
        lifecycle.prepareChild();
        lifecycle.returnedToParent();
        assertFalse(lifecycle.consumeRemoval());
    }
}
