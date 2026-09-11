package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransientScreenLifecycleTest {
    @Test void childTransitionSuspendsRemovalAndReturnAllowsRealExit() {
        var lifecycle = new TransientScreenLifecycle();
        // setScreen removes the parent synchronously, before the child starts interacting.
        lifecycle.openChild(() -> assertTrue(lifecycle.consumeRemoval()));
        lifecycle.returnedToParent();
        assertFalse(lifecycle.consumeRemoval());
        lifecycle.openChild(() -> assertTrue(lifecycle.consumeRemoval()));
        assertFalse(lifecycle.consumeRemoval());
    }

    @Test void failedTransitionDoesNotLeaveAnExitSuppressionToken() {
        var lifecycle = new TransientScreenLifecycle();
        assertThrows(IllegalStateException.class,
                () -> lifecycle.openChild(() -> { throw new IllegalStateException("failed opening"); }));
        assertFalse(lifecycle.consumeRemoval());
    }
}
