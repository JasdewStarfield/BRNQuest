package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class EditorSessionLifecycleTest {
    @Test void leaseReplacementAndLossNotifyOnceWhileOrdinaryTicksKeepForms() {
        var lifecycle = new EditorSessionLifecycle();
        var changes = new AtomicInteger();
        var book = ResourceLocation.parse("test:book");
        var first = new EditorSessionLifecycle.Scope(UUID.randomUUID(), book, true);
        lifecycle.accept(first, changes::incrementAndGet);
        lifecycle.accept(first, changes::incrementAndGet);
        assertEquals(0, changes.get());
        var replacement = new EditorSessionLifecycle.Scope(UUID.randomUUID(), book, true);
        lifecycle.accept(replacement, changes::incrementAndGet);
        lifecycle.accept(replacement, changes::incrementAndGet);
        assertEquals(1, changes.get());
        var closed = new EditorSessionLifecycle.Scope(null, null, false);
        lifecycle.accept(closed, changes::incrementAndGet);
        lifecycle.accept(closed, changes::incrementAndGet);
        assertEquals(2, changes.get());
    }
}
